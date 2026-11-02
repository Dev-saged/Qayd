package dev.saged.qayd.ui.holds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import dev.saged.qayd.data.Hold
import dev.saged.qayd.data.HoldStatus
import dev.saged.qayd.data.ProductStock
import dev.saged.qayd.ui.components.IconTile
import dev.saged.qayd.ui.components.NumpadSheet
import dev.saged.qayd.ui.components.QButton
import dev.saged.qayd.ui.components.QField
import dev.saged.qayd.ui.components.QSheet
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.theme.LocalPalette
import dev.saged.qayd.ui.util.cur
import dev.saged.qayd.ui.util.dayLabel

class HoldActions(
    val add: (name: String, phone: String, title: String, productId: String?, variant: String, total: Double, deposit: Double) -> Unit = { _, _, _, _, _, _, _ -> },
    val pay: (Hold, Double) -> Unit = { _, _ -> },
    val deliver: (Hold) -> Unit = {},
    val cancel: (Hold) -> Unit = {},
) {
    companion object { val None = HoldActions() }
}

/** العربون والحجز: الزبون يدفع جزءاً ويستلم عند الإكمال. */
@Composable
fun HoldsPage(padding: PaddingValues, holds: List<Hold>, products: List<ProductStock>, canCancel: Boolean, actions: HoldActions, onBack: () -> Unit) {
    val p = LocalPalette.current
    var filter by rememberSaveable { mutableStateOf(HoldStatus.Open) }
    var adding by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    val list = holds.filter { it.status == filter }.sortedByDescending { it.at }
    val active = holds.filter { it.status == HoldStatus.Open }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(QIcons.Back, "رجوع", onBack)
                T("العربون والحجز", Modifier.weight(1f).padding(horizontal = 12.dp), 20.sp, FontWeight.Bold)
                IconTile(QIcons.Plus, "حجز جديد", { adding = true })
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("حجوزات مفتوحة" to active.size.toString(), "عرابين مقبوضة" to cur(active.sumOf { it.paid }), "باقٍ للتحصيل" to cur(active.sumOf { it.left }))
                    .forEach { (l, v) ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(16.dp)).padding(12.dp)) {
                            T(l, size = 11.sp, color = p.mute)
                            T(v, Modifier.padding(top = 2.dp), 15.sp, FontWeight.SemiBold)
                        }
                    }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HoldStatus.entries.forEach { st ->
                    val on = filter == st
                    T(st.label, Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) p.pri.copy(alpha = .18f) else p.surface)
                        .clickable(role = Role.RadioButton) { filter = st }.padding(horizontal = 14.dp, vertical = 8.dp), 12.5.sp,
                        if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) p.pri else p.ink)
                }
            }
        }
        if (list.isEmpty()) item { T("لا حجوزات هنا.", Modifier.padding(vertical = 12.dp), 13.sp, color = p.mute) }
        items(list, key = { it.id }) { h ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.surface).clickable(role = Role.Button) { open = h.id }.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(h.title + if (h.variant.isNotBlank()) " (${h.variant})" else "", Modifier.weight(1f), 14.5.sp, FontWeight.SemiBold)
                    T(cur(h.total), size = 14.sp, weight = FontWeight.SemiBold)
                }
                T("${h.name} · ${dayLabel(h.at)}", Modifier.padding(top = 3.dp), 12.sp, color = p.mute)
                Progress(if (h.total > 0) (h.paid / h.total).toFloat() else 1f, Modifier.padding(top = 10.dp))
                Row(Modifier.padding(top = 6.dp)) {
                    T("مدفوع ${cur(h.paid)}", Modifier.weight(1f), 12.sp, color = p.ok)
                    T("الباقي ${cur(h.left)}", size = 12.sp, color = if (h.left > 0) p.warn else p.ok)
                }
            }
        }
    }
    if (adding) HoldForm(products, actions) { adding = false }
    open?.let { id -> holds.firstOrNull { it.id == id }?.let { h -> HoldSheet(h, canCancel, actions) { open = null } } ?: run { open = null } }
}

@Composable
private fun Progress(f: Float, modifier: Modifier) {
    val p = LocalPalette.current
    Box(modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(p.surface2)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceIn(0f, 1f)).clip(CircleShape).background(p.ok))
    }
}

