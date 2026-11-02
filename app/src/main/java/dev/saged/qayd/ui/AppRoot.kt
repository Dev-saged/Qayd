package dev.saged.qayd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.saged.qayd.config.BrandConfig
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.saged.qayd.data.Customer
import dev.saged.qayd.data.Reading
import dev.saged.qayd.data.Template
import dev.saged.qayd.system.AppLock
import dev.saged.qayd.system.AutoBackup
import dev.saged.qayd.system.Perms
import dev.saged.qayd.ui.perms.PermissionsScreen
import dev.saged.qayd.system.Backup
import dev.saged.qayd.data.Access
import dev.saged.qayd.data.Module
import dev.saged.qayd.data.PowerState
import dev.saged.qayd.data.Prices
import dev.saged.qayd.data.Gateway
import dev.saged.qayd.gateway.GatewayService
import dev.saged.qayd.notify.PsAlarms
import dev.saged.qayd.data.Account
import dev.saged.qayd.data.Ledger
import dev.saged.qayd.data.TxKind
import dev.saged.qayd.ui.components.rememberNow
import dev.saged.qayd.ui.shell.ShopActions
import dev.saged.qayd.data.Role
import dev.saged.qayd.data.Shop
import dev.saged.qayd.data.SyncState
import dev.saged.qayd.ui.admin.AdminScreen
import dev.saged.qayd.ui.auth.LoginScreen
import dev.saged.qayd.ui.components.Ambient
import dev.saged.qayd.ui.components.QButton
import dev.saged.qayd.ui.components.QIcon
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.components.rememberPulse
import dev.saged.qayd.ui.home.idleSlots
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.more.MoreScreen
import dev.saged.qayd.ui.shell.QaydShell
import dev.saged.qayd.ui.theme.LocalPalette

