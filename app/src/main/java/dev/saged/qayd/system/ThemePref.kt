package dev.saged.qayd.system

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/** مظهر التطبيق: تلقائي يتبع الجهاز، أو فاتح، أو داكن. */
object ThemePref {
    enum class Mode(val label: String) { System("تلقائي"), Light("فاتح"), Dark("داكن") }

    private const val PREFS = "qayd_theme"
    val mode = mutableStateOf(Mode.System)

    fun load(c: Context) {
        val v = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("mode", null)
        mode.value = Mode.entries.firstOrNull { it.name == v } ?: Mode.System
    }

    fun set(c: Context, m: Mode) {
        mode.value = m
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("mode", m.name).apply()
    }
}
