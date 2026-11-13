package dev.saged.qayd.ui.batteries

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.saged.qayd.data.BattSale
import dev.saged.qayd.data.BatteryState
import dev.saged.qayd.data.NewBattery
import dev.saged.qayd.data.ProductStock
import dev.saged.qayd.data.QaydRepo
import dev.saged.qayd.data.WarrantyState
import dev.saged.qayd.ui.components.IconTile
import dev.saged.qayd.ui.components.QButton
import dev.saged.qayd.ui.components.QField
import dev.saged.qayd.ui.components.QSheet
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.messages.openWhatsApp
import dev.saged.qayd.ui.theme.LocalPalette
import dev.saged.qayd.ui.util.cur
import dev.saged.qayd.ui.util.dayLabel
import dev.saged.qayd.ui.util.ltr
import dev.saged.qayd.ui.util.money

private enum class Tab(val label: String) { Sales("المبيعات"), Warranty("الكفالة"), Scrap("الخردة") }

/** بطاريات السيارات: سجل البيع بالرقم التسلسلي، الكفالة، الاستبدال، وخردة البطاريات القديمة. */
@Composable
fun BatteriesPage(padding: PaddingValues, shopId: String, stock: List<ProductStock>, canDelete: Boolean, onBack: () -> Unit) {
    val p = LocalPalette.current
    val repo = QaydRepo.get(LocalContext.current)
    val state by remember(shopId) { repo.batteries(shopId) }.collectAsStateWithLifecycle(BatteryState.Empty)
    var tab by rememberSaveable { mutableStateOf(Tab.Sales) }
    var q by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var scrapping by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    val key = q.trim()
    val sales = state.sales.filter { key.isBlank() || listOf(it.serial, it.name, it.phone, it.car, it.model).any { f -> f.contains(key, true) } }
    val list = when (tab) {
        Tab.Sales -> sales.sortedByDescending { it.at }
        Tab.Warranty -> sales.filter { it.state() != WarrantyState.Active && it.daysLeft() >= -60 }.sortedBy { it.endAt }
        Tab.Scrap -> emptyList()
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(QIcons.Back, "رجوع", onBack)
                T("بطاريات السيارات", Modifier.weight(1f).padding(horizontal = 12.dp), 20.sp, FontWeight.Bold)
                IconTile(QIcons.Plus, "بيع بطارية", { adding = true })
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("مبيعات مسجلة" to state.sales.size.toString(), "كفالات تنتهي قريباً" to state.soon.toString(), "خردة بالمحل" to "${money(state.scrapKg)} كغ")
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
                Tab.entries.forEach { t ->
                    val on = tab == t
                    T(t.label, Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) p.pri.copy(alpha = .18f) else p.surface)
                        .clickable(role = Role.RadioButton) { tab = t }.padding(horizontal = 14.dp, vertical = 8.dp), 12.5.sp,
                        if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) p.pri else p.ink)
                }
            }
        }
        if (tab != Tab.Scrap) item { QField(q, { q = it }, "بحث", hint = "الرقم التسلسلي، الزبون، الجوال، السيارة") }
        if (tab == Tab.Scrap) item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T("خردة البطاريات القديمة", size = 13.sp, color = p.mute)
                T("${money(state.scrapKg)} كغ", size = 26.sp, weight = FontWeight.Bold)
                T("تتجمع من البطاريات المستلمة مع الاستبدال، وتنقص عند بيعها.", size = 12.sp, color = p.mute, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                QButton("بيع خردة", { scrapping = true })
            }
        }
        if (tab == Tab.Scrap) {
            items(state.scrap.sortedByDescending { it.at }, key = { it.id }) { s ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).padding(14.dp)) {
                    T("بيع ${money(s.kg)} كغ · ${dayLabel(s.at)}", Modifier.weight(1f), 13.5.sp)
                    T(cur(s.amount), size = 13.5.sp, weight = FontWeight.SemiBold)
                }
            }
        } else {
            if (list.isEmpty()) item { T(if (tab == Tab.Warranty) "لا كفالات تنتهي قريباً." else "لا مبيعات هنا.", Modifier.padding(vertical = 12.dp), 13.sp, color = p.mute) }
            items(list, key = { it.id }) { s -> SaleCard(s) { open = s.id } }
        }
    }
    if (adding) SaleForm(stock, { repo.sellBattery(shopId, it) }) { adding = false }
    if (scrapping) ScrapForm(state.scrapKg, { kg, amt -> repo.sellScrap(shopId, kg, amt) }) { scrapping = false }
    open?.let { id ->
        state.sales.firstOrNull { it.id == id }?.let { s ->
            SaleSheet(s, canDelete, { repo.deleteBattery(shopId, s, stock.firstOrNull { it.product.id == s.productId }?.product?.cost ?: 0.0) }) { open = null }
        } ?: run { open = null }
    }
}

