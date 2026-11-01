package dev.saged.qayd.data

import androidx.compose.runtime.Immutable

@Immutable
data class Reading(
    val id: String,
    val customerId: String,
    val prev: Double,
    val curr: Double,
    val kwh: Double,
    val price: Double,
    val amount: Double,
    val period: String,
    val at: Long,
    val byName: String,
)

@Immutable
data class Subscriber(
    val customer: Customer,
    /** آخر قراءة معروفة: آخر فاتورة أو قراءة البداية. */
    val last: Double,
    val thisMonth: Reading?,
    val history: List<Reading>,
) {
    val avgKwh: Double get() = history.take(6).map { it.kwh }.average().takeIf { !it.isNaN() } ?: 0.0
}

@Immutable
data class PowerState(val subscribers: List<Subscriber>, val monthKwh: Double, val monthAmount: Double) {
    val pending get() = subscribers.count { it.thisMonth == null }

    companion object {
        val Empty = PowerState(emptyList(), 0.0, 0.0)

        fun of(customers: List<Customer>, readings: List<Reading>, now: Long): PowerState {
            val period = Ledger.period(now)
            val byCustomer = readings.sortedByDescending { it.at }.groupBy { it.customerId }
            val subs = customers.filter { it.power }.map { c ->
                val h = byCustomer[c.id].orEmpty()
                Subscriber(c, h.firstOrNull()?.curr ?: c.initialReading, h.firstOrNull { it.period == period }, h)
            }.sortedBy { it.customer.name }
            val month = readings.filter { it.period == period }
            return PowerState(subs, round2(month.sumOf { it.kwh }), round2(month.sumOf { it.amount }))
        }

        /** الاستهلاك والمبلغ بالتناسب: (الحالية − السابقة) × سعر الكيلو، بلا تقريب للكيلو. */
        fun bill(prev: Double, curr: Double, price: Double): Pair<Double, Double> {
            val kwh = round2(curr - prev)
            return kwh to round2(kwh * price)
        }
    }
}
