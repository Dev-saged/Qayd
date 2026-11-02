package dev.saged.qayd.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.data.Access
import dev.saged.qayd.data.Customer
import dev.saged.qayd.data.Member
import dev.saged.qayd.data.Perm
import dev.saged.qayd.data.Prices
import dev.saged.qayd.data.TemplateKind
import dev.saged.qayd.ui.home.PsSlot
import dev.saged.qayd.ui.util.cur
import dev.saged.qayd.data.CustomerBalance
import dev.saged.qayd.data.ExpenseCategory
import dev.saged.qayd.data.RecurringStatus
import dev.saged.qayd.data.Tx
import dev.saged.qayd.data.TxKind
import dev.saged.qayd.ui.components.ActionRow
import dev.saged.qayd.ui.components.QButton
import dev.saged.qayd.ui.components.QField
import dev.saged.qayd.ui.components.QIcon
import dev.saged.qayd.ui.components.QSheet
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.theme.LocalPalette
import dev.saged.qayd.ui.util.balanceText
import dev.saged.qayd.ui.util.money
import dev.saged.qayd.ui.util.norm

/** كل عمليات الكتابة التي تحتاجها شاشات المحل. */
class ShopActions(
    val sale: (Double) -> Unit,
    val debt: (String, Double) -> Unit,
    val payment: (String, Double) -> Unit,
    val expense: (ExpenseCategory, Double, String?) -> Unit,
    val deleteTx: (Tx) -> Unit,
    val addCustomer: (String, String) -> String,
    val addRecurring: (String, ExpenseCategory, Int) -> Unit,
    val deleteRecurring: (String) -> Unit,
    val updateMember: (Member, Set<Perm>, Boolean) -> Unit = { _, _, _ -> },
    val psStart: (Int, Int) -> Unit = { _, _ -> },
    val psPause: (PsSlot) -> Unit = {},
    val psExtend: (PsSlot) -> Unit = {},
    val psEnd: (PsSlot, Double, String?) -> Unit = { _, _, _ -> },
    val setPrice: (String, Double) -> Unit = { _, _ -> },
    val addReading: (String, Double, Double) -> Unit = { _, _, _ -> },
    val addSubscriber: (String, String, String, Double) -> Unit = { _, _, _, _ -> },
    val enqueueSms: (List<Triple<String, String, String>>, String) -> Unit = { _, _ -> },
    val retrySms: (String) -> Unit = {},
    val saveTemplate: (String?, String, String, TemplateKind) -> Unit = { _, _, _, _ -> },
    val deleteTemplate: (String) -> Unit = {},
    val gateway: (Boolean) -> Unit = {},
    val backup: () -> Unit = {},
    val setLimit: (String, Double) -> Unit = { _, _ -> },
    val setWaPrefix: (String, String) -> Unit = { _, _ -> },
    val saveSmsSettings: (dev.saged.qayd.data.SmsSettings) -> Unit = {},
    val requestGateway: () -> Unit = {},
    val answerGateway: (Boolean) -> Unit = {},
    val saleWith: (Double, String?) -> String = { _, _ -> "" },
    val paymentWith: (String, Double, String?) -> String = { _, _, _ -> "" },
    val discount: (String, Double) -> Unit = { _, _ -> },
    val setPayer: (String, String, String) -> Unit = { _, _, _ -> },
    val verifyTx: (Tx, String) -> Unit = { _, _ -> },
    val saveWallets: (List<String>) -> Unit = {},
    val cashClose: (Double, Double, String) -> Unit = { _, _, _ -> },
) {
    companion object {
        val None = ShopActions({}, { _, _ -> }, { _, _ -> }, { _, _, _ -> }, {}, { _, _ -> "" }, { _, _, _ -> }, {})
    }
}

/** طلب فتح النمباد لعملية محددة. */
sealed interface PadRequest {
    data object Sale : PadRequest
    data class Debt(val customer: Customer) : PadRequest
    data class Payment(val customer: Customer) : PadRequest
    data class Expense(val recurring: RecurringStatus? = null) : PadRequest
    data class Price(val key: String, val title: String, val current: Double) : PadRequest
    data class Limit(val customer: Customer) : PadRequest
    data class Discount(val customer: Customer) : PadRequest
}

