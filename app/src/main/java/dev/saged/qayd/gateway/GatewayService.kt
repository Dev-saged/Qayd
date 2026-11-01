package dev.saged.qayd.gateway

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import dev.saged.qayd.MainActivity
import dev.saged.qayd.R
import dev.saged.qayd.config.BrandConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * جوال الإرسال: خدمة في المقدمة تلتقط رسائل طابور المحل من Firestore وترسلها من شريحة هذا الجوال،
 * وتحدّث حالة كل رسالة (أُرسلت، وصلت، فشلت) وتعلن حضورها لباقي أجهزة المحل بنبضة كل دقيقة.
 */
class GatewayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val db = FirebaseFirestore.getInstance()
    private var reg: ListenerRegistration? = null
    private var shopId: String = ""
    private val inFlight = mutableSetOf<String>()

    private val results = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val id = i.getStringExtra(EXTRA_ID) ?: return
            val change: Map<String, Any> = when (i.action) {
                ACTION_SENT -> if (resultCode == Activity.RESULT_OK) {
                    mapOf("status" to "sent", "sentAt" to FieldValue.serverTimestamp())
                } else {
                    mapOf("status" to "failed", "error" to errorText(resultCode))
                }
                ACTION_DELIVERED -> mapOf("status" to "delivered", "deliveredAt" to FieldValue.serverTimestamp())
                else -> return
            }
            inFlight -= id
            msg(id).update(change + ("updatedAt" to FieldValue.serverTimestamp()))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply { addAction(ACTION_SENT); addAction(ACTION_DELIVERED) }
        ContextCompat.registerReceiver(this, results, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            goOffline()
            stopSelf()
            return START_NOT_STICKY
        }
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        shopId = intent?.getStringExtra(EXTRA_SHOP) ?: prefs.getString(EXTRA_SHOP, null) ?: run { stopSelf(); return START_NOT_STICKY }
        prefs.edit().putString(EXTRA_SHOP, shopId).putBoolean(KEY_ON, true).apply()
        val canSend = ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!canSend || runCatching { startInForeground() }.isFailure) {
            prefs.edit().putBoolean(KEY_ON, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        if (reg == null) listen()
        scope.launch {
            while (isActive) {
                gatewayDoc().set(
                    mapOf("online" to true, "device" to "${Build.MANUFACTURER} ${Build.MODEL}", "lastSeen" to FieldValue.serverTimestamp()),
                    SetOptions.merge(),
                )
                delay(60_000)
            }
        }
        return START_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "جوال الإرسال", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, GatewayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("${BrandConfig.NAME} — جوال الإرسال يعمل")
            .setContentText("يرسل رسائل المحل من شريحة هذا الجوال")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "إيقاف", stop)
            .build()
        ServiceCompat.startForeground(
            this, 7, n,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
    }

    private fun listen() {
        reg = db.collection("shops/$shopId/smsQueue").whereEqualTo("status", "queued")
            .addSnapshotListener { s, _ -> s?.documents?.forEach { d -> if (d.id !in inFlight) claim(d.id) } }
    }

    /** يحجز الرسالة بمعاملة حتى لا يرسلها جوالان معاً. */
    private fun claim(id: String) {
        inFlight += id
        db.runTransaction { t ->
            val snap = t.get(msg(id))
            if (snap.getString("status") != "queued") return@runTransaction null
            t.update(msg(id), mapOf("status" to "sending", "gateway" to Build.MODEL, "attempts" to (snap.getLong("attempts") ?: 0L) + 1, "updatedAt" to FieldValue.serverTimestamp()))
            snap.getString("to").orEmpty() to snap.getString("text").orEmpty()
        }.addOnSuccessListener { r ->
            if (r == null) inFlight -= id else send(id, r.first, r.second)
        }.addOnFailureListener { inFlight -= id }
    }

    private fun send(id: String, to: String, text: String) {
        try {
            val sms = if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
            val parts = sms.divideMessage(text)
            fun pi(action: String, i: Int) = PendingIntent.getBroadcast(
                this, (id.hashCode() * 31 + i) and 0x7fffffff,
                Intent(action).setPackage(packageName).putExtra(EXTRA_ID, id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val sent = ArrayList(parts.indices.map { if (it == parts.lastIndex) pi(ACTION_SENT, it) else null })
            val delivered = ArrayList(parts.indices.map { if (it == parts.lastIndex) pi(ACTION_DELIVERED, 1000 + it) else null })
            sms.sendMultipartTextMessage(to, null, parts, sent, delivered)
        } catch (e: Exception) {
            inFlight -= id
            msg(id).update(mapOf("status" to "failed", "error" to (e.message ?: "تعذر الإرسال"), "updatedAt" to FieldValue.serverTimestamp()))
        }
    }

    private fun msg(id: String) = db.document("shops/$shopId/smsQueue/$id")
    private fun gatewayDoc() = db.document("shops/$shopId/gateway/main")

    private fun goOffline() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ON, false).apply()
        if (shopId.isNotEmpty()) gatewayDoc().set(mapOf("online" to false, "lastSeen" to FieldValue.serverTimestamp()), SetOptions.merge())
    }

    override fun onDestroy() {
        reg?.remove()
        scope.cancel()
        runCatching { unregisterReceiver(results) }
        super.onDestroy()
    }

    private fun errorText(code: Int) = when (code) {
        SmsManager.RESULT_ERROR_NO_SERVICE -> "لا توجد تغطية"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "الشبكة مغلقة"
        SmsManager.RESULT_ERROR_NULL_PDU -> "رسالة غير صالحة"
        else -> "فشل الإرسال ($code)"
    }

    companion object {
        private const val CHANNEL = "gateway"
        private const val PREFS = "qayd_gateway"
        private const val KEY_ON = "on"
        private const val EXTRA_SHOP = "shopId"
        private const val EXTRA_ID = "id"
        private const val ACTION_STOP = "dev.saged.qayd.GATEWAY_STOP"
        private const val ACTION_SENT = "dev.saged.qayd.SMS_SENT"
        private const val ACTION_DELIVERED = "dev.saged.qayd.SMS_DELIVERED"

        fun isOn(c: Context) = c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ON, false)

        fun start(c: Context, shopId: String) =
            ContextCompat.startForegroundService(c, Intent(c, GatewayService::class.java).putExtra(EXTRA_SHOP, shopId))

        fun stop(c: Context) = c.startService(Intent(c, GatewayService::class.java).setAction(ACTION_STOP))
    }
}
