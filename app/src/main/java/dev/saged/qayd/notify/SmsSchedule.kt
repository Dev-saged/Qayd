package dev.saged.qayd.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.firestore.Source
import dev.saged.qayd.MainActivity
import dev.saged.qayd.R
import dev.saged.qayd.config.BrandConfig
import dev.saged.qayd.data.SmsSettings
import dev.saged.qayd.gateway.GatewayService
import dev.saged.qayd.system.DeviceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * التذكير الشهري للمدينين على جوال الإرسال المعتمد فقط:
 * تنبيه قبل ساعة، ثم في الموعد تُضاف الرسائل للطابور ويبدأ الإرسال تلقائياً.
 */
object SmsSchedule {
    private const val CHANNEL = "sms_schedule"
    private const val PREFS = "qayd_sms_schedule"

    /** موعد التذكير القادم بتوقيت الجهاز. */
    fun next(s: SmsSettings, now: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, s.reminderDay.coerceIn(1, 28)); set(Calendar.HOUR_OF_DAY, s.reminderHour.coerceIn(0, 23))
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= now) c.add(Calendar.MONTH, 1)
        return c.timeInMillis
    }

    fun designated(c: Context, s: SmsSettings) = s.gatewayDevice.isNotBlank() && s.gatewayDevice == DeviceId.get(c)

    /** يُستدعى عند كل تغيير في الإعدادات: يضبط المنبهين أو يلغيهما. */
    fun sync(c: Context, shopId: String, s: SmsSettings) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("shop", shopId).apply()
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val pre = pi(c, 71, "pre")
        val run = pi(c, 72, "run")
        am.cancel(pre); am.cancel(run)
        if (designated(c, s)) {
            WorkManager.getInstance(c).enqueueUniquePeriodicWork("gateway_check", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<GatewayCheck>(15, TimeUnit.MINUTES).build())
        } else {
            WorkManager.getInstance(c).cancelUniqueWork("gateway_check")
        }
        if (!s.reminderOn || !designated(c, s)) return
        val at = next(s)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        listOf(at - 3_600_000L to pre, at to run).filter { it.first > System.currentTimeMillis() }.forEach { (t, p) ->
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p)
        }
    }

    private fun pi(c: Context, code: Int, kind: String) = PendingIntent.getBroadcast(
        c, code, Intent(c, SmsScheduleReceiver::class.java).putExtra("kind", kind), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun notify(c: Context, id: Int, title: String, text: String) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "التذكير الشهري وجوال الإرسال", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(c, id, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        runCatching {
            nm.notify(id, NotificationCompat.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open).setAutoCancel(true).build())
        }
    }

    /** يقرأ بيانات المحل من الخادم ويضيف رسائل التذكير للطابور ثم يشغّل الإرسال. */
    suspend fun run(c: Context) {
        val shopId = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("shop", null) ?: return
        val month = Calendar.getInstance().let { "${it.get(Calendar.YEAR)}-${it.get(Calendar.MONTH)}" }
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString("done", "") == month) return
        val db = dev.saged.qayd.data.QaydRepo.get(c).firestore
        val s = db.document("shops/$shopId/settings/sms").get(Source.SERVER).await()
        if (s.getBoolean("reminderOn") != true || s.getString("gatewayDevice") != DeviceId.get(c)) return
        val shop = db.document("shops/$shopId").get().await().getString("name").orEmpty()
        val customers = db.collection("shops/$shopId/customers").get().await().documents.filter { it.getBoolean("deleted") != true }
        val txs = db.collection("shops/$shopId/tx").get().await().documents.filter { it.getBoolean("deleted") != true }
        val bal = HashMap<String, Double>()
        val lastDebt = HashMap<String, Long>()
        txs.forEach { t ->
            val cid = t.getString("customerId") ?: return@forEach
            val a = t.getDouble("amount") ?: 0.0
            when (t.getString("kind")) {
                "debt" -> { bal[cid] = (bal[cid] ?: 0.0) + a; lastDebt[cid] = maxOf(lastDebt[cid] ?: 0L, t.getTimestamp("at")?.toDate()?.time ?: 0L) }
                "payment", "discount" -> bal[cid] = (bal[cid] ?: 0.0) - a
            }
        }
        val lateOnly = s.getBoolean("reminderLateOnly") == true
        val cutoff = System.currentTimeMillis() - BrandConfig.OVERDUE_DAYS * 86_400_000L
        val tid = s.getString("reminderTemplate") ?: "b_debt"
        val body = dev.saged.qayd.data.Templates.BUILT_IN.firstOrNull { it.id == tid }?.body
            ?: db.document("shops/$shopId/templates/$tid").get().await().getString("body")
            ?: dev.saged.qayd.data.Templates.BUILT_IN.first().body
        val linkOn = s.getBoolean("link") == true
        val items = customers.mapNotNull { d ->
            val b = bal[d.id] ?: 0.0
            val phone = d.getString("phone").orEmpty()
            if (b <= 0 || phone.isBlank() || (lateOnly && (lastDebt[d.id] ?: Long.MAX_VALUE) > cutoff)) return@mapNotNull null
            val name = d.getString("name").orEmpty()
            val link = d.getString("link").orEmpty().takeIf { linkOn && it.isNotBlank() }?.let { dev.saged.qayd.data.StatementLinks.BASE + it }.orEmpty()
            Triple(phone, name, dev.saged.qayd.data.Templates.fill(body, mapOf(
                "الاسم" to name, "المحل" to shop, "الرصيد" to "${dev.saged.qayd.ui.util.money(b)} ${BrandConfig.CURRENCY}",
                "المبلغ" to "${dev.saged.qayd.ui.util.money(b)} ${BrandConfig.CURRENCY}", "رابط_الكشف" to link,
            )))
        }
        if (items.isEmpty()) { prefs.edit().putString("done", month).apply(); return }
        dev.saged.qayd.data.QaydRepo.get(c).enqueueSms(shopId, items, "التذكير الشهري")
        prefs.edit().putString("done", month).apply()
        runCatching { GatewayService.start(c, shopId) }
        notify(c, 73, "بدأ التذكير الشهري", "أُضيفت ${items.size} رسالة للطابور، وجوال الإرسال يرسلها الآن.")
    }
}

class SmsScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val done = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                if (i.getStringExtra("kind") == "pre") {
                    SmsSchedule.notify(c, 74, "التذكير الشهري بعد ساعة", "سيُرسل تذكير الرصيد للمدينين من هذا الجوال. تأكد أن الشريحة فعّالة وفيها رصيد.")
                } else {
                    SmsSchedule.run(c)
                }
            }
            done.finish()
        }
    }
}

/** فحص دوري على جوال الإرسال المعتمد: طلبات التشغيل من الموظفين. */
class GatewayCheck(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        val shopId = applicationContext.getSharedPreferences("qayd_sms_schedule", Context.MODE_PRIVATE).getString("shop", null) ?: return Result.success()
        runCatching {
            val r = dev.saged.qayd.data.QaydRepo.get(applicationContext).firestore.document("shops/$shopId/gateway/request").get(Source.SERVER).await()
            val at = r.getTimestamp("at")?.toDate()?.time ?: 0L
            val seen = applicationContext.getSharedPreferences("qayd_sms_schedule", Context.MODE_PRIVATE).getLong("seenReq", 0L)
            if (r.getString("status") == "pending" && at > seen && !GatewayService.isOn(applicationContext)) {
                SmsSchedule.notify(applicationContext, 75, "طلب تشغيل جوال الإرسال", "${r.getString("byName").orEmpty()} يطلب تشغيل الإرسال. افتح التطبيق للقبول.")
                applicationContext.getSharedPreferences("qayd_sms_schedule", Context.MODE_PRIVATE).edit().putLong("seenReq", at).apply()
            }
        }
        return Result.success()
    }
}
