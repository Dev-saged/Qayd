package dev.saged.qayd.ui.shell

import androidx.activity.compose.BackHandler
import dev.saged.qayd.data.Access
import dev.saged.qayd.data.AmountInput
import dev.saged.qayd.data.AuditEntry
import dev.saged.qayd.data.Member
import dev.saged.qayd.data.Module
import dev.saged.qayd.data.Perm
import dev.saged.qayd.data.PowerState
import dev.saged.qayd.data.Prices
import dev.saged.qayd.data.Gateway
import dev.saged.qayd.data.SmsMsg
import dev.saged.qayd.data.Template
import dev.saged.qayd.data.Templates
import dev.saged.qayd.gateway.GatewayService
import dev.saged.qayd.pdf.QaydPdf
import dev.saged.qayd.data.report
import dev.saged.qayd.ui.reports.ReportsPage
import dev.saged.qayd.ui.messages.MessagesScreen
import dev.saged.qayd.ui.messages.SendSheet
import dev.saged.qayd.ui.messages.TemplatesPage
import dev.saged.qayd.ui.messages.smsVars
import androidx.compose.ui.platform.LocalContext
import dev.saged.qayd.ui.power.PowerPage
import dev.saged.qayd.ui.ps.PsSessionSheet
import dev.saged.qayd.ui.ps.PsStartSheet
import dev.saged.qayd.ui.util.cur
import dev.saged.qayd.ui.team.AuditPage
import dev.saged.qayd.ui.team.TeamPage
import dev.saged.qayd.data.Customer
import dev.saged.qayd.data.ExpenseCategory
import dev.saged.qayd.data.Ledger
import dev.saged.qayd.data.Tx
import dev.saged.qayd.data.TxKind
import dev.saged.qayd.ui.components.LockedCard
import dev.saged.qayd.ui.components.NumpadSheet
import dev.saged.qayd.ui.components.PadChip
import dev.saged.qayd.ui.components.QSheet
import dev.saged.qayd.ui.customers.CustomerPage
import dev.saged.qayd.ui.customers.CustomersScreen
import dev.saged.qayd.ui.expenses.ExpensesPage
import dev.saged.qayd.ui.util.balanceText
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.data.Account
import dev.saged.qayd.data.Shop
import dev.saged.qayd.data.SyncState
import dev.saged.qayd.ui.components.Ambient
import dev.saged.qayd.ui.home.PsSlot
import dev.saged.qayd.ui.components.QIcon
import dev.saged.qayd.ui.components.Silk
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.components.pressScale
import dev.saged.qayd.ui.home.HomeScreen
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.theme.LocalPalette

enum class Tab(val label: String, val icon: ImageVector) {
    Home("الرئيسية", QIcons.Home),
    Customers("الزبائن", QIcons.Users),
    Messages("الرسائل", QIcons.Msg),
    More("المزيد", QIcons.More),
}

private sealed interface Page {
    data class CustomerDetail(val id: String) : Page
    data object Expenses : Page
    data object Team : Page
    data object Audit : Page
    data object Power : Page
    data object Templates : Page
    data object Reports : Page
    data object Stock : Page
    data object Suppliers : Page
    data object Orders : Page
    data object Holds : Page
}

/** تنقل صفحات "المزيد". */
class MoreNav(
    val expenses: (() -> Unit)?,
    val team: (() -> Unit)?,
    val audit: (() -> Unit)?,
    val prices: (() -> Unit)? = null,
    val power: (() -> Unit)? = null,
    val reports: (() -> Unit)? = null,
    val backup: (() -> Unit)? = null,
    val reportsLocked: Boolean = false,
    val stock: (() -> Unit)? = null,
    val suppliers: (() -> Unit)? = null,
    val lockedModules: List<String> = emptyList(),
    val orders: (() -> Unit)? = null,
    val holds: (() -> Unit)? = null,
)

