package dev.saged.qayd.data

import androidx.compose.runtime.Immutable
import java.util.Calendar
import java.util.Locale

enum class TxKind(val key: String, val label: String) {
    Sale("sale", "بيع"),
    Debt("debt", "دين"),
    Payment("payment", "دفعة"),
    Expense("expense", "مصروف");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}

enum class ExpenseCategory(val key: String, val label: String) {
    Goods("goods", "بضاعة"),
    Internet("internet", "اشتراك إنترنت"),
    Power("power", "كهرباء"),
    Rent("rent", "إيجار"),
    Salaries("salaries", "رواتب"),
    Other("other", "أخرى");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: Other
    }
}

@Immutable
data class Customer(val id: String, val name: String, val phone: String, val note: String, val createdAt: Long)

@Immutable
data class Tx(
    val id: String,
    val kind: TxKind,
    val amount: Double,
    val at: Long,
    val customerId: String? = null,
    val category: ExpenseCategory? = null,
    val recurringId: String? = null,
    val period: String? = null,
    val byName: String = "",
    val pending: Boolean = false,
)

@Immutable
data class Recurring(val id: String, val title: String, val category: ExpenseCategory, val day: Int)

@Immutable
data class CustomerBalance(
    val customer: Customer,
    /** موجب = عليه، سالب = له (دفعة مقدمة أو زيادة). */
    val balance: Double,
    val lastAt: Long,
    val late: Boolean,
)

@Immutable
data class RecurringStatus(val item: Recurring, val paid: Tx?, val lastAmount: Double?)

/** كل الأرقام تُشتق من الحركات؛ لا رصيد مخزَّن. */
@Immutable
data class Ledger(
    val customers: List<CustomerBalance>,
    val totalDebt: Double,
    val totalCredit: Double,
    val late: Int,
    val todaySales: Double,
    val todaySalesCount: Int,
    val todayCollected: Double,
    val todaySalesList: List<Tx>,
    val monthSales: Double,
    val monthExpenses: Double,
    val monthByCategory: Map<ExpenseCategory, Double>,
    val monthExpenseList: List<Tx>,
    val recurring: List<RecurringStatus>,
    val debtTrend: List<Float>,
    private val txByCustomer: Map<String, List<Tx>>,
) {
    fun history(customerId: String): List<Tx> = txByCustomer[customerId].orEmpty()
    fun balanceOf(customerId: String): CustomerBalance? = customers.firstOrNull { it.customer.id == customerId }

    companion object {
        val Empty = of(emptyList(), emptyList(), emptyList(), System.currentTimeMillis(), 30)

        fun period(t: Long): String {
            val c = Calendar.getInstance().apply { timeInMillis = t }
            return String.format(Locale.US, "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
        }

        fun dayStart(t: Long): Long = Calendar.getInstance().apply {
            timeInMillis = t
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        private fun monthStart(t: Long): Long = Calendar.getInstance().apply {
            timeInMillis = dayStart(t)
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis

        private fun signed(t: Tx) = when (t.kind) {
            TxKind.Debt -> t.amount
            TxKind.Payment -> -t.amount
            else -> 0.0
        }

        fun of(customers: List<Customer>, txs: List<Tx>, recurring: List<Recurring>, now: Long, overdueDays: Int): Ledger {
            val today = dayStart(now)
            val month = monthStart(now)
            val sorted = txs.sortedByDescending { it.at }
            val byCustomer = sorted.filter { it.customerId != null && (it.kind == TxKind.Debt || it.kind == TxKind.Payment) }
                .groupBy { it.customerId!! }

            val balances = customers.map { c ->
                val h = byCustomer[c.id].orEmpty()
                val bal = round2(h.sumOf(::signed))
                val lastPay = h.firstOrNull { it.kind == TxKind.Payment }?.at
                val firstDebt = h.lastOrNull { it.kind == TxKind.Debt }?.at
                val ref = lastPay ?: firstDebt ?: now
                CustomerBalance(c, bal, h.firstOrNull()?.at ?: c.createdAt, bal > 0 && now - ref > overdueDays * 86_400_000L)
            }

            val sales = sorted.filter { it.kind == TxKind.Sale }
            val todaySales = sales.filter { it.at >= today }
            val expenses = sorted.filter { it.kind == TxKind.Expense && it.at >= month }
            val thisPeriod = period(now)
            val status = recurring.sortedBy { it.day }.map { r ->
                val all = sorted.filter { it.kind == TxKind.Expense && it.recurringId == r.id }
                RecurringStatus(r, all.firstOrNull { it.period == thisPeriod }, all.firstOrNull()?.amount)
            }

            // منحنى إجمالي الديون آخر 14 يوماً (نهاية كل يوم)
            val debtTx = sorted.filter { it.kind == TxKind.Debt || it.kind == TxKind.Payment }
            val total = balances.sumOf { it.balance }
            val trend = (13 downTo 0).map { d ->
                val end = today + 86_400_000L - d * 86_400_000L
                (total - debtTx.filter { it.at >= end }.sumOf(::signed)).toFloat()
            }

            return Ledger(
                customers = balances,
                totalDebt = round2(balances.filter { it.balance > 0 }.sumOf { it.balance }),
                totalCredit = round2(-balances.filter { it.balance < 0 }.sumOf { it.balance }),
                late = balances.count { it.late },
                todaySales = round2(todaySales.sumOf { it.amount }),
                todaySalesCount = todaySales.size,
                todayCollected = round2(sorted.filter { it.kind == TxKind.Payment && it.at >= today }.sumOf { it.amount }),
                todaySalesList = todaySales,
                monthSales = round2(sales.filter { it.at >= month }.sumOf { it.amount }),
                monthExpenses = round2(expenses.sumOf { it.amount }),
                monthByCategory = expenses.groupBy { it.category ?: ExpenseCategory.Other }.mapValues { (_, v) -> round2(v.sumOf { it.amount }) },
                monthExpenseList = expenses,
                recurring = status,
                debtTrend = if (debtTx.isEmpty()) emptyList() else trend,
                txByCustomer = byCustomer,
            )
        }
    }
}

fun round2(v: Double) = Math.round(v * 100.0) / 100.0

/** إدخال المبلغ من النمباد: أرقام، فاصلة عشرية واحدة، منزلتان، وحد أعلى. */
object AmountInput {
    const val MAX = 9_999_999.0

    fun press(text: String, key: Char): String = when {
        key == '.' -> when {
            text.contains('.') -> text
            text.isEmpty() -> "0."
            else -> "$text."
        }
        !key.isDigit() -> text
        text.contains('.') && text.substringAfter('.').length >= 2 -> text
        text == "0" -> if (key == '0') text else key.toString()
        else -> (text + key).takeIf { (it.toDoubleOrNull() ?: 0.0) <= MAX } ?: text
    }

    fun back(text: String): String = text.dropLast(1)

    fun add(text: String, delta: Int): String {
        val v = round2((text.toDoubleOrNull() ?: 0.0) + delta).coerceAtMost(MAX)
        return if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
    }

    fun value(text: String): Double = round2(text.toDoubleOrNull() ?: 0.0)

    fun of(v: Double?): String = when {
        v == null || v <= 0.0 -> ""
        v % 1.0 == 0.0 -> v.toLong().toString()
        else -> round2(v).toString()
    }
}
