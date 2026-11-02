package dev.saged.qayd.ui.orders

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.saged.qayd.data.MenuItem
import dev.saged.qayd.data.NewOrder
import dev.saged.qayd.data.Order
import dev.saged.qayd.data.OrderLine
import dev.saged.qayd.data.OrderStatus
import dev.saged.qayd.data.OrderType
import dev.saged.qayd.data.today
import dev.saged.qayd.system.BtPrinter
import dev.saged.qayd.ui.components.IconTile
import dev.saged.qayd.ui.components.QButton
import dev.saged.qayd.ui.components.QField
import dev.saged.qayd.ui.components.QIcon
import dev.saged.qayd.ui.components.QNotice
import dev.saged.qayd.ui.components.QSheet
import dev.saged.qayd.ui.components.QSwitchRow
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.theme.LocalPalette
import dev.saged.qayd.ui.util.clock
import dev.saged.qayd.ui.util.cur
import kotlinx.coroutines.launch

/** عمليات الطلبات وقائمة الطعام. */
class OrderActions(
    val add: (NewOrder) -> Order? = { null },
    val status: (Order, OrderStatus) -> Unit = { _, _ -> },
    val cancel: (Order) -> Unit = {},
    val saveItem: (String?, String, Double, String, Boolean) -> Unit = { _, _, _, _, _ -> },
    val deleteItem: (String) -> Unit = {},
    val notify: ((Order) -> Unit)? = null,
) {
    companion object { val None = OrderActions() }
}

private sealed interface View {
    data object List : View
    data object New : View
    data object Menu : View
}

/** طلبات اليوم: طلب جديد من شبكة الأصناف، وحالة كل طلب، والطباعة. */
@Composable
fun OrdersPage(
    padding: PaddingValues,
    shopName: String,
    menu: List<MenuItem>,
    orders: List<Order>,
    canEditMenu: Boolean,
    canCancel: Boolean,
    actions: OrderActions,
    onBack: () -> Unit,
) {
    var view by remember { mutableStateOf<View>(View.List) }
    var printFor by remember { mutableStateOf<Order?>(null) }
    BackHandler(view != View.List) { view = View.List }
    when (view) {
        View.New -> NewOrderView(padding, menu, onBack = { view = View.List }) { o -> actions.add(o)?.let { printFor = it }; view = View.List }
        View.Menu -> MenuView(padding, menu, actions) { view = View.List }
        View.List -> OrdersList(padding, orders.today(), canEditMenu, canCancel, actions, onBack, { view = View.New }, { view = View.Menu }) { printFor = it }
    }
    printFor?.let { o -> PrintSheet(shopName, o) { printFor = null } }
}

@Composable
private fun OrdersList(
    padding: PaddingValues, today: List<Order>, canEditMenu: Boolean, canCancel: Boolean, actions: OrderActions,
    onBack: () -> Unit, onNew: () -> Unit, onMenu: () -> Unit, onPrint: (Order) -> Unit,
) {
    val p = LocalPalette.current
    var filter by rememberSaveable { mutableStateOf<String?>(OrderStatus.New.key) }
    val live = today.filter { it.status != OrderStatus.Cancelled }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(QIcons.Back, "رجوع", onBack)
                T("الطلبات", Modifier.weight(1f).padding(horizontal = 12.dp), 20.sp, FontWeight.Bold)
                if (canEditMenu) IconTile(QIcons.Edit, "قائمة الطعام", onMenu)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("طلبات اليوم" to live.size.toString(), "دخل الطلبات" to cur(live.sumOf { it.total }), "قيد التحضير" to live.count { it.status == OrderStatus.New }.toString())
                    .forEachIndexed { i, (l, v) ->
                        Column(Modifier.weight(if (i == 1) 1.4f else 1f).clip(RoundedCornerShape(16.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(16.dp)).padding(12.dp)) {
                            T(l, size = 11.5.sp, color = p.mute)
                            T(v, Modifier.padding(top = 2.dp), 17.sp, FontWeight.SemiBold)
                        }
                    }
            }
        }
        item { QButton("طلب جديد", onNew) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf<OrderStatus?>(null) + OrderStatus.entries).forEach { st ->
                    val on = filter == st?.key
                    T(st?.label ?: "الكل", Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) p.pri.copy(alpha = .18f) else p.surface)
                        .clickable(role = Role.RadioButton) { filter = st?.key }.padding(horizontal = 14.dp, vertical = 8.dp), 12.5.sp,
                        if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) p.pri else p.ink)
                }
            }
        }
        val list = today.filter { filter == null || it.status.key == filter }
        if (list.isEmpty()) item { T("لا طلبات هنا.", Modifier.padding(vertical = 12.dp), 13.sp, color = p.mute) }
        items(list, key = { it.id }) { o -> OrderCard(o, canCancel, actions, onPrint) }
    }
}