/** يوجّه حسب الجلسة: الدخول، لوحة المالك، أو تطبيق المحل. */
@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val session by vm.session.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var locked by remember { mutableStateOf(AppLock.enabled(ctx)) }
    var hiddenAt by remember { mutableStateOf(0L) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> hiddenAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> if (AppLock.enabled(ctx) && AppLock.shouldLock(hiddenAt)) locked = true
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    if (locked && session is Session.Active) return LockScreen { (ctx as? FragmentActivity)?.let { AppLock.prompt(it) { locked = false } } }

    var firstRun by remember { mutableStateOf(!Perms.seen(ctx)) }
    val updatesOpen by dev.saged.qayd.system.Updates.pageOpen.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        AppContent(vm, session)
        if (updatesOpen) dev.saged.qayd.ui.updates.UpdatesPage { dev.saged.qayd.system.Updates.pageOpen.value = false }
        // الشاشات المنبثقة تُرسم فوق التطبيق، فيبقى المستخدم في مكانه عند إغلاقها
        if (firstRun || Perms.open.value) {
            val close = { Perms.markSeen(ctx); firstRun = false; Perms.open.value = false }
            androidx.activity.compose.BackHandler(onBack = close)
            PermissionsScreen(close)
        }
        // حدّ ثابت خلف شريط الإشعارات حتى لا يمر المحتوى تحته عند التمرير
        Box(
            Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                .background(LocalPalette.current.bg),
        )
        dev.saged.qayd.ui.updates.UpdateLayer(Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun AppContent(vm: AppViewModel, session: Session) {
    when (val s = session) {
        Session.Loading -> Splash()
        Session.SignedOut -> {
            val op by vm.login.collectAsStateWithLifecycle()
            LoginScreen(op, vm::signIn)
        }
        is Session.Active -> when (s.account.role) {
            Role.SuperAdmin -> AdminRoot(vm, s.account)
            Role.Owner, Role.Staff -> ShopRoot(vm, s.account)
        }
    }
}

@Composable
private fun AdminRoot(vm: AppViewModel, account: Account) {
    val flow = remember { vm.repo.shops() }
    val data by flow.collectAsStateWithLifecycle(emptyList<Shop>() to SyncState.Offline)
    val op by vm.adminOp.collectAsStateWithLifecycle()
    val features by remember { vm.repo.features() }.collectAsStateWithLifecycle(emptyMap())
    AdminScreen(
        account = account, shops = data.first, sync = data.second, op = op,
        onCreate = vm::createShop,
        onToggle = { shop, on -> vm.repo.setShopActive(shop.id, on) },
        onClearOp = vm::clearOp,
        onSignOut = vm::signOut,
        features = features,
        onFeature = vm.repo::setFeature,
        onSaveShop = { s, m, n, paid, lic, name, quota, type -> vm.repo.updateShop(s.id, m, n, paid, lic, name, quota, type) },
    )
}

@Composable
private fun ShopRoot(vm: AppViewModel, account: Account) {
    val shopId = account.shopId ?: return Blocked("الحساب غير مرتبط بمحل", vm::signOut)
    val flow = remember(shopId) { vm.repo.shop(shopId) }
    val state by flow.collectAsStateWithLifecycle(null)
    val current = state ?: return Splash()
    val shop = current.first
    when {
        shop == null && current.second == SyncState.Synced -> Blocked("المحل غير موجود", vm::signOut)
        shop == null -> Splash()
        shop.blockReason() != null -> Blocked(shop.blockReason()!!, vm::signOut)
        else -> {
            val op by vm.adminOp.collectAsStateWithLifecycle()
            val ctx = LocalContext.current
            val live by remember(shop.id) { vm.repo.ps(shop.id) }.collectAsStateWithLifecycle(emptyList())
            val prices by remember(shop.id) { vm.repo.prices(shop.id) }.collectAsStateWithLifecycle(Prices.None)
            val slots = remember(shop.psScreens, live) {
                idleSlots(shop.psScreens).map { s -> live.firstOrNull { it.number == s.number && it.running } ?: s }
            }
            LaunchedEffect(slots) { if (Module.Ps in shop.modules) PsAlarms.sync(ctx, slots) }
            val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            val ledger = rememberLedger(vm, shop.id)
            val readings by remember(shop.id) { vm.repo.readings(shop.id) }.collectAsStateWithLifecycle(emptyList())
            val customers by remember(shop.id) { vm.repo.customers(shop.id) }.collectAsStateWithLifecycle(emptyList())
            val power = remember(customers, readings) { PowerState.of(customers, readings, System.currentTimeMillis()) }
            val gateway by remember(shop.id) { vm.repo.gateway(shop.id) }.collectAsStateWithLifecycle(Gateway.None)
            val queue by remember(shop.id) { vm.repo.smsQueue(shop.id) }.collectAsStateWithLifecycle(emptyList())
            val templates by remember(shop.id) { vm.repo.templates(shop.id) }.collectAsStateWithLifecycle(emptyList())
            val features by remember { vm.repo.features() }.collectAsStateWithLifecycle(emptyMap())
            val smsUsed by remember(shop.id) { vm.repo.smsUsed(shop.id) }.collectAsStateWithLifecycle(0)
            val products by remember(shop.id) { vm.repo.products(shop.id) }.collectAsStateWithLifecycle(emptyList())
            val moves by remember(shop.id) { vm.repo.moves(shop.id) }.collectAsStateWithLifecycle(emptyList())
            val stock = remember(products, moves) { dev.saged.qayd.data.StockState.of(products, moves) }
            val supplierList by remember(shop.id) { vm.repo.suppliers(shop.id) }.collectAsStateWithLifecycle(emptyList())
            val stx by remember(shop.id) { vm.repo.stx(shop.id) }.collectAsStateWithLifecycle(emptyList())
            val suppliers = remember(supplierList, stx) { dev.saged.qayd.data.SupplierState.of(supplierList, stx) }
            val stockActions = remember(shop.id) {
                val r = vm.repo
                val id = shop.id
                dev.saged.qayd.ui.stock.StockActions(
                    addProduct = { r.addProduct(id, it) },
                    editProduct = { r.editProduct(id, it) },
                    deleteProduct = { r.deleteProduct(id, it) },
                    sell = { pr, q -> r.sellProduct(id, pr, q) },
                    receive = { pr, q, c, s -> r.receiveStock(id, pr, q, c, s) },
                    adjust = { pr, cur, act -> r.adjustStock(id, pr, cur, act) },
                    addSupplier = { n, ph -> r.addSupplier(id, n, ph) },
                    supplierTx = { s, k, v, note -> r.addSupplierTx(id, s, k, v, note) },
                )
            }
            val actions = remember(shop.id, ledger, prices, customers, readings, templates) {
                shopActions(vm, shop.id, ledger, prices, ctx, customers, readings, templates)
            }
            val members by remember(shop.id) { vm.repo.members(shop.id) }.collectAsStateWithLifecycle(emptyList())
            val audit by remember(shop.id, account.role) {
                if (account.role == Role.Owner) vm.repo.audit(shop.id) else kotlinx.coroutines.flow.flowOf(emptyList())
            }.collectAsStateWithLifecycle(emptyList())
            val autoBackupOn = account.role == Role.Owner && shop.unlocked("autoBackup", features)
            LaunchedEffect(autoBackupOn, customers.size, ledger.allTx.size) {
                if (autoBackupOn && customers.isNotEmpty() && AutoBackup.due(ctx)) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { AutoBackup.store(ctx, Backup.write(ctx, shop.id, ledger, customers, readings, templates)) }
                    }
                }
            }
            val me = members.firstOrNull { it.uid == account.uid }
            val access = if (account.role == Role.Owner) Access.Full else Access(Role.Staff, me?.perms.orEmpty(), me?.active ?: true)
            if (!access.active) return Blocked("حسابك موقوف من صاحب المحل", vm::signOut)
            QaydShell(shop, account, current.second, ledger, slots, actions, access, members, audit, prices, power, gateway, queue, templates, features, smsUsed, stock, suppliers, stockActions) { pad, nav ->
                MoreScreen(
                    padding = pad, shop = shop, account = account, sync = current.second, op = op,
                    onRename = { vm.repo.renameShop(shop.id, it) },
                    onAddStaff = vm::createStaff,
                    onClearOp = vm::clearOp,
                    onSignOut = vm::signOut,
                    nav = nav,
                    autoBackup = if (account.role == Role.Owner) shop.unlocked("autoBackup", features) else null,
                )
            }
        }
    }
}

