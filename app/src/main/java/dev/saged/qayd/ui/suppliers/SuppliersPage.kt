package dev.saged.qayd.ui.suppliers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.data.STxKind
import dev.saged.qayd.data.SupplierBalance
import dev.saged.qayd.data.SupplierState
import dev.saged.qayd.ui.components.IconTile
import dev.saged.qayd.ui.components.NumpadSheet
import dev.saged.qayd.ui.components.QButton
import dev.saged.qayd.ui.components.QField
import dev.saged.qayd.ui.components.QIcon
import dev.saged.qayd.ui.components.QSheet
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.stock.StockActions
import dev.saged.qayd.ui.theme.LocalPalette
import dev.saged.qayd.ui.util.cur
import dev.saged.qayd.ui.util.dayLabel
import dev.saged.qayd.ui.util.ltr

/** الموردون: ما على المحل لكل تاجر، وفواتير الشراء والدفعات. */
@Composable
fun SuppliersPage(padding: PaddingValues, state: SupplierState, canManage: Boolean, actions: StockActions, onBack: () -> Unit) {
    val p = LocalPalette.current
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    val current = open?.let { state.of(it) }
    if (current != null) return SupplierDetail(padding, current, state, canManage, actions) { open = null }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(QIcons.Back, "رجوع", onBack)
                T("الموردون", Modifier.weight(1f).padding(horizontal = 12.dp), 20.sp, FontWeight.Bold)
                if (canManage) IconTile(QIcons.Plus, "مورد جديد", { adding = true })
            }
        }
        item {
            val shape = RoundedCornerShape(24.dp)
            Column(Modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(p.surface, p.surface2))).border(1.dp, p.line, shape).padding(18.dp)) {
                T("المستحق علينا للموردين", size = 13.sp, color = p.mute)
                T(cur(state.totalOwed), Modifier.padding(top = 4.dp), 28.sp, FontWeight.SemiBold, if (state.totalOwed > 0) p.danger else p.ok)
                T("${state.items.size} مورد", Modifier.padding(top = 4.dp), 12.sp, color = p.mute)
            }
        }
        if (state.items.isEmpty()) item {
            T("لا موردين بعد. أضف التجار الذين تشتري منهم لتتابع فواتيرهم ودفعاتك لهم.", Modifier.padding(vertical = 16.dp), 13.5.sp, color = p.mute, maxLines = 3)
        }
        items(state.items, key = { it.supplier.id }) { s ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface).clickable(role = Role.Button) { open = s.supplier.id }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(p.surface2), contentAlignment = Alignment.Center) {
                    QIcon(QIcons.Truck, p.pri, Modifier.size(21.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    T(s.supplier.name, size = 14.5.sp, weight = FontWeight.Medium)
                    T(if (s.last > 0) "آخر حركة ${dayLabel(s.last)}" else "لا حركات", Modifier.padding(top = 3.dp), 12.sp, color = p.mute)
                }
                T(cur(kotlin.math.abs(s.owed)), size = 14.5.sp, weight = FontWeight.SemiBold, color = if (s.owed > 0) p.danger else p.ok)
            }
        }
    }
    if (adding) SupplierForm({ n, ph -> open = actions.addSupplier(n, ph); adding = false }) { adding = false }
}

@Composable
private fun SupplierDetail(padding: PaddingValues, s: SupplierBalance, state: SupplierState, canManage: Boolean, actions: StockActions, onBack: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var pad by remember { mutableStateOf<STxKind?>(null) }
    androidx.activity.compose.BackHandler(onBack = onBack)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(QIcons.Back, "رجوع", onBack)
                T(s.supplier.name, Modifier.weight(1f).padding(horizontal = 12.dp), 20.sp, FontWeight.Bold)
                if (s.supplier.phone.isNotBlank()) IconTile(QIcons.Phone, "اتصال", {
                    runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:${s.supplier.phone}"))) }
                })
            }
        }
        item {
            val shape = RoundedCornerShape(24.dp)
            Column(Modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, p.line, shape).padding(18.dp)) {
                T(if (s.owed >= 0) "المستحق علينا" else "رصيد لنا عنده", size = 13.sp, color = p.mute)
                T(cur(kotlin.math.abs(s.owed)), Modifier.padding(top = 4.dp), 28.sp, FontWeight.SemiBold, if (s.owed > 0) p.danger else p.ok)
                if (canManage) Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QButton("فاتورة شراء", { pad = STxKind.Purchase }, Modifier.weight(1f), secondary = true)
                    QButton("دفعة للمورد", { pad = STxKind.Payment }, Modifier.weight(1f))
                }
            }
        }
        val h = state.history(s.supplier.id)
        if (h.isEmpty()) item { T("لا حركات بعد.", Modifier.padding(vertical = 10.dp), 13.sp, color = p.mute) }
        items(h, key = { it.id }) { t ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T(t.kind.label, size = 14.sp, weight = FontWeight.Medium)
                    T("${t.note.ifBlank { t.byName }} · ${dayLabel(t.at)}", Modifier.padding(top = 3.dp), 12.sp, color = p.mute, maxLines = 2)
                }
                T(ltr((if (t.kind == STxKind.Purchase) "+" else "−") + dev.saged.qayd.ui.util.money(t.amount)), size = 14.5.sp, weight = FontWeight.SemiBold,
                    color = if (t.kind == STxKind.Purchase) p.danger else p.ok)
            }
        }
    }
    pad?.let { k ->
        NumpadSheet(
            "${k.label} · ${s.supplier.name}", if (k == STxKind.Purchase) "يزيد ما علينا للمورد" else "ينقص ما علينا للمورد",
            if (k == STxKind.Purchase) p.danger else p.ok, if (k == STxKind.Purchase) "سجّل الفاتورة" else "سجّل الدفعة",
            preview = { v -> "بعدها: ${cur(kotlin.math.abs(s.owed + if (k == STxKind.Purchase) v else -v))}" to true },
            onDismiss = { pad = null },
        ) { v, _ -> if (v > 0) actions.supplierTx(s.supplier.id, k, v, ""); pad = null }
    }
}

@Composable
private fun SupplierForm(onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    QSheet("مورد جديد", onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            QField(name, { name = it }, "اسم المورد", hint = "شركة الأمل للتوزيع", error = if (tried && name.isBlank()) "اكتب الاسم" else null)
            QField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, "الجوال (اختياري)", ltr = true, keyboard = KeyboardType.Phone)
            QButton("إضافة", { tried = true; if (name.isNotBlank()) onAdd(name, phone) })
        }
    }
}
