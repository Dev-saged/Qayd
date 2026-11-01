package dev.saged.qayd.system

import android.content.Context
import java.io.File

/** نسخة احتياطية تلقائية مرة يومياً في مجلد قيد، ويُحتفظ بآخر 7 نسخ. */
object AutoBackup {
    private const val PREFS = "qayd_autobackup"
    private const val DAY = 86_400_000L
    private const val KEEP = 7
    private const val PREFIX = "qayd-auto-"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("on", false)
    fun setEnabled(c: Context, on: Boolean) = prefs(c).edit().putBoolean("on", on).apply()
    fun lastAt(c: Context) = prefs(c).getLong("last", 0L)

    fun due(c: Context, now: Long = System.currentTimeMillis()) = enabled(c) && now - lastAt(c) >= DAY

    /** ينسخ ملف النسخة إلى التنزيلات/Qayd/Backups ويحتفظ بآخر 7. */
    fun store(c: Context, src: File, now: Long = System.currentTimeMillis()) {
        if (QaydFiles.save(c, src, QaydFiles.BACKUPS, "application/json", PREFIX + src.name.removePrefix("qayd-backup-"), PREFIX, KEEP)) {
            prefs(c).edit().putLong("last", now).apply()
        }
    }
}