@Composable
private fun OrderCard(o: Order, canCancel: Boolean, actions: OrderActions, onPrint: (Order) -> Unit) {
    val p = LocalPalette.current
    val c = when (o.status) { OrderStatus.New -> p.warn; OrderStatus.Ready -> p.pri; OrderStatus.Done -> p.ok; OrderStatus.Cancelled -> p.danger }
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, c.copy(alpha = .35f), shape).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("#${o.no}", size = 20.sp, weight = FontWeight.Bold)
            T(o.type.label + if (o.table.isNotBlank()) " · طاولة ${o.table}" else "", Modifier.padding(start = 10.dp).weight(1f), 13.sp, color = p.mute)
            T(o.status.label, Modifier.clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = .14f)).padding(horizontal = 10.dp, vertical = 4.dp), 12.sp, FontWeight.SemiBold, c)
        }
        T(o.lines.joinToString("، ") { "${it.qty} ${it.name}" }, Modifier.padding(top = 8.dp), 13.5.sp, maxLines = 3)
        if (o.note.isNotBlank()) T("ملاحظة: ${o.note}", Modifier.padding(top = 4.dp), 12.5.sp, color = p.warn, maxLines = 2)
        if (o.address.isNotBlank()) T("${o.address} · ${o.phone}", Modifier.padding(top = 4.dp), 12.5.sp, color = p.mute, maxLines = 2)
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            T(cur(o.total), Modifier.weight(1f), 16.sp, FontWeight.SemiBold)
            T(clock(o.at), size = 12.sp, color = p.mute)
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (o.status) {
                OrderStatus.New -> QButton("جاهز", { actions.status(o, OrderStatus.Ready) }, Modifier.weight(1f))
                OrderStatus.Ready -> QButton("تم التسليم", { actions.status(o, OrderStatus.Done) }, Modifier.weight(1f))
                else -> Unit
            }
            QButton("طباعة", { onPrint(o) }, Modifier.weight(1f), secondary = true)
        }
        if (o.status != OrderStatus.Cancelled) Row(Modifier.padding(top = 4.dp)) {
            if (o.type == OrderType.Delivery && o.phone.isNotBlank()) actions.notify?.let { n ->
                T("أبلغه أن الطلب في الطريق", Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { n(o) }.padding(8.dp), 12.5.sp, FontWeight.Medium, p.pri)
            }
            Spacer(Modifier.weight(1f))
            if (canCancel && o.status != OrderStatus.Done) {
                T("إلغاء الطلب", Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { actions.cancel(o) }.padding(8.dp), 12.5.sp, FontWeight.Medium, p.danger)
            }
        }
    }
}

