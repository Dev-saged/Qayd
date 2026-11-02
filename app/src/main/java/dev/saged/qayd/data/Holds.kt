package dev.saged.qayd.data

import androidx.compose.runtime.Immutable

enum class HoldStatus(val key: String, val label: String) {
    Open("open", "محجوز"), Done("done", "سُلِّم"), Cancelled("cancelled", "ملغي");

    companion object { fun of(k: String?) = entries.firstOrNull { it.key == k } ?: Open }
}

/** حجز بعربون: يدفع الزبون جزءاً ويستلم عند الإكمال. */
@Immutable
data class Hold(
    val id: String,
    val name: String,
    val phone: String,
    val title: String,
    val productId: String?,
    val variant: String,
    val total: Double,
    val pays: List<Pair<Double, Long>>,
    val status: HoldStatus,
    val at: Long,
) {
    val paid get() = round2(pays.sumOf { it.first })
    val left get() = round2((total - paid).coerceAtLeast(0.0))
}
