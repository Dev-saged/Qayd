package dev.saged.qayd.ui.util

import androidx.compose.ui.graphics.Color
import dev.saged.qayd.config.BrandConfig
import dev.saged.qayd.ui.theme.Palette
import java.util.Calendar
import java.util.Locale

/** عزل LTR حتى لا تنقلب الإشارة والعملة داخل سطر عربي. */
fun ltr(s: String) = "⁦$s⁩"

fun money(v: Double): String =
    if (v % 1.0 == 0.0) String.format(Locale.US, "%,d", v.toLong()) else String.format(Locale.US, "%,.2f", v).trimEnd('0').trimEnd('.')

fun cur(v: Double) = ltr("${money(v)} ${BrandConfig.CURRENCY}")

/** نص الرصيد ولونه: عليه / له / مسدّد. */
fun balanceText(b: Double, p: Palette): Pair<String, Color> = when {
    b > 0 -> "عليه ${cur(b)}" to p.danger
    b < 0 -> "له ${cur(-b)}" to p.ok
    else -> "مسدّد" to p.mute
}

fun clock(t: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    return ltr(String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)))
}

fun dayLabel(t: Long, now: Long = System.currentTimeMillis()): String {
    val d = Calendar.getInstance().apply { timeInMillis = t }
    val n = Calendar.getInstance().apply { timeInMillis = now }
    val sameYear = d.get(Calendar.YEAR) == n.get(Calendar.YEAR)
    val diff = n.get(Calendar.DAY_OF_YEAR) - d.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && diff == 0 -> "اليوم"
        sameYear && diff == 1 -> "أمس"
        else -> ltr(String.format(Locale.US, "%d/%d/%d", d.get(Calendar.DAY_OF_MONTH), d.get(Calendar.MONTH) + 1, d.get(Calendar.YEAR)))
    }
}

/** توحيد الحروف العربية للبحث. */
fun norm(s: String): String = s.lowercase().trim()
    .replace(Regex("[ً-ْٰ]"), "")
    .replace(Regex("[آأإٱ]"), "ا").replace('ى', 'ي').replace('ة', 'ه')
    .replace(Regex("\\s+"), " ")
