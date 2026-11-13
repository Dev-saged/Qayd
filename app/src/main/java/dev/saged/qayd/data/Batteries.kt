package dev.saged.qayd.data

import androidx.compose.runtime.Immutable
import java.util.Calendar

private const val DAY = 86_400_000L

enum class WarrantyState(val label: String) { Active("سارية"), Soon("تنتهي قريباً"), Expired("منتهية") }

/** بيع بطارية سيارة: الرقم التسلسلي والكفالة. وتاريخ انتهاء الكفالة يُحسب ولا يُخزَّن. */
@Immutable
data class BattSale(
    val id: String, val serial: String, val model: String, val name: String, val phone: String, val car: String,
    val price: Double, val credit: Double, val oldKg: Double, val months: Int, val at: Long, val productId: String?, val txId: String?, val cost: Double = 0.0,
) {
    val net get() = round2((price - credit).coerceAtLeast(0.0))
    /** الربح: ما قُبض ناقص تكلفة البطارية، ويظهر فقط إن عُرفت التكلفة من المخزون. */
    val profit get() = if (cost > 0) round2(net - cost) else null
    val endAt: Long get() = Calendar.getInstance().apply { timeInMillis = at; add(Calendar.MONTH, months) }.timeInMillis
    fun daysLeft(now: Long = System.currentTimeMillis()): Int = Math.floorDiv(endAt - now, DAY).toInt()
    fun state(now: Long = System.currentTimeMillis()) = daysLeft(now).let { if (it < 0) WarrantyState.Expired else if (it <= 30) WarrantyState.Soon else WarrantyState.Active }
}

/** بيع خردة البطاريات القديمة بالكيلو. */
@Immutable
data class ScrapOut(val id: String, val kg: Double, val amount: Double, val at: Long)

@Immutable
data class BatteryState(val sales: List<BattSale>, val scrap: List<ScrapOut>) {
    /** رصيد الخردة: المستلم مع الاستبدال ناقص المباع. */
    val scrapKg get() = round2((sales.sumOf { it.oldKg } - scrap.sumOf { it.kg }).coerceAtLeast(0.0))
    fun monthProfit(now: Long = System.currentTimeMillis()): Double {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = now }
        return round2(sales.filter { s -> java.util.Calendar.getInstance().apply { timeInMillis = s.at }.let { it.get(java.util.Calendar.YEAR) == c.get(java.util.Calendar.YEAR) && it.get(java.util.Calendar.MONTH) == c.get(java.util.Calendar.MONTH) } }
            .sumOf { it.profit ?: 0.0 })
    }
    val soon get() = sales.count { it.state() != WarrantyState.Active && it.daysLeft() >= -60 }
    fun find(serial: String) = sales.filter { it.serial.equals(serial.trim(), true) }

    companion object { val Empty = BatteryState(emptyList(), emptyList()) }
}

data class NewBattery(
    val serial: String, val model: String, val name: String, val phone: String, val car: String,
    val price: Double, val credit: Double, val oldKg: Double, val months: Int, val productId: String?, val cost: Double,
)
