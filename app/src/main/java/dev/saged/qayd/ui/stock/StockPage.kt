package dev.saged.qayd.ui.stock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.saged.qayd.data.NewProduct
import dev.saged.qayd.data.Product
import dev.saged.qayd.data.ProductStock
import dev.saged.qayd.data.StockState
import dev.saged.qayd.data.Supplier
import dev.saged.qayd.ui.components.ActionRow
import dev.saged.qayd.ui.components.IconTile
import dev.saged.qayd.ui.components.NumpadSheet
import dev.saged.qayd.ui.components.PadChip
import dev.saged.qayd.ui.components.QButton
import dev.saged.qayd.ui.components.QField
import dev.saged.qayd.ui.components.QIcon
import dev.saged.qayd.ui.components.QSheet
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.theme.LocalPalette
import dev.saged.qayd.ui.util.cur
import dev.saged.qayd.ui.util.dayLabel
import dev.saged.qayd.ui.util.ltr
import dev.saged.qayd.ui.util.money
import dev.saged.qayd.ui.util.norm

private val UNITS = listOf("قطعة", "كغ", "علبة", "كرتونة", "متر", "لتر")

private sealed interface Op {
    val s: ProductStock
    data class Sell(override val s: ProductStock, val v: String = "") : Op
    data class Receive(override val s: ProductStock, val v: String = "") : Op
    data class Count(override val s: ProductStock, val v: String = "") : Op
    data class Return(override val s: ProductStock, val v: String = "") : Op
}

/** يطلب اختيار المقاس/اللون أولاً إن كان للصنف تنويعات. */
private fun Op.with(v: String): Op = when (this) {
    is Op.Sell -> copy(v = v); is Op.Receive -> copy(v = v); is Op.Count -> copy(v = v); is Op.Return -> copy(v = v)
}

/** رمز الباركود بكاميرا الجوال. */
@Composable
fun rememberScanner(onCode: (String) -> Unit): () -> Unit {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(ScanContract()) { r -> r.contents?.let(onCode) }
    return {
        launcher.launch(ScanOptions().setPrompt("وجّه الكاميرا إلى الباركود").setBeepEnabled(true).setOrientationLocked(false))
    }
}

