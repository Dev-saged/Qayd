package dev.saged.qayd.data

import androidx.compose.runtime.Immutable

@Immutable
data class MenuItem(val id: String, val name: String, val price: Double, val category: String, val active: Boolean)

enum class OrderType(val key: String, val label: String) {
    Takeaway("takeaway", "سفري"), Dine("dine", "صالة"), Delivery("delivery", "توصيل");

    companion object { fun of(k: String?) = entries.firstOrNull { it.key == k } ?: Takeaway }
}

enum class OrderStatus(val key: String, val label: String) {
    New("new", "جديد"), Ready("ready", "جاهز"), Done("done", "مُسلَّم"), Cancelled("cancelled", "ملغي");

    companion object { fun of(k: String?) = entries.firstOrNull { it.key == k } ?: New }
}

@Immutable
data class OrderLine(val name: String, val price: Double, val qty: Int) {
    val total get() = round2(price * qty)
}

@Immutable
data class Order(
    val id: String,
    val no: Int,
    val lines: List<OrderLine>,
    val type: OrderType,
    val status: OrderStatus,
    val table: String,
    val phone: String,
    val address: String,
    val note: String,
    val at: Long,
    val byName: String,
    val txId: String = "",
) {
    val total get() = round2(lines.sumOf { it.total })
    val count get() = lines.sumOf { it.qty }
}

@Immutable
data class NewOrder(val lines: List<OrderLine>, val type: OrderType, val table: String, val phone: String, val address: String, val note: String)

/** طلبات اليوم وأرقامها. */
fun List<Order>.today(now: Long = System.currentTimeMillis()) = filter { it.at >= Ledger.dayStart(now) }.sortedByDescending { it.at }
fun List<Order>.nextNo(now: Long = System.currentTimeMillis()) = (today(now).maxOfOrNull { it.no } ?: 0) + 1