@Composable
private fun NewOrderView(padding: PaddingValues, menu: List<MenuItem>, onBack: () -> Unit, onConfirm: (NewOrder) -> Unit) {
    val p = LocalPalette.current
    val cart = remember { mutableStateMapOf<String, Int>() }
    val items = menu.filter { it.active }
    val cats = items.map { it.category.ifBlank { "عام" } }.distinct()
    var cat by rememberSaveable { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf(false) }
    val lines = items.filter { (cart[it.id] ?: 0) > 0 }.map { OrderLine(it.name, it.price, cart[it.id] ?: 0) }
    val total = lines.sumOf { it.total }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding() + 10.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(QIcons.Back, "رجوع", onBack)
                T("طلب جديد", Modifier.weight(1f).padding(horizontal = 12.dp), 20.sp, FontWeight.Bold)
                if (cart.isNotEmpty()) T("تفريغ", Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { cart.clear() }.padding(8.dp), 13.sp, FontWeight.Medium, p.danger)
            }
            if (cats.size > 1) Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf<String?>(null) + cats).forEach { c ->
                    val on = c == cat
                    T(c ?: "الكل", Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) p.pri.copy(alpha = .18f) else p.surface)
                        .clickable(role = Role.RadioButton) { cat = c }.padding(horizontal = 14.dp, vertical = 8.dp), 13.sp,
                        if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) p.pri else p.ink)
                }
            }
            if (items.isEmpty()) T("القائمة فارغة. أضف الأصناف من زر قائمة الطعام في صفحة الطلبات.", Modifier.padding(20.dp), 13.5.sp, color = p.mute, maxLines = 3)
            LazyVerticalGrid(
                GridCells.Fixed(2), Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 190.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items.filter { cat == null || it.category.ifBlank { "عام" } == cat }, key = { it.id }) { m ->
                    val n = cart[m.id] ?: 0
                    val shape = RoundedCornerShape(18.dp)
                    Box(
                        Modifier.clip(shape).background(if (n > 0) p.pri.copy(alpha = .14f) else p.surface).border(1.dp, if (n > 0) p.pri else p.line, shape)
                            .clickable(role = Role.Button) { cart[m.id] = n + 1 }.padding(14.dp).height(64.dp).fillMaxWidth(),
                    ) {
                        Column {
                            T(m.name, size = 14.5.sp, weight = FontWeight.SemiBold, maxLines = 2)
                            T(cur(m.price), Modifier.padding(top = 4.dp), 13.sp, color = p.pri)
                        }
                        if (n > 0) Row(Modifier.align(Alignment.BottomEnd), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(p.surface2).clickable(role = Role.Button) { if (n <= 1) cart.remove(m.id) else cart[m.id] = n - 1 }, contentAlignment = Alignment.Center) {
                                T("−", size = 16.sp, weight = FontWeight.Bold)
                            }
                            T("$n", Modifier.padding(horizontal = 8.dp), 15.sp, FontWeight.Bold, p.pri)
                        }
                    }
                }
            }
        }
        if (lines.isNotEmpty()) Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 92.dp, start = 16.dp, end = 16.dp)
                .clip(RoundedCornerShape(22.dp)).background(p.surface).border(1.dp, p.pri.copy(alpha = .5f), RoundedCornerShape(22.dp)).padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("${lines.sumOf { it.qty }} صنف", Modifier.weight(1f), 13.sp, color = p.mute)
                T(cur(total), size = 20.sp, weight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            QButton("متابعة الطلب", { details = true })
        }
    }
    if (details) OrderDetailsSheet(lines, total, { o -> onConfirm(o); cart.clear(); details = false }) { details = false }
}