/** الأصناف والمخزون: بحث ومسح باركود، ورصيد كل صنف محسوب من حركاته. */
@Composable
fun StockPage(
    padding: PaddingValues,
    stock: StockState,
    suppliers: List<Supplier>,
    canManage: Boolean,
    canSell: Boolean,
    actions: StockActions,
    onBack: () -> Unit,
) {
    val p = LocalPalette.current
    var q by rememberSaveable { mutableStateOf("") }
    var lowOnly by rememberSaveable { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Product?>(null) }
    var adding by remember { mutableStateOf<String?>(null) }
    var op by remember { mutableStateOf<Op?>(null) }
    var pick by remember { mutableStateOf<Op?>(null) }
    fun start(o: Op) { if (o.s.product.variants.isEmpty()) op = o else pick = o }
    val scan = rememberScanner { code -> stock.byBarcode(code)?.let { open = it.product.id } ?: run { if (canManage) adding = code } }
    val list = stock.items.filter { (!lowOnly || it.low) && (q.isBlank() || norm(it.product.name).contains(norm(q)) || it.product.barcode.contains(q)) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(QIcons.Back, "رجوع", onBack)
                T("الأصناف والمخزون", Modifier.weight(1f).padding(horizontal = 12.dp), 20.sp, FontWeight.Bold)
                IconTile(QIcons.Scan, "مسح باركود", scan)
                if (canManage) IconTile(QIcons.Plus, "صنف جديد", { adding = "" }, Modifier.padding(start = 8.dp))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("الأصناف", stock.items.size.toString(), Modifier.weight(1f))
                Stat("قيمة المخزون", cur(stock.value), Modifier.weight(1.4f))
                Stat("قارب على النفاد", stock.lowCount.toString(), Modifier.weight(1.2f).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button) { lowOnly = !lowOnly }, if (stock.lowCount > 0) p.warn else null)
            }
        }
        item {
            QField(q, { q = it }, "بحث بالاسم أو الباركود", hint = "اسم الصنف")
            if (lowOnly) T("يعرض الأصناف التي قاربت على النفاد فقط · اضغط البطاقة للإلغاء", Modifier.padding(top = 6.dp), 12.sp, color = p.warn, maxLines = 2)
        }
        if (list.isEmpty()) item {
            T(if (stock.items.isEmpty()) "لا أصناف بعد. أضف أول صنف من زر +، أو امسح باركوده." else "لا نتائج.", Modifier.padding(vertical = 18.dp), 13.5.sp, color = p.mute, maxLines = 2)
        }
        items(list, key = { it.product.id }) { s -> ProductRow(s) { open = s.product.id } }
    }

    open?.let { id ->
        val s = stock.of(id)
        if (s == null) open = null else ProductSheet(
            s, stock, canManage, canSell,
            onSell = { start(Op.Sell(s)); open = null }, onReceive = { start(Op.Receive(s)); open = null }, onCount = { start(Op.Count(s)); open = null },
            onReturn = { start(Op.Return(s)); open = null },
            onEdit = { editing = s.product; open = null }, onDelete = { actions.deleteProduct(s.product); open = null },
        ) { open = null }
    }
    adding?.let { code -> ProductForm(null, code, { actions.addProduct(it); adding = null }, { _ -> }) { adding = null } }
    editing?.let { pr -> ProductForm(pr, pr.barcode, { }, { actions.editProduct(it); editing = null }) { editing = null } }

    pick?.let { o -> VariantSheet(o.s, { v -> op = o.with(v); pick = null }) { pick = null } }

    when (val o = op) {
        is Op.Sell -> {
            val have = o.s.qtyOf(o.v)
            NumpadSheet(
                "بيع ${o.s.product.name}${tag(o.v)}", "المتوفر ${money(have)} ${o.s.product.unit} · السعر ${cur(o.s.product.price)}", p.ok, "سجّل البيع",
                label = "الكمية", unit = o.s.product.unit,
                preview = { v -> "الإجمالي ${cur(v * o.s.product.price)}" to (v <= have) },
                onDismiss = { op = null },
            ) { v, _ -> if (v > 0) actions.sell(o.s.product, v, o.v); op = null }
        }
        is Op.Receive -> NumpadSheet(
            "إدخال بضاعة: ${o.s.product.name}${tag(o.v)}", "سعر الشراء ${cur(o.s.product.cost)} للـ${o.s.product.unit}", p.pri, "إدخال",
            chips = suppliers.map { PadChip(it.id, it.name) }, label = "الكمية", unit = o.s.product.unit,
            preview = { v -> "التكلفة ${cur(v * o.s.product.cost)}${if (suppliers.isNotEmpty()) " · اختر المورد لتُسجَّل عليه" else ""}" to true },
            onDismiss = { op = null },
        ) { v, sup -> if (v > 0) actions.receive(o.s.product, v, o.s.product.cost, sup, o.v); op = null }
        is Op.Count -> {
            val have = o.s.qtyOf(o.v)
            NumpadSheet(
                "جرد ${o.s.product.name}${tag(o.v)}", "المسجّل حالياً ${money(have)} ${o.s.product.unit}", p.warn, "اعتماد الجرد",
                initial = "", label = "العدد الفعلي", unit = o.s.product.unit,
                preview = { v -> "الفرق ${ltr(money(v - have))} ${o.s.product.unit}" to true },
                onDismiss = { op = null },
            ) { v, _ -> actions.adjust(o.s.product, have, v, o.v); op = null }
        }
        is Op.Return -> NumpadSheet(
            "مرتجع ${o.s.product.name}${tag(o.v)}", "يعود للمخزون ويُخصم ثمنه من مبيعات اليوم", p.danger, "اعتماد المرتجع",
            label = "الكمية", unit = o.s.product.unit,
            preview = { v -> "يُرَدّ للزبون ${cur(v * o.s.product.price)}" to true },
            onDismiss = { op = null },
        ) { v, _ -> if (v > 0) actions.returnItem(o.s.product, v, o.v); op = null }
        null -> Unit
    }
}

private fun tag(v: String) = if (v.isBlank()) "" else " ($v)"

@Composable
private fun VariantSheet(s: ProductStock, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    QSheet("اختر المقاس واللون", onDismiss) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.product.variants.forEach { v ->
                val n = s.qtyOf(v)
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(p.surface2).border(1.dp, if (n > 0) p.line else p.danger.copy(alpha = .3f), RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button) { onPick(v) }.padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    T(v, size = 14.sp, weight = FontWeight.SemiBold)
                    T("${money(n)} ${s.product.unit}", Modifier.padding(top = 2.dp), 12.sp, color = if (n > 0) p.mute else p.danger)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color? = null) {
    val p = LocalPalette.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(p.surface).border(1.dp, color?.copy(alpha = .4f) ?: p.line, RoundedCornerShape(16.dp)).padding(12.dp)) {
        T(label, size = 11.5.sp, color = p.mute)
        T(value, Modifier.padding(top = 2.dp), 17.sp, FontWeight.SemiBold, color ?: p.ink)
    }
}

