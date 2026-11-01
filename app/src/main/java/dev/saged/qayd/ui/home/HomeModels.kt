package dev.saged.qayd.ui.home

import androidx.compose.runtime.Immutable

@Immutable
data class PsSlot(val number: Int, val startedAt: Long? = null, val bookedMinutes: Int = 60)

enum class ActivityKind { Payment, Debt, Session }

@Immutable
data class Activity(val name: String, val note: String, val amount: Double, val kind: ActivityKind)

@Immutable
data class HomeSummary(
    val shopName: String,
    val user: String,
    val totalDebt: Long,
    val collectedToday: Long,
    val trend: List<Float>,
    val late: Int,
    val pendingReadings: Int,
    val gatewayOnline: Boolean,
    val recent: List<Activity>,
)

/** بيانات عرض للهيكل قبل ربط قاعدة البيانات. */
object SampleHome {
    val summary = HomeSummary(
        shopName = "كفي الأنوار",
        user = "ahmad@anoar.ps",
        totalDebt = 18_450,
        collectedToday = 1_240,
        trend = listOf(21.2f, 21.6f, 20.9f, 21.4f, 22.8f, 22.1f, 20.7f, 21.3f, 20.2f, 19.6f, 19.9f, 18.45f),
        late = 7,
        pendingReadings = 12,
        gatewayOnline = true,
        recent = listOf(
            Activity("محمود أبو سعدة", "دفعة · قبل 12 د", 300.0, ActivityKind.Payment),
            Activity("أم خالد — كهرباء", "فاتورة · قبل ساعة", 86.4, ActivityKind.Debt),
            Activity("شاشة 3 — حسن", "جلسة محوّلة لدين · قبل 3 س", 12.0, ActivityKind.Session),
            Activity("سامر العطار", "دفعة · أمس", 150.0, ActivityKind.Payment),
        ),
    )

    fun slots(now: Long) = listOf(
        PsSlot(1, now - (1 * 3600 + 42 * 60 + 18) * 1000L, 120),
        PsSlot(2),
        PsSlot(3, now - (25 * 60 + 40) * 1000L, 60),
    )
}