@Composable
private fun OrderDetailsSheet(lines: List<OrderLine>, total: Double, onConfirm: (NewOrder) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var type by rememberSaveable { mutableStateOf(OrderType.Takeaway) }
    var table by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    QSheet("تأكيد الطلب", onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            lines.forEach { l ->
                Row {
                    T("${l.qty} × ${l.name}", Modifier.weight(1f), 14.sp)
                    T(cur(l.total), size = 14.sp, weight = FontWeight.Medium)
                }
            }
            Row(Modifier.padding(top = 4.dp)) {
                T("المجموع", Modifier.weight(1f), 15.sp, FontWeight.SemiBold)
                T(cur(total), size = 17.sp, weight = FontWeight.Bold, color = p.pri)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrderType.entries.forEach { t ->
                    val on = t == type
                    T(t.label, Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) p.pri.copy(alpha = .18f) else p.surface2)
                        .border(1.dp, if (on) p.pri else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable(role = Role.RadioButton) { type = t }.padding(vertical = 11.dp), 14.sp, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) p.pri else p.ink)
                }
            }
            when (type) {
                OrderType.Dine -> QField(table, { table = it.take(6) }, "رقم الطاولة", ltr = true, keyboard = KeyboardType.Number)
                OrderType.Delivery -> {
                    QField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, "جوال الزبون", ltr = true, keyboard = KeyboardType.Phone)
                    QField(address, { address = it }, "العنوان", hint = "الحي، الشارع، علامة مميزة")
                }
                OrderType.Takeaway -> Unit
            }
            QField(note, { note = it }, "ملاحظة للمطبخ (اختياري)", hint = "بدون بصل، حار")
            QButton("تأكيد وتسجيل البيع", { onConfirm(NewOrder(lines, type, table, phone, address, note)) })
        }
    }
}

@Composable
private fun MenuView(padding: PaddingValues, menu: List<MenuItem>, actions: OrderActions, onBack: () -> Unit) {
    val p = LocalPalette.current
    var editing by remember { mutableStateOf<MenuItem?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(QIcons.Back, "رجوع", onBack)
                T("قائمة الطعام", Modifier.weight(1f).padding(horizontal = 12.dp), 20.sp, FontWeight.Bold)
                IconTile(QIcons.Plus, "صنف جديد", { editing = MenuItem("", "", 0.0, menu.lastOrNull()?.category.orEmpty(), true) })
            }
        }
        menu.groupBy { it.category.ifBlank { "عام" } }.forEach { (cat, list) ->
            item(key = "c-$cat") { T(cat, Modifier.padding(top = 10.dp, bottom = 2.dp), 13.5.sp, FontWeight.SemiBold, p.pri) }
            items(list, key = { it.id }) { m ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).clickable(role = Role.Button) { editing = m }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(m.name, Modifier.weight(1f), 14.5.sp, color = if (m.active) p.ink else p.mute)
                    if (!m.active) T("متوقف", Modifier.padding(end = 8.dp), 11.5.sp, color = p.mute)
                    T(cur(m.price), size = 14.sp, weight = FontWeight.SemiBold)
                }
            }
        }
        if (menu.isEmpty()) item { T("أضف أصناف قائمتك: شاورما، صحن فلافل، مشروبات…", Modifier.padding(vertical = 14.dp), 13.5.sp, color = p.mute, maxLines = 2) }
    }
    editing?.let { m ->
        val cats = menu.map { it.category }.filter { it.isNotBlank() }.distinct()
        MenuItemSheet(m, cats, { n, pr, c, a -> actions.saveItem(m.id.ifBlank { null }, n, pr, c, a); editing = null },
            if (m.id.isBlank()) null else ({ actions.deleteItem(m.id); editing = null })) { editing = null }
    }
}

@Composable
private fun MenuItemSheet(m: MenuItem, cats: List<String>, onSave: (String, Double, String, Boolean) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var name by rememberSaveable { mutableStateOf(m.name) }
    var price by rememberSaveable { mutableStateOf(if (m.price > 0) dev.saged.qayd.ui.util.money(m.price) else "") }
    var cat by rememberSaveable { mutableStateOf(m.category) }
    var active by rememberSaveable { mutableStateOf(m.active) }
    QSheet(if (m.id.isBlank()) "صنف جديد" else "تعديل الصنف", onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            QField(name, { name = it }, "الاسم", hint = "سندويش شاورما")
            QField(price, { price = it.filter { c -> c.isDigit() || c == '.' }.take(7) }, "السعر", ltr = true, keyboard = KeyboardType.Decimal)
            QField(cat, { cat = it }, "القسم", hint = "سندويشات، صحون، مشروبات")
            if (cats.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cats.forEach { c -> T(c, Modifier.clip(RoundedCornerShape(10.dp)).background(p.surface2).clickable(role = Role.Button) { cat = c }.padding(horizontal = 10.dp, vertical = 6.dp), 12.5.sp, color = p.pri) }
            }
            QSwitchRow("متوفر للطلب", active, { active = it })
            QButton("حفظ", { price.toDoubleOrNull()?.takeIf { it > 0 && name.isNotBlank() }?.let { onSave(name, it, cat, active) } })
            if (onDelete != null) QButton("حذف الصنف", onDelete, secondary = true)
        }
    }
}