@Composable
fun PricesSheet(prices: Prices, ps: Boolean, power: Boolean, onEdit: (PadRequest.Price) -> Unit, onDismiss: () -> Unit) {
    QSheet("الأسعار", onDismiss) {
        if (ps) ActionRow(QIcons.Pad, "سعر ساعة الشاشة", if (prices.psRate > 0) cur(prices.psRate) else "غير محدد") {
            onEdit(PadRequest.Price("psRate", "سعر ساعة الشاشة", prices.psRate))
        }
        if (power) ActionRow(QIcons.Bolt, "سعر كيلو الكهرباء", if (prices.kwhPrice > 0) cur(prices.kwhPrice) else "غير محدد") {
            onEdit(PadRequest.Price("kwhPrice", "سعر كيلو الكهرباء", prices.kwhPrice))
        }
        T("السعر الجديد يُطبق على الجلسات والفواتير الجديدة فقط، والقديمة تبقى بسعرها.", Modifier.padding(top = 8.dp), 12.5.sp, color = LocalPalette.current.mute, maxLines = 2)
    }
}

@Composable
fun NewActionSheet(access: Access, onPick: (TxKind) -> Unit, onDismiss: () -> Unit) {
    QSheet("عملية جديدة", onDismiss) {
        if (access.can(Perm.QuickSale)) ActionRow(QIcons.Cart, "بيع سريع", "مبلغ فقط، يُسجل فوراً كبيع للمحل") { onPick(TxKind.Sale) }
        if (access.can(Perm.AddDebt)) ActionRow(QIcons.Up, "دين على زبون", "اختر الزبون ثم المبلغ") { onPick(TxKind.Debt) }
        if (access.can(Perm.AddPayment)) ActionRow(QIcons.Down, "دفعة من زبون", "تُخصم من رصيده، والزيادة تبقى له") { onPick(TxKind.Payment) }
        if (access.can(Perm.Expenses)) ActionRow(QIcons.Wallet, "مصروف", "بضاعة، إنترنت، كهرباء، إيجار…") { onPick(TxKind.Expense) }
    }
}

@Composable
fun CustomerPickerSheet(
    title: String,
    customers: List<CustomerBalance>,
    onPick: (Customer) -> Unit,
    onCreate: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    var q by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(false) }
    QSheet(title, onDismiss) {
        if (adding) {
            NewCustomerForm(initialName = q, onSave = onCreate)
            return@QSheet
        }
        QField(q, { q = it }, "بحث بالاسم أو الرقم", hint = "اكتب للبحث")
        Spacer(Modifier.height(10.dp))
        ActionRow(QIcons.UserPlus, if (q.isBlank()) "زبون جديد" else "زبون جديد: $q") { adding = true }
        val list = customers.filter { q.isBlank() || norm(it.customer.name).contains(norm(q)) || it.customer.phone.contains(q.trim()) }
            .sortedByDescending { it.lastAt }.take(40)
        list.forEach { c ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { onPick(c.customer) }
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(p.surface2), contentAlignment = Alignment.Center) {
                    T(c.customer.name.take(1), size = 15.sp, weight = FontWeight.SemiBold, color = p.pri)
                }
                T(c.customer.name, Modifier.weight(1f).padding(horizontal = 12.dp), 14.5.sp)
                val (txt, col) = balanceText(c.balance, p)
                T(txt, size = 13.sp, weight = FontWeight.SemiBold, color = col)
            }
        }
        if (list.isEmpty() && customers.isNotEmpty()) T("لا نتائج", Modifier.padding(16.dp), 13.sp, color = p.mute)
    }
}

@Composable
fun NewCustomerForm(initialName: String = "", onSave: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var phone by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QField(name, { name = it }, "اسم الزبون", hint = "محمود أبو سعدة", error = if (tried && name.isBlank()) "اكتب الاسم" else null)
        QField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, "رقم الجوال (اختياري)", hint = "0599123456", ltr = true, keyboard = KeyboardType.Phone)
        QButton("حفظ الزبون", { tried = true; if (name.isNotBlank()) onSave(name, phone) })
    }
}

@Composable
fun ConfirmDeleteSheet(tx: Tx, who: String?, onDelete: (() -> Unit)?, onReceipt: (() -> Unit)?, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    QSheet("الحركة", onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QIcon(QIcons.Trash, p.danger, Modifier.size(22.dp))
            T(
                "${tx.kind.label}${if (who != null) " — $who" else ""} بمبلغ ⁦${money(tx.amount)}⁩",
                Modifier.padding(start = 10.dp), 15.sp, FontWeight.Medium, maxLines = 2,
            )
        }
        Spacer(Modifier.height(18.dp))
        if (onReceipt != null) {
            QButton("إيصال PDF", { onReceipt(); onDismiss() })
            Spacer(Modifier.height(10.dp))
        }
        if (onDelete != null) {
            T("الحذف يغيّر الرصيد، ويبقى أثره في سجل التدقيق.", Modifier.padding(bottom = 8.dp), 12.5.sp, color = p.mute, maxLines = 2)
            QButton("احذف الحركة", { onDelete(); onDismiss() }, secondary = true)
        }
    }
}