@Composable
private fun HoldSheet(h: Hold, canCancel: Boolean, actions: HoldActions, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var paying by remember { mutableStateOf(false) }
    QSheet(h.title, onDismiss) {
        T("${h.name}${if (h.phone.isNotBlank()) " · ${h.phone}" else ""}${if (h.variant.isNotBlank()) " · ${h.variant}" else ""}", size = 13.sp, color = p.mute, maxLines = 2)
        Progress(if (h.total > 0) (h.paid / h.total).toFloat() else 1f, Modifier.padding(top = 12.dp))
        Row(Modifier.padding(top = 8.dp)) {
            T("مدفوع ${cur(h.paid)} من ${cur(h.total)}", Modifier.weight(1f), 13.5.sp)
            T("الباقي ${cur(h.left)}", size = 13.5.sp, weight = FontWeight.SemiBold, color = if (h.left > 0) p.warn else p.ok)
        }
        h.pays.sortedByDescending { it.second }.forEach { (a, at) ->
            Row(Modifier.padding(top = 6.dp)) {
                T("دفعة ${dayLabel(at)}", Modifier.weight(1f), 12.5.sp, color = p.mute)
                T(cur(a), size = 12.5.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (h.status == HoldStatus.Open) {
            if (h.left > 0) QButton("دفعة إضافية", { paying = true }, secondary = true)
            Spacer(Modifier.height(8.dp))
            QButton(if (h.left > 0) "تسليم مع دفع الباقي ${cur(h.left)}" else "تسليم للزبون", { actions.deliver(h); onDismiss() })
            if (canCancel) T("إلغاء الحجز", Modifier.padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { actions.cancel(h); onDismiss() }.padding(8.dp), 13.sp, FontWeight.Medium, p.danger)
        } else {
            T(h.status.label, size = 14.sp, weight = FontWeight.SemiBold, color = if (h.status == HoldStatus.Done) p.ok else p.danger)
        }
    }
    if (paying) NumpadSheet("دفعة · ${h.title}", "الباقي ${cur(h.left)}", p.ok, "سجّل الدفعة",
        preview = { v -> "الباقي بعدها ${cur((h.left - v).coerceAtLeast(0.0))}" to (v <= h.left) }, onDismiss = { paying = false },
    ) { v, _ -> if (v > 0) actions.pay(h, v.coerceAtMost(h.left)); paying = false }
}

@Composable
private fun HoldForm(products: List<ProductStock>, actions: HoldActions, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var productId by rememberSaveable { mutableStateOf<String?>(null) }
    var variant by rememberSaveable { mutableStateOf("") }
    var total by rememberSaveable { mutableStateOf("") }
    var deposit by rememberSaveable { mutableStateOf("") }
    var q by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    fun d(s: String) = s.toDoubleOrNull() ?: 0.0
    val picked = products.firstOrNull { it.product.id == productId }
    QSheet("حجز جديد", onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            QField(name, { name = it }, "اسم الزبون", error = if (tried && name.isBlank()) "مطلوب" else null)
            QField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, "الجوال (اختياري)", ltr = true, keyboard = KeyboardType.Phone)
            if (products.isNotEmpty()) {
                QField(q, { q = it }, "اختر صنفاً من المخزون (اختياري)", hint = "ابحث بالاسم")
                if (q.isNotBlank() && picked == null) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    products.filter { it.product.name.contains(q.trim()) }.take(8).forEach { s ->
                        T(s.product.name, Modifier.clip(RoundedCornerShape(10.dp)).background(p.surface2).clickable(role = Role.Button) {
                            productId = s.product.id; title = s.product.name; total = dev.saged.qayd.ui.util.money(s.product.price).replace(",", ""); q = ""
                        }.padding(horizontal = 10.dp, vertical = 6.dp), 12.5.sp, color = p.pri)
                    }
                }
                picked?.takeIf { it.product.variants.isNotEmpty() }?.let { s ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.product.variants.forEach { v ->
                            val on = v == variant
                            T(v, Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) p.pri.copy(alpha = .18f) else p.surface2)
                                .clickable(role = Role.RadioButton) { variant = v }.padding(horizontal = 10.dp, vertical = 6.dp), 12.5.sp, color = if (on) p.pri else p.ink)
                        }
                    }
                }
            }
            QField(title, { title = it; if (picked != null && it != picked.product.name) productId = null }, "ما المحجوز", hint = "فستان سهرة، طقم صحون", error = if (tried && title.isBlank()) "مطلوب" else null)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QField(total, { total = it.filter { c -> c.isDigit() || c == '.' } }, "السعر الكامل", Modifier.weight(1f), ltr = true, keyboard = KeyboardType.Decimal,
                    error = if (tried && d(total) <= 0) "مطلوب" else null)
                QField(deposit, { deposit = it.filter { c -> c.isDigit() || c == '.' } }, "العربون الآن", Modifier.weight(1f), ltr = true, keyboard = KeyboardType.Decimal)
            }
            QButton("تسجيل الحجز", {
                tried = true
                if (name.isNotBlank() && title.isNotBlank() && d(total) > 0) {
                    actions.add(name, phone, title, productId, variant, d(total), d(deposit).coerceAtMost(d(total)))
                    onDismiss()
                }
            })
        }
    }
}
