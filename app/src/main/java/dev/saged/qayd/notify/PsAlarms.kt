package dev.saged.qayd.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.saged.qayd.MainActivity
import dev.saged.qayd.R
import dev.saged.qayd.config.BrandConfig
import dev.saged.qayd.ui.home.PsSlot

/** منبهات نظام لجلسات الشاشات: قرب الانتهاء، الانتهاء، وكل ساعة. تعمل والتطبيق مغلق. */
object PsAlarms {
    private const val CHANNEL = "ps"
    private const val MAX_SCREENS = 12
    private val scheduled = mutableSetOf<Int>()

    fun sync(context: Context, slots: List<PsSlot>) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        scheduled.toList().forEach { cancel(context, am, it) }
        scheduled.clear()
        val now = System.currentTimeMillis()
        slots.filter { it.running && it.pausedAt == null }.forEach { s ->
            val start = s.startedAt!! + s.pausedMs
            if (!s.open) {
                val end = start + s.bookedMinutes * 60_000L
                schedule(context, am, s.number * 10 + 1, end - BrandConfig.PS_WARN_MINUTES * 60_000L, s.number, "شاشة ${s.number}: باقي ${BrandConfig.PS_WARN_MINUTES} دقيقة", now)
                schedule(context, am, s.number * 10 + 2, end, s.number, "شاشة ${s.number}: انتهى الوقت", now)
            }
            val nextHour = start + ((now - start) / 3_600_000L + 1) * 3_600_000L
            val hours = (nextHour - start) / 3_600_000L
            schedule(context, am, s.number * 10 + 3, nextHour, s.number, "شاشة ${s.number}: مرّت $hours ساعة", now)
        }
    }

    private fun schedule(context: Context, am: AlarmManager, code: Int, at: Long, screen: Int, text: String, now: Long) {
        if (at <= now || screen > MAX_SCREENS) return
        val pi = pending(context, code, Intent(context, PsAlarmReceiver::class.java).putExtra("text", text).putExtra("id", code))
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        scheduled += code
    }

    private fun cancel(context: Context, am: AlarmManager, code: Int) =
        am.cancel(pending(context, code, Intent(context, PsAlarmReceiver::class.java)))

    private fun pending(context: Context, code: Int, intent: Intent) =
        PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun notify(context: Context, id: Int, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "شاشات PlayStation", NotificationManager.IMPORTANCE_HIGH))
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        NotificationManagerCompat.from(context).notify(
            id,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(BrandConfig.NAME)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build(),
        )
    }
}

class PsAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PsAlarms.notify(context, intent.getIntExtra("id", 0), intent.getStringExtra("text") ?: return)
    }
}
