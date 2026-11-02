package dev.saged.qayd.data

import androidx.compose.runtime.Immutable

enum class Perm(val key: String, val label: String, val group: String) {
    QuickSale("quickSale", "البيع السريع", "المبيعات والديون"),
    AddDebt("addDebt", "تسجيل دين", "المبيعات والديون"),
    AddPayment("addPayment", "تسجيل دفعة", "المبيعات والديون"),
    ManageCustomers("manageCustomers", "إضافة الزبائن", "المبيعات والديون"),
    DeleteTx("deleteTx", "حذف الحركات", "المبيعات والديون"),
    ViewBalances("viewBalances", "رؤية الأرصدة والإجماليات", "المبيعات والديون"),
    Expenses("expenses", "المصروفات", "الإدارة"),
    ViewReports("viewReports", "التقارير", "الإدارة"),
    SetPrices("setPrices", "تغيير الأسعار", "الإدارة"),
    ExportPdf("exportPdf", "كشف الحساب PDF", "الإدارة"),
    ManagePs("managePs", "تشغيل الشاشات", "الوحدات"),
    RecordReadings("recordReadings", "قراءات الكهرباء", "الوحدات"),
    ManageElectricity("manageElectricity", "إدارة مشتركي الكهرباء", "الوحدات"),
    SendSms("sendSms", "إرسال الرسائل", "الوحدات"),
    ManageTemplates("manageTemplates", "قوالب الرسائل", "الوحدات"),
    ManageStock("manageStock", "إدارة الأصناف والمخزون", "الوحدات"),
    ManageSuppliers("manageSuppliers", "الموردون وفواتير الشراء", "الوحدات"),
    ManageMenu("manageMenu", "تعديل قائمة الطعام", "الوحدات"),
    ConfirmTransfers("confirmTransfers", "مراجعة التحويلات وتأكيدها", "الإدارة"),
    Discount("discount", "منح خصم للزبون", "المبيعات والديون"),
}

@Immutable
data class Member(
    val uid: String,
    val username: String,
    val displayName: String,
    val role: Role,
    val perms: Set<Perm>,
    val active: Boolean,
    val avatar: Int? = null,
)

/** ما يسمح به الحساب الحالي. صاحب المحل ومالك المنصة لهم كل شيء. */
@Immutable
data class Access(val role: Role, val perms: Set<Perm>, val active: Boolean = true) {
    fun can(p: Perm) = role != Role.Staff || (active && p in perms)

    companion object {
        val Full = Access(Role.Owner, Perm.entries.toSet())
    }
}

@Immutable
data class AuditEntry(val id: String, val action: String, val detail: String, val amount: Double?, val byName: String, val at: Long)

/** تقرير الوردية: ما سجّله كل عضو اليوم. */
@Immutable
data class ShiftTotals(val sales: Double, val salesCount: Int, val collected: Double, val debts: Double)

fun Ledger.shift(uid: String, today: Long): ShiftTotals {
    val mine = allTx.filter { it.by == uid && it.at >= today }
    return ShiftTotals(
        sales = round2(mine.filter { it.kind == TxKind.Sale }.sumOf { it.amount }),
        salesCount = mine.count { it.kind == TxKind.Sale },
        collected = round2(mine.filter { it.kind == TxKind.Payment }.sumOf { it.amount }),
        debts = round2(mine.filter { it.kind == TxKind.Debt }.sumOf { it.amount }),
    )
}

/** إغلاق صندوق: المتوقع من حركات اليوم والفعلي في الدرج والفرق. */
@Immutable
data class CashClose(val id: String, val byName: String, val by: String, val at: Long, val expected: Double, val actual: Double, val note: String) {
    val diff get() = round2(actual - expected)
}

/** نقد هذا العضو اليوم: المبيعات والدفعات النقدية ناقص مصروفاته. */
fun Ledger.cashToday(uid: String, today: Long): Triple<Double, Double, Double> {
    val mine = allTx.filter { it.by == uid && it.at >= today && it.wallet == null }
    val inflow = round2(mine.filter { it.kind == TxKind.Sale || it.kind == TxKind.Payment }.sumOf { it.amount })
    val out = round2(mine.filter { it.kind == TxKind.Expense }.sumOf { it.amount })
    return Triple(inflow, out, round2(inflow - out))
}
