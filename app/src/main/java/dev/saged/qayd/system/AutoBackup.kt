package dev.saged.qayd.system

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** نسخة احتياطية تلقائية مرة يومياً في مجلد التنزيلات/Qayd، ويُحتفظ بآخر 7 نسخ. */
object AutoBackup {
    private const val PREFS = "qayd_autobackup"
    private const val DAY = 86_400_000L
    private const val KEEP = 7
    private const val DIR = "Qayd"
    private const val PREFIX = "qayd-auto-"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("on", false)
    fun setEnabled(c: Context, on: Boolean) = prefs(c).edit().putBoolean("on", on).apply()
    fun lastAt(c: Context) = prefs(c).getLong("last", 0L)

    fun due(c: Context, now: Long = System.currentTimeMillis()) = enabled(c) && now - lastAt(c) >= DAY

    /** ينسخ ملف النسخة إلى مكان دائم ويحذف الأقدم من 7. */
    fun store(c: Context, src: File, now: Long = System.currentTimeMillis()) {
        val name = PREFIX + src.name.removePrefix("qayd-backup-")
        if (Build.VERSION.SDK_INT >= 29) {
            val r = c.contentResolver
            val uri = r.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DIR")
                },
            ) ?: return
            r.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
            r.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?", arrayOf("$PREFIX%"), "${MediaStore.Downloads.DATE_ADDED} DESC",
            )?.use { cur ->
                var i = 0
                while (cur.moveToNext()) {
                    if (i++ >= KEEP) runCatching { r.delete(android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cur.getLong(0)), null, null) }
                }
            }
        } else {
            val dir = File(c.getExternalFilesDir(null), DIR).apply { mkdirs() }
            src.copyTo(File(dir, name), overwrite = true)
            dir.listFiles { f -> f.name.startsWith(PREFIX) }?.sortedByDescending { it.lastModified() }?.drop(KEEP)?.forEach { it.delete() }
        }
        prefs(c).edit().putLong("last", now).apply()
    }
}