@Composable
private fun WarrantyChip(s: BattSale) {
    val p = LocalPalette.current
    val st = s.state()
    val c = when (st) { WarrantyState.Active -> p.ok; WarrantyState.Soon -> p.warn; WarrantyState.Expired -> p.danger }
    val d = s.daysLeft()
    T(if (st == WarrantyState.Expired) "انتهت منذ ${-d} يوماً" else "كفالة $d يوماً", Modifier.clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = .16f)).padding(horizontal = 10.dp, vertical = 4.dp), 11.5.sp, FontWeight.SemiBold, c)
}

@Composable
private fun SaleCard(s: BattSale, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.surface).clickable(role = Role.Button, onClick = onClick).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(s.model, Modifier.weight(1f), 14.5.sp, FontWeight.SemiBold)
            T(cur(s.net), size = 14.sp, weight = FontWeight.SemiBold)
        }
        T(ltr(s.serial), Modifier.padding(top = 2.dp), 12.5.sp, color = p.mute)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            T(listOf(s.name, s.car).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { dayLabel(s.at) }, Modifier.weight(1f), 12.sp, color = p.mute)
            WarrantyChip(s)
        }
    }
}

@Composable
private fun SaleSheet(s: BattSale, canDelete: Boolean, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    QSheet(s.model, onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T(ltr(s.serial), size = 14.sp, weight = FontWeight.SemiBold)
            listOf(
                "الزبون" to s.name.ifBlank { "غير مسجل" }, "الجوال" to s.phone.ifBlank { "غير مسجل" }, "السيارة" to s.car.ifBlank { "غير مسجلة" },
                "تاريخ البيع" to dayLabel(s.at), "الكفالة" to "${s.months} شهراً · تنتهي ${dayLabel(s.endAt)}",
                "السعر" to cur(s.price), "قيمة القديمة" to if (s.credit > 0) "${cur(s.credit)} · ${money(s.oldKg)} كغ" else "لا استبدال", "المدفوع" to cur(s.net),
            ).forEach { (l, v) -> Row { T(l, Modifier.weight(1f), 12.5.sp, color = p.mute); T(v, size = 13.sp) } }
            WarrantyChip(s)
            Spacer(Modifier.height(6.dp))
            if (s.phone.isNotBlank()) QButton("تذكير بالكفالة عبر واتساب", {
                openWhatsApp(ctx, s.phone, "+970", "مرحباً ${s.name.ifBlank { "" }}، كفالة بطاريتك ${s.model} رقم ${s.serial} تنتهي ${dayLabel(s.endAt)}. نسعد بخدمتك.")
            })
            if (canDelete) T("حذف هذا البيع", Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { onDelete(); onDismiss() }.padding(8.dp), 13.sp, FontWeight.Medium, p.danger)
        }
    }
}