/** طباعة إيصال الزبون وتذكرة المطبخ على الطابعة المحفوظة. */
@Composable
fun PrintSheet(shopName: String, o: Order, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var device by remember { mutableStateOf(BtPrinter.saved(ctx)) }
    var width by remember { mutableStateOf(BtPrinter.width(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var picking by remember { mutableStateOf(device == null) }
    fun granted() = Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    var ok by remember { mutableStateOf(granted()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok = it }
    fun go(kitchen: Boolean, customer: Boolean) {
        val d = device ?: return
        busy = true
        msg = null
        val pages = buildList {
            if (customer) add(BtPrinter.ticket(ctx, shopName, o, false, width))
            if (kitchen) add(BtPrinter.ticket(ctx, shopName, o, true, width))
        }
        scope.launch {
            msg = BtPrinter.print(ctx, d, pages).fold({ "تمت الطباعة" to false }, { "تعذر الاتصال بالطابعة. تأكد أنها مشغّلة وقريبة." to true })
            busy = false
        }
    }
    QSheet("طباعة الطلب #${o.no}", onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!ok) {
                T("اسمح للتطبيق بالاتصال بأجهزة البلوتوث ليصل إلى الطابعة.", size = 13.5.sp, color = p.mute, maxLines = 2)
                QButton("السماح", { if (Build.VERSION.SDK_INT >= 31) ask.launch(Manifest.permission.BLUETOOTH_CONNECT) })
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                QIcon(QIcons.Printer, p.pri, Modifier.size(22.dp))
                T(device?.name ?: "لم تُختر طابعة", Modifier.weight(1f).padding(horizontal = 10.dp), 14.5.sp, FontWeight.Medium)
                T("تغيير", Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { picking = !picking }.padding(8.dp), 13.sp, FontWeight.Medium, p.pri)
            }
            if (picking) {
                val list = BtPrinter.paired(ctx)
                if (list.isEmpty()) T("لا أجهزة مقترنة. اقرن الطابعة من إعدادات البلوتوث في الجوال ثم ارجع هنا.", size = 12.5.sp, color = p.mute, maxLines = 3)
                list.forEach { d ->
                    T(d.name, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (d.address == device?.address) p.pri.copy(alpha = .16f) else p.surface2)
                        .clickable(role = Role.RadioButton) { BtPrinter.save(ctx, d); device = d; picking = false }.padding(12.dp), 14.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(384 to "ورق 58 ملم", 576 to "ورق 80 ملم").forEach { (px, l) ->
                        val on = px == width
                        T(l, Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) p.pri.copy(alpha = .18f) else p.surface2)
                            .clickable(role = Role.RadioButton) { width = px; BtPrinter.setWidth(ctx, px) }.padding(vertical = 10.dp), 13.sp, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) p.pri else p.ink)
                    }
                }
            }
            msg?.let { (t, err) -> QNotice(t, error = err) }
            if (device != null) {
                QButton("إيصال الزبون + تذكرة المطبخ", { go(kitchen = true, customer = true) }, busy = busy, busyText = "جارٍ الطباعة…")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QButton("إيصال فقط", { go(kitchen = false, customer = true) }, Modifier.weight(1f), secondary = true)
                    QButton("المطبخ فقط", { go(kitchen = true, customer = false) }, Modifier.weight(1f), secondary = true)
                }
            }
        }
    }
}
