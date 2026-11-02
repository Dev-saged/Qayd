package dev.saged.qayd.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.saged.qayd.data.CashClose
import dev.saged.qayd.data.Ledger
import dev.saged.qayd.data.Tx
import dev.saged.qayd.data.TxKind
import dev.saged.qayd.data.cashToday
import dev.saged.qayd.ui.components.IconTile
import dev.saged.qayd.ui.components.NumpadSheet
import dev.saged.qayd.ui.components.QButton
import dev.saged.qayd.ui.components.QField
import dev.saged.qayd.ui.components.QSheet
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.theme.LocalPalette
import dev.saged.qayd.ui.util.clock
import dev.saged.qayd.ui.util.cur
import dev.saged.qayd.ui.util.dayLabel
import dev.saged.qayd.ui.util.ltr

private val STATES = listOf("pending" to "بانتظار التأكيد", "ok" to "وصلت", "missing" to "لم تصل")

/** مراجعة التحويلات: كل بيع أو دفعة بمحفظة أو بنك تنتظر تأكيد صاحب الحساب. */
@Composable
fun TransfersPage(
    padding: PaddingValues, ledger: Ledger, wallets: List<String>, canVerify: Boolean, canEditWallets: Boolean,
    onVerify: (Tx, String) -> Unit, onWallets: (List<String>) -> Unit, onBack: () -> Unit,
) {
    val p = LocalPalette.current
    var tab by rememberSaveable { mutableStateOf("pending") }
    var editWallets by remember { mutableStateOf(false) }
    val all = ledger.allTx.filter { it.wallet != null && (it.kind == TxKind.Sale || it.kind == TxKind.Payment) }
    val list = all.filter { (it.verify ?: "pending") == tab }.sortedByDescending { it.at }
    val month = Ledger.dayStart(System.currentTimeMillis()).let { java.util.Calendar.getInstance().apply { timeInMillis = it; set(java.util.Calendar.DAY_OF_MONTH, 1) }.timeInMillis }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(QIcons.Back, "رجوع", onBack)
                T("التحويلات", Modifier.weight(1f).padding(horizontal = 12.dp), 20.sp, FontWeight.Bold)
                if (canEditWallets) IconTile(QIcons.Wallet, "المحافظ والبنوك", { editWallets = true })
            }
        }
        item {
            val shape = RoundedCornerShape(22.dp)
            Column(Modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, p.line, shape).padding(16.dp)) {
                T("هذا الشهر حسب المحفظة", size = 13.sp, color = p.mute)
                val by = all.filter { it.at >= month && it.verify != "missing" }.groupBy { it.wallet!! }
                if (by.isEmpty()) T("لا تحويلات هذا الشهر.", Modifier.padding(top = 8.dp), 13.sp, color = p.mute)
                by.forEach { (w, v) ->
                    Row(Modifier.padding(top = 8.dp)) {
                        T(w, Modifier.weight(1f), 14.sp)
                        T("${v.size} · ${cur(v.sumOf { it.amount })}", size = 14.sp, weight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                STATES.forEach { (k, l) ->
                    val on = k == tab
                    val n = all.count { (it.verify ?: "pending") == k }
                    T("$l · $n", Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) p.pri.copy(alpha = .18f) else p.surface)
                        .clickable(role = Role.RadioButton) { tab = k }.padding(horizontal = 14.dp, vertical = 8.dp), 12.5.sp,
                        if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) p.pri else p.ink)
                }
            }
        }
        if (list.isEmpty()) item { T("لا تحويلات هنا.", Modifier.padding(vertical = 12.dp), 13.sp, color = p.mute) }
        items(list, key = { it.id }) { t ->
            val customer = t.customerId?.let { ledger.balanceOf(it)?.customer?.name }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(cur(t.amount), Modifier.weight(1f), 17.sp, FontWeight.SemiBold)
                    T(t.wallet.orEmpty(), Modifier.clip(RoundedCornerShape(10.dp)).background(p.pri.copy(alpha = .14f)).padding(horizontal = 10.dp, vertical = 4.dp), 12.sp, FontWeight.SemiBold, p.pri)
                }
                T(
                    listOfNotNull(
                        t.kind.label + (customer?.let { " من $it" } ?: ""),
                        t.payer.takeIf { it.isNotBlank() }?.let { "المحوِّل: $it" },
                        t.payerPhone.takeIf { it.isNotBlank() }?.let { ltr(it) },
                    ).joinToString(" · "),
                    Modifier.padding(top = 6.dp), 13.sp, maxLines = 2,
                )
                T("${t.byName} · ${dayLabel(t.at)} ${clock(t.at)}", Modifier.padding(top = 4.dp), 11.5.sp, color = p.mute)
                if (canVerify && tab == "pending") Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QButton("وصلت", { onVerify(t, "ok") }, Modifier.weight(1f))
                    QButton("لم تصل", { onVerify(t, "missing") }, Modifier.weight(1f), secondary = true)
                }
                if (canVerify && tab != "pending") T("إرجاع للانتظار", Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) { onVerify(t, "pending") }.padding(6.dp), 12.sp, FontWeight.Medium, p.mute)
            }
        }
    }
    if (editWallets) WalletsSheet(wallets, onWallets) { editWallets = false }
}