@Composable
fun QaydShell(
    shop: Shop,
    account: Account,
    sync: SyncState,
    ledger: Ledger,
    slots: List<PsSlot>,
    actions: ShopActions,
    access: Access = Access.Full,
    members: List<Member> = emptyList(),
    audit: List<AuditEntry> = emptyList(),
    prices: Prices = Prices.None,
    power: PowerState = PowerState.Empty,
    gateway: Gateway = Gateway.None,
    queue: List<SmsMsg> = emptyList(),
    templates: List<Template> = emptyList(),
    features: Map<String, String> = emptyMap(),
    smsUsed: Int = 0,
    stock: dev.saged.qayd.data.StockState = dev.saged.qayd.data.StockState.Empty,
    suppliers: dev.saged.qayd.data.SupplierState = dev.saged.qayd.data.SupplierState.Empty,
    stockActions: dev.saged.qayd.ui.stock.StockActions = dev.saged.qayd.ui.stock.StockActions.None,
    menu: List<dev.saged.qayd.data.MenuItem> = emptyList(),
    orders: List<dev.saged.qayd.data.Order> = emptyList(),
    orderActions: dev.saged.qayd.ui.orders.OrderActions = dev.saged.qayd.ui.orders.OrderActions.None,
    holds: List<dev.saged.qayd.data.Hold> = emptyList(),
    holdActions: dev.saged.qayd.ui.holds.HoldActions = dev.saged.qayd.ui.holds.HoldActions.None,
    notice: String? = null,
    onNoticeClose: () -> Unit = {},
    more: @Composable (PaddingValues, MoreNav) -> Unit,
) = androidx.compose.runtime.CompositionLocalProvider(
    dev.saged.qayd.ui.components.LocalQuickAdds provides dev.saged.qayd.data.BizType.of(shop.type).quickAdds,
) {
    val p = LocalPalette.current
    val full = shop
    // الوحدات المدفوعة غير المفعّلة لهذا المحل تختفي من الواجهة ويظهر مكانها قفل
    @Suppress("NAME_SHADOWING")
    val shop = full.copy(modules = full.modules.filter { full.unlocked(it.key, features) }.toSet())
    val locked = (full.modules - shop.modules).map { it.label }
    val pdfOn = full.unlocked("pdf", features)
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var page by remember { mutableStateOf<Page?>(null) }
    var pad by remember { mutableStateOf<PadRequest?>(null) }
    var picking by remember { mutableStateOf<TxKind?>(null) }
    var newAction by rememberSaveable { mutableStateOf(false) }
    var addingCustomer by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Tx?>(null) }
    var psOpen by remember { mutableStateOf<PsSlot?>(null) }
    var psDebt by remember { mutableStateOf<PsSlot?>(null) }
    var pricesOpen by rememberSaveable { mutableStateOf(false) }
    var smsFor by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    var gatewayHere by remember { mutableStateOf(GatewayService.isOn(ctx)) }
    val allTemplates = Templates.BUILT_IN + templates
    val owner = account.role == dev.saged.qayd.data.Role.Owner
    val tap: (Tx) -> Unit = { confirm = it }
    val pdf = remember { QaydPdf(ctx) }
    val top = WindowInsets.statusBars.asPaddingValues()
    val pad0 = PaddingValues(top = top.calculateTopPadding())

    BackHandler(page != null) { page = null }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        Ambient()
        Column(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = page to tab,
                modifier = Modifier.weight(1f),
                transitionSpec = { fadeIn(tween(220, easing = Silk)) togetherWith fadeOut(tween(120)) },
                label = "screen",
            ) { (pg, t) ->
                when (pg) {
                    is Page.CustomerDetail -> {
                        // زبون أُضيف للتو يظهر مع أول تحديث للمستمع
                        val c = ledger.balanceOf(pg.id)?.customer
                        if (c != null) {
                            CustomerPage(
                                pad0, c, ledger, onBack = { page = null },
                                onDebt = { pad = PadRequest.Debt(c) }.takeIf { access.can(Perm.AddDebt) },
                                onPayment = { pad = PadRequest.Payment(c) }.takeIf { access.can(Perm.AddPayment) },
                                showBalance = access.can(Perm.ViewBalances),
                                onSms = { smsFor = c.id }.takeIf { access.can(Perm.SendSms) && Module.Sms in shop.modules },
                                onPdf = {
                                    runCatching { pdf.share(pdf.statement(shop, c, ledger.history(c.id))) }
                                    Unit
                                }.takeIf { access.can(Perm.ExportPdf) && pdfOn },
                                onTxTap = tap,
                            )
                        }
                    }
                    Page.Team -> TeamPage(pad0, members, ledger, onBack = { page = null }, onAdd = { tab = Tab.More; page = null }, onSave = actions.updateMember)
                    Page.Audit -> AuditPage(pad0, audit, onBack = { page = null })
                    Page.Stock -> dev.saged.qayd.ui.stock.StockPage(
                        pad0, stock, suppliers.items.map { it.supplier }, access.can(Perm.ManageStock), access.can(Perm.QuickSale), stockActions, onBack = { page = null },
                    )
                    Page.Holds -> dev.saged.qayd.ui.holds.HoldsPage(pad0, holds, stock.items, access.can(Perm.DeleteTx), holdActions, onBack = { page = null })
                    Page.Orders -> dev.saged.qayd.ui.orders.OrdersPage(
                        pad0, shop.name, menu, orders, access.can(Perm.ManageMenu), access.can(Perm.DeleteTx), orderActions, onBack = { page = null },
                    )
                    Page.Suppliers -> dev.saged.qayd.ui.suppliers.SuppliersPage(pad0, suppliers, access.can(Perm.ManageSuppliers), stockActions, onBack = { page = null })
                    Page.Reports -> ReportsPage(pad0, ledger.report(System.currentTimeMillis()), onBack = { page = null })
                    Page.Templates -> TemplatesPage(pad0, templates, onBack = { page = null }, onSave = actions.saveTemplate, onDelete = actions.deleteTemplate)
                    Page.Power -> PowerPage(
                        pad0, power, prices.kwhPrice, access.can(Perm.RecordReadings), access.can(Perm.ManageElectricity),
                        onBack = { page = null }, onOpenCustomer = { page = Page.CustomerDetail(it) },
                        onReading = actions.addReading, onAddSubscriber = actions.addSubscriber,
                    )
                    Page.Expenses -> ExpensesPage(
                        pad0, ledger, onBack = { page = null }, onAdd = { pad = PadRequest.Expense() },
                        onPay = { pad = PadRequest.Expense(it) },
                        onAddRecurring = actions.addRecurring, onDeleteRecurring = actions.deleteRecurring,
                        onTxTap = tap,
                    )
                    null -> when (t) {
                        Tab.Home -> HomeScreen(
                            pad0, shop, account, sync, ledger, slots, access,
                            onQuickSale = { pad = PadRequest.Sale }.takeIf { access.can(Perm.QuickSale) }, onTxTap = tap,
                            onPs = { if (access.can(Perm.ManagePs)) psOpen = it },
                            pendingReadings = power.pending,
                            onPower = { page = Page.Power },
                            lowStock = stock.lowCount.takeIf { Module.Inventory in shop.modules },
                            onStock = { page = Page.Stock },
                            orders = orders.takeIf { Module.Menu in shop.modules }?.let { o ->
                                val t = o.filter { it.at >= dev.saged.qayd.data.Ledger.dayStart(System.currentTimeMillis()) && it.status != dev.saged.qayd.data.OrderStatus.Cancelled }
                                Triple(t.size, t.count { it.status == dev.saged.qayd.data.OrderStatus.New }, t.sumOf { it.total })
                            },
                            onOrders = { page = Page.Orders },
                            notice = notice, onNoticeClose = onNoticeClose,
                            locked = locked,
                        )
                        Tab.Customers -> CustomersScreen(
                            pad0, ledger, access.can(Perm.ViewBalances), onOpen = { page = Page.CustomerDetail(it.id) },
                            onAdd = { addingCustomer = true }.takeIf { access.can(Perm.ManageCustomers) },
                        )
                        Tab.Messages -> if (Module.Sms in shop.modules) {
                            MessagesScreen(
                                pad0, shop, gateway, queue, ledger, power, allTemplates,
                                smsLeft = full.smsLeft(smsUsed), smsQuota = full.smsQuota,
                                canSend = access.can(Perm.SendSms), canTemplates = access.can(Perm.ManageTemplates), gatewayHere = gatewayHere,
                                onGateway = { on -> gatewayHere = on; actions.gateway(on) }, onRetry = actions.retrySms,
                                onEnqueue = actions.enqueueSms, onTemplates = { page = Page.Templates },
                            )
                        } else if (Module.Sms in full.modules) {
                            Column(Modifier.padding(pad0).padding(20.dp)) { LockedCard(Module.Sms.label) }
                        } else {
                            EmptyTab(pad0, QIcons.Msg, "الرسائل غير مفعّلة", "تواصل مع مالك المنصة لتفعيل وحدة الرسائل لمحلك.")
                        }
                        Tab.More -> more(
                            pad0,
                            MoreNav(
                                expenses = { page = Page.Expenses }.takeIf { access.can(Perm.Expenses) },
                                team = { page = Page.Team }.takeIf { owner },
                                audit = { page = Page.Audit }.takeIf { owner },
                                power = { page = Page.Power }.takeIf { Module.Electricity in shop.modules },
                                reports = { page = Page.Reports }.takeIf { access.can(Perm.ViewReports) && full.unlocked("reports", features) },
                                backup = actions.backup.takeIf { owner },
                                reportsLocked = access.can(Perm.ViewReports) && !full.unlocked("reports", features),
                                stock = { page = Page.Stock }.takeIf { Module.Inventory in shop.modules },
                                orders = { page = Page.Orders }.takeIf { Module.Menu in shop.modules },
                                holds = { page = Page.Holds }.takeIf { Module.Layaway in shop.modules && access.can(Perm.QuickSale) },
                                suppliers = { page = Page.Suppliers }.takeIf { Module.Suppliers in shop.modules },
                                lockedModules = listOf(Module.Inventory, Module.Suppliers, Module.Menu, Module.Layaway).filter { it in full.modules && it !in shop.modules }.map { it.label },
                                prices = { pricesOpen = true }.takeIf {
                                    access.can(Perm.SetPrices) && (Module.Ps in shop.modules || Module.Electricity in shop.modules)
                                },
                            ),
                        )
                    }
                }
            }
            BottomBar(tab) { tab = it; page = null }
        }
        Fab(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().offset(y = (-34).dp)) { newAction = true }
    }

    if (newAction) {
        NewActionSheet(access, onPick = { k ->
            newAction = false
            when (k) {
                TxKind.Sale -> pad = PadRequest.Sale
                TxKind.Expense -> pad = PadRequest.Expense()
                else -> picking = k
            }
        }) { newAction = false }
    }

    picking?.let { k ->
        CustomerPickerSheet(
            title = if (k == TxKind.Debt) "دين على زبون" else "دفعة من زبون",
            customers = ledger.customers,
            onPick = { c -> picking = null; pad = if (k == TxKind.Debt) PadRequest.Debt(c) else PadRequest.Payment(c) },
            onCreate = { n, ph ->
                val c = Customer(actions.addCustomer(n, ph), n.trim(), ph.trim(), "", System.currentTimeMillis())
                picking = null
                pad = if (k == TxKind.Debt) PadRequest.Debt(c) else PadRequest.Payment(c)
            },
        ) { picking = null }
    }

    if (addingCustomer) {
        QSheet("زبون جديد", { addingCustomer = false }) {
            NewCustomerForm { n, ph ->
                val id = actions.addCustomer(n, ph)
                addingCustomer = false
                page = Page.CustomerDetail(id)
            }
        }
    }

    when (val r = pad) {
        PadRequest.Sale -> NumpadSheet("بيع سريع", "يُسجل كبيع للمحل مباشرة", p.ok, "سجّل البيع", onDismiss = { pad = null }) { v, _ -> actions.sale(v) }
        is PadRequest.Debt -> NumpadSheet(
            "دين على ${r.customer.name}", balanceText(ledger.balanceOf(r.customer.id)?.balance ?: 0.0, p).first,
            p.danger, "أضف الدين", onDismiss = { pad = null },
        ) { v, _ -> actions.debt(r.customer.id, v) }
        is PadRequest.Payment -> NumpadSheet(
            "دفعة من ${r.customer.name}", balanceText(ledger.balanceOf(r.customer.id)?.balance ?: 0.0, p).first,
            p.ok, "سجّل الدفعة", onDismiss = { pad = null },
        ) { v, _ -> actions.payment(r.customer.id, v) }
        is PadRequest.Expense -> NumpadSheet(
            title = r.recurring?.item?.title ?: "مصروف",
            subtitle = r.recurring?.let { "${it.item.category.label} · دفعة هذا الشهر" },
            accent = p.pri, confirm = "سجّل المصروف",
            initial = AmountInput.of(r.recurring?.lastAmount),
            chips = if (r.recurring == null) ExpenseCategory.entries.map { PadChip(it.key, it.label) } else emptyList(),
            onDismiss = { pad = null },
        ) { v, chip -> actions.expense(r.recurring?.item?.category ?: ExpenseCategory.of(chip), v, r.recurring?.item?.id) }
        is PadRequest.Price -> NumpadSheet(
            r.title, "السعر الحالي ${if (r.current > 0) cur(r.current) else "غير محدد"}", p.pri, "حفظ السعر",
            initial = AmountInput.of(r.current), onDismiss = { pad = null },
        ) { v, _ -> actions.setPrice(r.key, v) }
        null -> Unit
    }

    smsFor?.let { id ->
        val c = ledger.balanceOf(id)
        if (c != null) {
            val reading = power.subscribers.firstOrNull { it.customer.id == id }?.history?.firstOrNull()
            SendSheet(
                "رسالة إلى ${c.customer.name}", allTemplates, 1,
                preview = { t -> Templates.fill(t.body, smsVars(shop, c, reading)) },
                onSend = { t -> actions.enqueueSms(listOf(Triple(c.customer.phone, c.customer.name, Templates.fill(t.body, smsVars(shop, c, reading)))), t.title) },
                left = full.smsLeft(smsUsed),
            ) { smsFor = null }
        }
    }

    psOpen?.let { open ->
        val s = slots.firstOrNull { it.number == open.number } ?: open
        if (!s.running) {
            PsStartSheet(s.number, prices.psRate, onStart = { m -> actions.psStart(s.number, m) }) { psOpen = null }
        } else {
            PsSessionSheet(
                s, onExtend = { actions.psExtend(s) }, onPause = { actions.psPause(s) },
                onEndCash = { actions.psEnd(s, s.cost(System.currentTimeMillis()), null) },
                onEndDebt = { psDebt = s },
            ) { psOpen = null }
        }
    }

    psDebt?.let { s ->
        CustomerPickerSheet(
            title = "جلسة شاشة ${s.number} على زبون",
            customers = ledger.customers,
            onPick = { c -> actions.psEnd(s, s.cost(System.currentTimeMillis()), c.id); psDebt = null },
            onCreate = { n, ph -> actions.psEnd(s, s.cost(System.currentTimeMillis()), actions.addCustomer(n, ph)); psDebt = null },
        ) { psDebt = null }
    }

    if (pricesOpen) {
        PricesSheet(prices, Module.Ps in shop.modules, Module.Electricity in shop.modules, onEdit = { pricesOpen = false; pad = it }) { pricesOpen = false }
    }

    confirm?.let { t ->
        val cust = t.customerId?.let { ledger.balanceOf(it)?.customer }
        ConfirmDeleteSheet(
            t, cust?.name,
            onDelete = { actions.deleteTx(t) }.takeIf { access.can(Perm.DeleteTx) },
            onReceipt = {
                val after = cust?.let { c ->
                    ledger.history(c.id).filter { it.at <= t.at }.sumOf { if (it.kind == TxKind.Debt) it.amount else -it.amount }
                }
                runCatching { pdf.share(pdf.receipt(shop, t, cust, after)) }
                Unit
            }.takeIf { access.can(Perm.ExportPdf) && pdfOn },
        ) { confirm = null }
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.surface)
            .drawWithContent {
                drawContent()
                drawLine(p.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            }
            .navigationBarsPadding()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
    ) {
        val items = Tab.entries
        items.forEachIndexed { i, t ->
            if (i == 2) Spacer(Modifier.weight(1f))
            val selected = t == current
            val c = if (selected) p.pri else p.mute
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Tab) {
                        if (!selected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(t)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                QIcon(t.icon, c, Modifier.size(22.dp), t.label)
                T(t.label, Modifier.padding(top = 4.dp), 11.sp, if (selected) FontWeight.Medium else FontWeight.Normal, c)
            }
        }
    }
}

@Composable
private fun Fab(modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .pressScale(source, .9f)
            .shadow(18.dp, shape, ambientColor = p.pri, spotColor = p.pri)
            .size(60.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(p.pri, lerp(p.pri, Color.Black, .4f))))
            .clickable(source, indication = null, role = Role.Button, onClickLabel = "عملية جديدة") {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        QIcon(QIcons.Plus, p.onPri, Modifier.size(26.dp), "عملية جديدة")
    }
}

@Composable
private fun EmptyTab(padding: PaddingValues, icon: ImageVector, title: String, body: String) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxSize().padding(padding).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(p.surface2),
            contentAlignment = Alignment.Center,
        ) { QIcon(icon, p.pri, Modifier.size(32.dp)) }
        Spacer(Modifier.height(18.dp))
        T(title, size = 18.sp, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        T(body, size = 13.5.sp, color = p.mute, maxLines = 3)
    }
}
