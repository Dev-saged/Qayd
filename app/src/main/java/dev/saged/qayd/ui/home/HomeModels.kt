package dev.saged.qayd.ui.home

import androidx.compose.runtime.Immutable

@Immutable
data class PsSlot(val number: Int, val startedAt: Long? = null, val bookedMinutes: Int = 60)

fun idleSlots(count: Int) = (1..count).map { PsSlot(it) }

/** جلسات عرض للمعاينة والاختبار. */
object SampleHome {
    fun slots(now: Long) = listOf(
        PsSlot(1, now - (1 * 3600 + 42 * 60 + 18) * 1000L, 120),
        PsSlot(2),
        PsSlot(3, now - (25 * 60 + 40) * 1000L, 60),
    )
}
