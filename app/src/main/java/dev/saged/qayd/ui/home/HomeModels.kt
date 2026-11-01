package dev.saged.qayd.ui.home

import androidx.compose.runtime.Immutable
import dev.saged.qayd.data.round2

/** جلسة شاشة: الوقت يُشتق من بداية مطلقة مخزنة، فيبقى صحيحاً على كل الأجهزة ولو أُغلق التطبيق. */
@Immutable
data class PsSlot(
    val number: Int,
    val startedAt: Long? = null,
    val bookedMinutes: Int = 60,
    val pausedAt: Long? = null,
    val pausedMs: Long = 0,
    val rate: Double = 0.0,
    val byName: String = "",
) {
    val running get() = startedAt != null
    val open get() = bookedMinutes <= 0
    fun activeMs(now: Long): Long = startedAt?.let { ((pausedAt ?: now) - it - pausedMs).coerceAtLeast(0) } ?: 0
    fun cost(now: Long): Double = round2(activeMs(now) / 3_600_000.0 * rate)
    fun leftMs(now: Long): Long = bookedMinutes * 60_000L - activeMs(now)
}

fun idleSlots(count: Int) = (1..count).map { PsSlot(it) }

/** جلسات عرض للمعاينة والاختبار. */
object SampleHome {
    fun slots(now: Long) = listOf(
        PsSlot(1, now - (1 * 3600 + 42 * 60 + 18) * 1000L, 120, rate = 10.0),
        PsSlot(2),
        PsSlot(3, now - (25 * 60 + 40) * 1000L, 60, rate = 10.0),
    )
}
