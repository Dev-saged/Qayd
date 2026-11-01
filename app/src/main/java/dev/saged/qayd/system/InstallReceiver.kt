package dev.saged.qayd.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/** نتيجة جلسة التثبيت: يفتح شاشة التأكيد إن طلبها النظام. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) != PackageInstaller.STATUS_PENDING_USER_ACTION) return
        val confirm = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_INTENT)
        confirm?.let { runCatching { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
}