@Composable
private fun ProductRow(s: ProductStock, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface).clickable(role = Role.Button, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(p.surface2), contentAlignment = Alignment.Center) {
            QIcon(QIcons.Box, if (s.low) p.warn else p.pri, Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            T(s.product.name, size = 14.5.sp, weight = FontWeight.Medium)
            T("${cur(s.product.price)} · ربح ${cur(s.product.price - s.product.cost)}${if (s.product.variants.isNotEmpty()) " · ${s.product.variants.size} تنويعة" else ""}", Modifier.padding(top = 3.dp), 12.sp, color = p.mute)
        }
        Column(horizontalAlignment = Alignment.End) {
            T("${money(s.qty)} ${s.product.unit}", size = 14.sp, weight = FontWeight.SemiBold, color = if (s.low) p.warn else p.ink)
            if (s.low) T("قارب على النفاد", Modifier.padding(top = 2.dp), 10.5.sp, color = p.warn)
        }
    }
}

@Composable
private fun ProductSheet(
    s: ProductStock, stock: StockState, canManage: Boolean, canSell: Boolean,
    onSell: () -> Unit, onReceive: () -> Unit, onCount: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
    onReturn: () -> Unit, onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    var confirmDelete by remember { mutableStateOf(false) }
    QSheet(s.product.name, onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("المتوفر", "${money(s.qty)} ${s.product.unit}", Modifier.weight(1f), if (s.low) p.warn else null)
            Stat("سعر البيع", cur(s.product.price), Modifier.weight(1f))
            Stat("سعر الشراء", cur(s.product.cost), Modifier.weight(1f))
        }
        if (s.product.barcode.isNotBlank()) T("الباركود ${ltr(s.product.barcode)}", Modifier.padding(top = 10.dp), 12.sp, color = p.mute)
        if (s.product.variants.isNotEmpty()) {
            T("حسب المقاس واللون", Modifier.padding(top = 10.dp, bottom = 6.dp), 13.sp, FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.product.variants.forEach { v ->
                    val n = s.qtyOf(v)
                    T("$v: ${money(n)}", Modifier.clip(RoundedCornerShape(10.dp)).background(if (n > 0) p.surface2 else p.danger.copy(alpha = .12f)).padding(horizontal = 10.dp, vertical = 5.dp),
                        12.sp, color = if (n > 0) p.ink else p.danger)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (canSell) ActionRow(QIcons.Cart, "بيع", "يُسجَّل في مبيعات اليوم ويُخصم من المخزون", onClick = onSell)
        if (canManage) {
            ActionRow(QIcons.Down, "إدخال بضاعة", "زيادة الكمية، مع فاتورة شراء على المورد إن اخترته", onClick = onReceive)
            ActionRow(QIcons.Receipt, "جرد", "اكتب العدد الفعلي على الرف", onClick = onCount)
            ActionRow(QIcons.Up, "مرتجع", "زبون أرجع الصنف: يعود للمخزون ويُرَدّ ثمنه", onClick = onReturn)
            ActionRow(QIcons.Edit, "تعديل الصنف", "الاسم، الأسعار، الباركود، حد التنبيه", onClick = onEdit)
            ActionRow(QIcons.Trash, "حذف الصنف", danger = true) { confirmDelete = true }
        }
        val h = stock.history(s.product.id).take(12)
        if (h.isNotEmpty()) {
            T("آخر الحركات", Modifier.padding(top = 10.dp, bottom = 4.dp), 13.sp, FontWeight.SemiBold)
            h.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("${m.kind.label} · ${m.byName}", Modifier.weight(1f), 12.5.sp)
                    T(dayLabel(m.at), Modifier.padding(horizontal = 8.dp), 11.5.sp, color = p.mute)
                    T(ltr((if (m.qty > 0) "+" else "") + money(m.qty)), size = 13.sp, weight = FontWeight.SemiBold, color = if (m.qty >= 0) p.ok else p.danger)
                }
            }
        }
    }
    if (confirmDelete) QSheet("حذف ${s.product.name}؟", { confirmDelete = false }) {
        T("يختفي الصنف من القائمة، وتبقى حركاته في المبيعات.", size = 13.5.sp, color = p.mute, maxLines = 2)
        Spacer(Modifier.height(14.dp))
        QButton("حذف", { onDelete(); confirmDelete = false })
    }
}

