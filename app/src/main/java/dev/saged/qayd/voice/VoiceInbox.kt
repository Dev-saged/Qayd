package dev.saged.qayd.voice

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import kotlin.coroutines.resume

/** إدخال صوتي ينتظر المراجعة: التسجيل محفوظ مع النص المفهوم ولا يُسجَّل شيء قبل التأكيد. */
data class VoiceEntry(val id: String, val at: Long, val audio: String, val text: String, val shopId: String)

object VoiceInbox {
    private const val PREFS = "qayd_voice"
    private const val RATE = 16_000
    val items = mutableStateOf<List<VoiceEntry>>(emptyList())

    private fun dir(c: Context) = File(c.filesDir, "voice").apply { mkdirs() }

    fun load(c: Context) {
        val a = runCatching { JSONArray(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("list", "[]")) }.getOrDefault(JSONArray())
        items.value = (0 until a.length()).map { a.getJSONObject(it) }.map {
            VoiceEntry(it.getString("id"), it.getLong("at"), it.getString("audio"), it.optString("text"), it.optString("shop"))
        }.filter { File(it.audio).exists() }
    }

    private fun save(c: Context) {
        val a = JSONArray()
        items.value.forEach { a.put(JSONObject().put("id", it.id).put("at", it.at).put("audio", it.audio).put("text", it.text).put("shop", it.shopId)) }
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("list", a.toString()).apply()
    }

    fun add(c: Context, e: VoiceEntry) { items.value = listOf(e) + items.value; save(c) }

    fun update(c: Context, id: String, text: String) { items.value = items.value.map { if (it.id == id) it.copy(text = text) else it }; save(c) }

    fun remove(c: Context, id: String) {
        items.value.firstOrNull { it.id == id }?.let { File(it.audio).delete() }
        items.value = items.value.filter { it.id != id }
        save(c)
    }

    /** مسجّل PCM يُحفظ WAV لإعادة الاستماع، وتُمرَّر نفس العينات للتعرّف على الكلام. */
    class Recorder(private val c: Context) {
        private var rec: AudioRecord? = null
        private var thread: Thread? = null
        @Volatile private var running = false
        private val pcm = java.io.ByteArrayOutputStream()
        val level = mutableStateOf(0f)

        @SuppressLint("MissingPermission")
        fun start(): Boolean {
            val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val r = runCatching { AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 4) }.getOrNull() ?: return false
            if (r.state != AudioRecord.STATE_INITIALIZED) return false
            rec = r
            running = true
            r.startRecording()
            thread = Thread {
                val buf = ShortArray(min)
                while (running) {
                    val n = r.read(buf, 0, buf.size)
                    if (n > 0) {
                        var peak = 0
                        val bytes = ByteArray(n * 2)
                        for (i in 0 until n) {
                            val v = buf[i].toInt()
                            peak = maxOf(peak, kotlin.math.abs(v))
                            bytes[i * 2] = (v and 0xFF).toByte(); bytes[i * 2 + 1] = (v shr 8 and 0xFF).toByte()
                        }
                        synchronized(pcm) { pcm.write(bytes) }
                        level.value = (peak / 32768f).coerceIn(0f, 1f)
                    }
                }
            }.apply { start() }
            return true
        }

        /** يوقف التسجيل ويكتب ملف WAV ويعيد مساره والعينات الخام. */
        fun stop(): Pair<File, ByteArray>? {
            running = false
            thread?.join(500)
            runCatching { rec?.stop() }
            rec?.release()
            rec = null
            val data = synchronized(pcm) { pcm.toByteArray() }
            if (data.size < RATE) return null
            val f = File(dir(c), "v${System.currentTimeMillis()}.wav")
            writeWav(f, data)
            return f to data
        }

        private fun writeWav(f: File, data: ByteArray) {
            RandomAccessFile(f, "rw").use { o ->
                fun le32(v: Int) = byteArrayOf((v and 0xFF).toByte(), (v shr 8 and 0xFF).toByte(), (v shr 16 and 0xFF).toByte(), (v shr 24 and 0xFF).toByte())
                fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), (v shr 8 and 0xFF).toByte())
                o.write("RIFF".toByteArray()); o.write(le32(36 + data.size)); o.write("WAVEfmt ".toByteArray())
                o.write(le32(16)); o.write(le16(1)); o.write(le16(1)); o.write(le32(RATE)); o.write(le32(RATE * 2)); o.write(le16(2)); o.write(le16(16))
                o.write("data".toByteArray()); o.write(le32(data.size)); o.write(data)
            }
        }
    }

    /** هل يتعرّف الجهاز على العربية بدون إنترنت. */
    fun offlineAvailable(c: Context) = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(c)

    /**
     * التعرّف على الكلام من العينات المسجّلة (أندرويد 13 فما فوق)، على الجهاز إن توفّر وإلا بالخدمة العادية.
     * على الإصدارات الأقدم يبقى التسجيل للمراجعة اليدوية.
     */
    suspend fun recognize(c: Context, pcm: ByteArray): String = withContext(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT < 33 || !SpeechRecognizer.isRecognitionAvailable(c)) return@withContext ""
        suspendCancellableCoroutine { cont ->
            val sr = if (offlineAvailable(c)) SpeechRecognizer.createOnDeviceSpeechRecognizer(c) else SpeechRecognizer.createSpeechRecognizer(c)
            val (read, write) = ParcelFileDescriptor.createPipe()
            Thread { runCatching { ParcelFileDescriptor.AutoCloseOutputStream(write).use { it.write(pcm) } } }.start()
            fun done(t: String) { runCatching { sr.destroy() }; runCatching { read.close() }; if (cont.isActive) cont.resume(t) }
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onResults(b: Bundle?) = done(b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                override fun onError(error: Int) = done("")
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(b: Bundle?) {}
                override fun onEvent(t: Int, b: Bundle?) {}
            })
            sr.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, read)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, RATE),
            )
            cont.invokeOnCancellation { runCatching { sr.destroy() } }
        }
    }
}