@Composable
private fun WalletsSheet(wallets: List<String>, onSave: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var list by remember { mutableStateOf(wallets) }
    var add by rememberSaveable { mutableStateOf("") }
    QSheet("المحافظ والبنوك", onDismiss, footer = { QButton("حفظ", { onSave(list); onDismiss() }) }) {
        T("تظهر كخيارات عند تسجيل بيع أو دفعة، و\"نقدي\" موجود دائماً.", size = 12.5.sp, color = p.mute, maxLines = 2)
        Spacer(Modifier.height(10.dp))
        list.forEach { w ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(p.surface2).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                T(w, Modifier.weight(1f), 14.5.sp)
                T("حذف", Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { list = list - w }.padding(6.dp), 12.5.sp, color = p.danger)
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
            QField(add, { add = it }, "إضافة", Modifier.weight(1f), hint = "بنك القدس، محفظة PalPay")
            QButton("أضف", { if (add.isNotBlank() && add.trim() !in list) { list = list + add.trim(); add = "" } }, Modifier.padding(start = 8.dp), secondary = true)
        }
    }
}

/** بيانات التحويل اختيارية: الاسم والجوال لتسهيل المراجعة. */
@Composable
fun PayerSheet(wallet: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    QSheet("تحويل عبر $wallet", onDismiss, footer = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QButton("تخطي", onDismiss, Modifier.weight(1f), secondary = true)
            QButton("حفظ", { onSave(name, phone); onDismiss() }, Modifier.weight(1f))
        }
    }) {
        T("سُجّلت العملية بانتظار تأكيد وصول المبلغ. أضف بيانات المحوِّل إن أردت.", size = 12.5.sp, color = LocalPalette.current.mute, maxLines = 2)
        Spacer(Modifier.height(10.dp))
        QField(name, { name = it }, "اسم المحوِّل (اختياري)")
        Spacer(Modifier.height(10.dp))
        QField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, "رقم الجوال (اختياري)", ltr = true, keyboard = KeyboardType.Phone)
    }
}

/** إغلاق الصندوق: نقد اليوم المتوقع لكل عضو مقابل الموجود فعلاً في الدرج. */
@Composable
fun CashClosePage(padding: PaddingValues, ledger: Ledger, uid: String, closes: List<CashClose>, isOwner: Boolean, onClose: (Double, Double, String) -> Unit, onBack: () -> Unit) {
    val p = LocalPalette.current
    val today = Ledger.dayStart(System.currentTimeMillis())
    val (inflow, out, expected) = ledger.cashToday(uid, today)
    var counting by remember { mutableStateOf(false) }
    val mineToday = closes.firstOrNull { it.by == uid && it.at >= today }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(QIcons.Back, "رجوع", onBack)
                T("إغلاق الصندوق", Modifier.weight(1f).padding(horizontal = 12.dp), 20.sp, FontWeight.Bold)
            }
        }
        item {
            val shape = RoundedCornerShape(24.dp)
            Column(Modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, p.line, shape).padding(18.dp)) {
                T("نقدك اليوم", size = 13.sp, color = p.mute)
                Row(Modifier.padding(top = 10.dp)) { T("مبيعات ودفعات نقدية", Modifier.weight(1f), 14.sp); T(cur(inflow), size = 14.sp, weight = FontWeight.Medium, color = p.ok) }
                Row(Modifier.padding(top = 6.dp)) { T("مصروفات دفعتها", Modifier.weight(1f), 14.sp); T(cur(out), size = 14.sp, weight = FontWeight.Medium, color = p.danger) }
                Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(p.line))
                Row { T("المتوقع في الدرج", Modifier.weight(1f), 15.sp, FontWeight.SemiBold); T(cur(expected), size = 18.sp, weight = FontWeight.Bold) }
                T("التحويلات لا تدخل هنا لأنها ليست نقداً.", Modifier.padding(top = 6.dp), 11.5.sp, color = p.mute)
                Spacer(Modifier.height(14.dp))
                if (mineToday != null) T("أغلقت صندوقك اليوم: الفعلي ${cur(mineToday.actual)} · الفرق ${ltr(dev.saged.qayd.ui.util.money(mineToday.diff))}", size = 13.sp,
                    weight = FontWeight.Medium, color = if (mineToday.diff == 0.0) p.ok else p.warn, maxLines = 2)
                QButton(if (mineToday == null) "عدّ الدرج وأغلق" else "إغلاق جديد", { counting = true }, secondary = mineToday != null)
            }
        }
        val shown = closes.filter { isOwner || it.by == uid }.sortedByDescending { it.at }.take(60)
        if (shown.isNotEmpty()) item { T(if (isOwner) "سجل الإغلاقات لكل الفريق" else "إغلاقاتك", Modifier.padding(top = 6.dp), 14.5.sp, FontWeight.SemiBold) }
        items(shown, key = { it.id }) { c ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("${c.byName} · ${dayLabel(c.at)} ${clock(c.at)}", size = 13.5.sp, weight = FontWeight.Medium)
                    T("المتوقع ${cur(c.expected)} · الفعلي ${cur(c.actual)}${if (c.note.isNotBlank()) " · ${c.note}" else ""}", Modifier.padding(top = 3.dp), 12.sp, color = p.mute, maxLines = 2)
                }
                T(if (c.diff == 0.0) "مطابق" else ltr((if (c.diff > 0) "+" else "") + dev.saged.qayd.ui.util.money(c.diff)), size = 14.sp,
                    weight = FontWeight.SemiBold, color = when { c.diff == 0.0 -> p.ok; c.diff > 0 -> p.warn; else -> p.danger })
            }
        }
    }
    if (counting) NumpadSheet("الموجود في الدرج", "المتوقع ${cur(expected)}", p.pri, "اعتماد الإغلاق",
        label = "المبلغ الفعلي", preview = { v -> if (v == expected) "مطابق" to true else "الفرق ${ltr(dev.saged.qayd.ui.util.money(v - expected))}" to (v >= expected) },
        onDismiss = { counting = false },
    ) { v, _ -> onClose(expected, v, ""); counting = false }
}