@Composable
private fun rememberLedger(vm: AppViewModel, shopId: String): Ledger {
    val customers by remember(shopId) { vm.repo.customers(shopId) }.collectAsStateWithLifecycle(emptyList())
    val txs by remember(shopId) { vm.repo.txs(shopId) }.collectAsStateWithLifecycle(emptyList())
    val recurring by remember(shopId) { vm.repo.recurring(shopId) }.collectAsStateWithLifecycle(emptyList())
    val now by rememberNow()
    val day = Ledger.dayStart(now)
    return remember(customers, txs, recurring, day) {
        Ledger.of(customers, txs, recurring, System.currentTimeMillis(), BrandConfig.OVERDUE_DAYS)
    }
}

private fun shopActions(
    vm: AppViewModel, shopId: String, ledger: Ledger, prices: Prices, ctx: android.content.Context,
    customers: List<Customer>, readings: List<Reading>, templates: List<Template>,
) = vm.repo.let { r ->
    ShopActions(
        sale = { r.addTx(shopId, TxKind.Sale, it) },
        debt = { c, v -> r.addTx(shopId, TxKind.Debt, v, customerId = c) },
        payment = { c, v -> r.addTx(shopId, TxKind.Payment, v, customerId = c) },
        expense = { cat, v, rec -> r.addTx(shopId, TxKind.Expense, v, category = cat, recurringId = rec) },
        deleteTx = { t -> r.deleteTx(shopId, t, t.customerId?.let { id -> ledger.balanceOf(id)?.customer?.name }) },
        addCustomer = { n, ph -> r.addCustomer(shopId, n, ph) },
        addRecurring = { t, c, d -> r.addRecurring(shopId, t, c, d) },
        deleteRecurring = { r.deleteRecurring(shopId, it) },
        updateMember = { m, perms, active -> r.updateMember(shopId, m, perms, active) },
        psStart = { n, m -> r.startPs(shopId, n, m, prices.psRate) },
        psPause = { r.pausePs(shopId, it) },
        psExtend = { r.extendPs(shopId, it, 30) },
        psEnd = { s, v, c -> r.endPs(shopId, s, v, c) },
        setPrice = { k, v -> r.setPrice(shopId, k, v) },
        addReading = { c, prev, curr -> r.addReading(shopId, c, prev, curr, prices.kwhPrice) },
        addSubscriber = { n, ph, m, v -> r.addSubscriber(shopId, n, ph, m, v) },
        enqueueSms = { items, t -> r.enqueueSms(shopId, items, t) },
        retrySms = { r.retrySms(shopId, it) },
        saveTemplate = { id, t, b, k -> r.saveTemplate(shopId, id, t, b, k) },
        deleteTemplate = { r.deleteTemplate(shopId, it) },
        gateway = { on -> runCatching { if (on) GatewayService.start(ctx, shopId) else GatewayService.stop(ctx) } },
        backup = { runCatching { Backup.share(ctx, Backup.write(ctx, shopId, ledger, customers, readings, templates)) } },
    )
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    val p = LocalPalette.current
    LaunchedEffect(Unit) { onUnlock() }
    Box(Modifier.fillMaxSize().background(p.bg)) {
        Ambient()
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(p.surface2), contentAlignment = Alignment.Center) {
                QIcon(QIcons.Lock, p.pri, Modifier.size(32.dp))
            }
            Spacer(Modifier.height(18.dp))
            T("قيد مقفل", size = 20.sp, weight = FontWeight.SemiBold)
            T("افتح بالبصمة أو برمز قفل الجوال", size = 13.5.sp, color = p.mute)
            Spacer(Modifier.height(24.dp))
            QButton("افتح", onUnlock)
        }
    }
}

/** شريط التحديث: يظهر عند توفر نسخة أحدث، وينزّلها ويفتح التثبيت. */
@Composable
private fun Splash() {
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize().background(p.bg), contentAlignment = Alignment.Center) {
        T("قيد", size = 30.sp, weight = FontWeight.Bold, color = p.pri.copy(alpha = rememberPulse()))
    }
}

@Composable
private fun Blocked(reason: String, onSignOut: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize().background(p.bg)) {
        Ambient()
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(p.surface2), contentAlignment = Alignment.Center) {
                QIcon(QIcons.Shop, p.danger, Modifier.size(32.dp))
            }
            Spacer(Modifier.height(18.dp))
            T(reason, size = 19.sp, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            T("تواصل مع مالك المنصة لإعادة التفعيل. بياناتك محفوظة.", size = 13.5.sp, color = p.mute, maxLines = 2)
            Spacer(Modifier.height(24.dp))
            QButton("تسجيل الخروج", onSignOut, secondary = true)
        }
    }
}