@Composable
private fun SaleForm(stock: List<ProductStock>, onSave: (NewBattery) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var serial by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var productId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var car by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var credit by rememberSaveable { mutableStateOf("") }
    var kg by rememberSaveable { mutableStateOf("") }
    var months by rememberSaveable { mutableStateOf(12) }
    var tried by rememberSaveable { mutableStateOf(false) }
    fun d(s: String) = s.toDoubleOrNull() ?: 0.0
    fun num(s: String) = s.filter { c -> c.isDigit() || c == '.' }
    val picked = stock.firstOrNull { it.product.id == productId }
    QSheet("بيع بطارية", onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            QField(serial, { serial = it.trim().uppercase() }, "الرقم التسلسلي", ltr = true, error = if (tried && serial.isBlank()) "مطلوب" else null)
            QField(model, { model = it; if (picked != null && it != picked.product.name) productId = null }, "الموديل", hint = "اسم البطارية وسعتها", error = if (tried && model.isBlank()) "مطلوب" else null)
            if (stock.isNotEmpty() && model.isNotBlank() && picked == null) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                stock.filter { it.product.name.contains(model.trim(), true) }.take(8).forEach { s ->
                    T(s.product.name, Modifier.clip(RoundedCornerShape(10.dp)).background(p.surface2).clickable(role = Role.Button) {
                        productId = s.product.id; model = s.product.name; price = money(s.product.price).replace(",", "")
                    }.padding(horizontal = 10.dp, vertical = 6.dp), 12.5.sp, color = p.pri)
                }
            }
            QField(name, { name = it }, "اسم الزبون (اختياري)")
            QField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, "الجوال (اختياري)", ltr = true, keyboard = KeyboardType.Phone)
            QField(car, { car = it }, "السيارة (اختياري)", hint = "النوع والسنة")
            QField(price, { price = num(it) }, "السعر", ltr = true, keyboard = KeyboardType.Decimal, error = if (tried && d(price) <= 0) "مطلوب" else null)
            T("مدة الكفالة", size = 13.sp, weight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(6, 12, 18, 24, 36).forEach { m ->
                    val on = m == months
                    T("$m شهراً", Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) p.pri.copy(alpha = .18f) else p.surface2)
                        .clickable(role = Role.RadioButton) { months = m }.padding(horizontal = 12.dp, vertical = 8.dp), 12.5.sp, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) p.pri else p.ink)
                }
            }
            T("استبدال بطارية قديمة", size = 13.sp, weight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QField(credit, { credit = num(it) }, "قيمة القديمة", Modifier.weight(1f), ltr = true, keyboard = KeyboardType.Decimal)
                QField(kg, { kg = num(it) }, "وزنها", Modifier.weight(1f), ltr = true, keyboard = KeyboardType.Decimal, suffix = "كغ")
            }
            val net = (d(price) - d(credit)).coerceAtLeast(0.0)
            if (d(price) > 0) T("المطلوب من الزبون ${cur(net)}", size = 13.5.sp, weight = FontWeight.SemiBold, color = p.ok)
            QButton("تسجيل البيع", {
                tried = true
                if (serial.isNotBlank() && model.isNotBlank() && d(price) > 0) {
                    onSave(NewBattery(serial, model, name, phone, car, d(price), d(credit).coerceAtMost(d(price)), d(kg), months, productId, picked?.product?.cost ?: 0.0))
                    onDismiss()
                }
            })
        }
    }
}

@Composable
private fun ScrapForm(available: Double, onSave: (Double, Double) -> Unit, onDismiss: () -> Unit) {
    var kg by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    fun d(s: String) = s.toDoubleOrNull() ?: 0.0
    QSheet("بيع خردة", onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            T("المتوفر ${money(available)} كغ", size = 13.sp, color = LocalPalette.current.mute)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QField(kg, { kg = it.filter { c -> c.isDigit() || c == '.' } }, "الوزن", Modifier.weight(1f), ltr = true, keyboard = KeyboardType.Decimal, suffix = "كغ",
                    error = if (tried && (d(kg) <= 0 || d(kg) > available)) "غير صحيح" else null)
                QField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, "المبلغ", Modifier.weight(1f), ltr = true, keyboard = KeyboardType.Decimal,
                    error = if (tried && d(amount) <= 0) "مطلوب" else null)
            }
            QButton("تسجيل البيع", {
                tried = true
                if (d(kg) > 0 && d(kg) <= available && d(amount) > 0) { onSave(d(kg), d(amount)); onDismiss() }
            })
        }
    }
}
