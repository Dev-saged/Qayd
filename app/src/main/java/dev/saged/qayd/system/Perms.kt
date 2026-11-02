package dev.saged.qayd.system

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat

/** صلاحيات قيد: التشغيلية تُطلب دفعة واحدة، والخاصة تفتح إعداداتها. */
object Perms {
    enum class Key { Notify, Sms, Phone, Mic, Storage, Battery, Install, Alarms }

    private const val PREFS = "qayd_perms"
    /** فتح شاشة الصلاحيات من أي مكان (مثل "المزيد"). */
    val open = mutableStateOf(false)

    fun seen(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("seen", false)
    fun markSeen(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("seen", true).apply()

    fun runtime(k: Key): List<String> = when (k) {
        Key.Notify -> if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
        Key.Sms -> listOf(Manifest.permission.SEND_SMS)
        Key.Phone -> listOf(Manifest.permission.READ_PHONE_STATE)
        Key.Mic -> listOf(Manifest.permission.RECORD_AUDIO)
        Key.Storage -> if (Build.VERSION.SDK_INT < 29) listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE) else emptyList()
        else -> emptyList()
    }

    /** الصلاحيات التي تخص هذا الجهاز فقط. */
    fun applicable(): List<Key> = Key.entries.filter { k ->
        when (k) {
            Key.Notify -> Build.VERSION.SDK_INT >= 33
            Key.Storage -> Build.VERSION.SDK_INT < 29
            Key.Alarms -> Build.VERSION.SDK_INT in 31..32
            else -> true
        }
    }

    fun granted(c: Context, k: Key): Boolean = when (k) {
        Key.Battery -> (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)
        Key.Install -> c.packageManager.canRequestPackageInstalls()
        Key.Alarms -> Build.VERSION.SDK_INT < 31 || (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        else -> runtime(k).all { ContextCompat.checkSelfPermission(c, it) == PackageManager.PERMISSION_GRANTED }
    }

    /** شاشة الإعداد الخاصة بالصلاحية التي لا تُطلب بنافذة. */
    fun settingsIntent(c: Context, k: Key): Intent? {
        val pkg = Uri.parse("package:${c.packageName}")
        return when (k) {
            Key.Battery -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            Key.Install -> Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkg)
            Key.Alarms -> if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg) else null
            else -> null
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun appInfo(c: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