@Composable
private fun ProductForm(edit: Product?, code: String, onAdd: (NewProduct) -> Unit, onSave: (Product) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var name by rememberSaveable { mutableStateOf(edit?.name.orEmpty()) }
    var barcode by rememberSaveable { mutableStateOf(code) }
    var unit by rememberSaveable { mutableStateOf(edit?.unit ?: UNITS[0]) }
    var cost by rememberSaveable { mutableStateOf(edit?.cost?.let(::money).orEmpty()) }
    var price by rememberSaveable { mutableStateOf(edit?.price?.let(::money).orEmpty()) }
    var min by rememberSaveable { mutableStateOf(edit?.minStock?.takeIf { it > 0 }?.let(::money).orEmpty()) }
    var qty by rememberSaveable { mutableStateOf("") }
    var sizes by rememberSaveable { mutableStateOf(edit?.sizes.orEmpty().joinToString("، ")) }
    var colors by rememberSaveable { mutableStateOf(edit?.colors.orEmpty().joinToString("، ")) }
    var tried by rememberSaveable { mutableStateOf(false) }
    fun list(s: String) = s.split(',', '،').map { it.trim() }.filter { it.isNotBlank() }.distinct()
    val scan = rememberScanner { barcode = it }
    fun d(s: String) = s.replace(',', '.').toDoubleOrNull() ?: 0.0
    fun num(s: String) = s.filter { it.isDigit() || it == '.' || it == ',' }.take(9)
    QSheet(if (edit == null) "صنف جديد" else "تعديل الصنف", onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            QField(name, { name = it }, "اسم الصنف", hint = "قميص قطن، صحن زجاج…", error = if (tried && name.isBlank()) "اكتب اسم الصنف" else null)
            Row(verticalAlignment = Alignment.Bottom) {
                QField(barcode, { barcode = it.trim() }, "الباركود (اختياري)", Modifier.weight(1f), ltr = true, keyboard = KeyboardType.Number)
                IconTile(QIcons.Scan, "مسح", scan, Modifier.padding(start = 8.dp, bottom = 4.dp))
            }
            T("الوحدة", size = 12.5.sp, color = p.mute)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UNITS.forEach { u ->
                    val on = u == unit
                    T(u, Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) p.pri.copy(alpha = .18f) else p.surface2)
                        .clickable(role = Role.RadioButton) { unit = u }.padding(horizontal = 14.dp, vertical = 9.dp), 13.sp, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) p.pri else p.ink)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QField(cost, { cost = num(it) }, "سعر الشراء", Modifier.weight(1f), ltr = true, keyboard = KeyboardType.Decimal)
                QField(price, { price = num(it) }, "سعر البيع", Modifier.weight(1f), ltr = true, keyboard = KeyboardType.Decimal, error = if (tried && d(price) <= 0) "مطلوب" else null)
            }
            QField(sizes, { sizes = it }, "المقاسات (اختياري)", hint = "S، M، L، XL أو 38، 40، 42")
            QField(colors, { colors = it }, "الألوان (اختياري)", hint = "أسود، أبيض، كحلي")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QField(min, { min = num(it) }, "نبّهني عند", Modifier.weight(1f), ltr = true, keyboard = KeyboardType.Decimal, suffix = unit)
                if (edit == null && list(sizes).isEmpty() && list(colors).isEmpty()) QField(qty, { qty = num(it) }, "الكمية الحالية", Modifier.weight(1f), ltr = true, keyboard = KeyboardType.Decimal, suffix = unit)
            }
            if (list(sizes).isNotEmpty() || list(colors).isNotEmpty()) T("أدخل كمية كل مقاس ولون من \"إدخال بضاعة\" بعد الحفظ.", size = 12.sp, color = p.mute, maxLines = 2)
            QButton(if (edit == null) "إضافة الصنف" else "حفظ", {
                tried = true
                if (name.isNotBlank() && d(price) > 0) {
                    if (edit == null) onAdd(NewProduct(name, barcode, unit, d(cost), d(price), d(min), d(qty), list(sizes), list(colors)))
                    else onSave(edit.copy(name = name, barcode = barcode, unit = unit, cost = d(cost), price = d(price), minStock = d(min), sizes = list(sizes), colors = list(colors)))
                }
            })
        }
    }
}
