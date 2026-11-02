package dev.saged.qayd.data

import androidx.compose.runtime.Immutable

@Immutable
data class Report(
    val week: List<Pair<Long, Double>>,
    val monthSales: Double,
    val monthCollected: Double,
    val monthDebts: Double,
    val monthExpenses: Double,
    val psMonth: Double,
    val powerMonth: Double,
    val topDebtors: List<CustomerBalance>,
    val byMember: List<Pair<String, Double>>,
    val monthDiscounts: Double = 0.0,
    val byWallet: List<Pair<String, Double>> = emptyList(),
    val topCustomers: List<Pair<CustomerBalance, Double>> = emptyList(),
) {
    /** الدخل النقدي = المبيعات + التحصيل، والصافي بعد المصروفات. */
    val income get() = round2(monthSales + monthCollected)
    val net get() = round2(income - monthExpenses)
}

fun Ledger.report(now: Long): Report {
    val today = Ledger.dayStart(now)
    val month = Ledger.dayStart(now).let { java.util.Calendar.getInstance().apply { timeInMillis = it; set(java.util.Calendar.DAY_OF_MONTH, 1) }.timeInMillis }
    val inMonth = allTx.filter { it.at >= month }
    fun sum(k: TxKind, f: (Tx) -> Boolean = { true }) = round2(inMonth.filter { it.kind == k && f(it) }.sumOf { it.amount })
    return Report(
        week = (6 downTo 0).map { d ->
            val start = today - d * 86_400_000L
            start to round2(allTx.filter { it.kind == TxKind.Sale && it.at >= start && it.at < start + 86_400_000L }.sumOf { it.amount })
        },
        monthSales = sum(TxKind.Sale),
        monthCollected = sum(TxKind.Payment),
        monthDebts = sum(TxKind.Debt),
        monthExpenses = sum(TxKind.Expense),
        psMonth = round2(inMonth.filter { it.source == "ps" }.sumOf { it.amount }),
        powerMonth = round2(inMonth.filter { it.source == "power" }.sumOf { it.amount }),
        topDebtors = customers.filter { it.balance > 0 }.sortedByDescending { it.balance }.take(5),
        byMember = inMonth.filter { it.kind == TxKind.Sale || it.kind == TxKind.Payment }
            .groupBy { it.byName.ifBlank { "—" } }.map { (k, v) -> k to round2(v.sumOf { it.amount }) }.sortedByDescending { it.second },
        monthDiscounts = sum(TxKind.Discount),
        byWallet = inMonth.filter { (it.kind == TxKind.Sale || it.kind == TxKind.Payment) && it.verify != "missing" }
            .groupBy { it.wallet ?: "نقدي" }.map { (k, v) -> k to round2(v.sumOf { it.amount }) }.sortedByDescending { it.second },
        topCustomers = topCustomers(now),
    )
}

/**
 * أهم الزبائن آخر 90 يوماً: ما دفعوه أولاً، ثم انتظامهم (عدد الدفعات)، مع خصم للمتأخرين.
 */
fun Ledger.topCustomers(now: Long, n: Int = 5): List<Pair<CustomerBalance, Double>> {
    val since = now - 90 * 86_400_000L
    return customers.map { c ->
        val h = history(c.customer.id).filter { it.at >= since }
        val paid = h.filter { it.kind == TxKind.Payment }.sumOf { it.amount }
        val score = paid + h.count { it.kind == TxKind.Payment } * 20.0 - (if (c.late) paid * .5 else 0.0)
        c to score
    }.filter { it.second > 0 }.sortedByDescending { it.second }.take(n).map { (c, _) ->
        c to round2(history(c.customer.id).filter { it.at >= since && it.kind == TxKind.Payment }.sumOf { it.amount })
    }
}
