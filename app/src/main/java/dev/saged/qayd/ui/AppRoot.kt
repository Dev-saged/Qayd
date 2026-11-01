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
import dev.saged.qayd.data.Access
import dev.saged.qayd.data.Module
import dev.saged.qayd.data.PowerState
import dev.saged.qayd.data.Prices
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
    AdminScreen(
        account = account, shops = data.first, sync = data.second, op = op,
        onCreate = vm::createShop,
        onToggle = { shop, on -> vm.repo.setShopActive(shop.id, on) },
        onClearOp = vm::clearOp,
        onSignOut = vm::signOut,
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
            val actions = remember(shop.id, ledger, prices) { shopActions(vm, shop.id, ledger, prices) }
            val members by remember(shop.id) { vm.repo.members(shop.id) }.collectAsStateWithLifecycle(emptyList())
            val audit by remember(shop.id, account.role) {
                if (account.role == Role.Owner) vm.repo.audit(shop.id) else kotlinx.coroutines.flow.flowOf(emptyList())
            }.collectAsStateWithLifecycle(emptyList())
            val me = members.firstOrNull { it.uid == account.uid }
            val access = if (account.role == Role.Owner) Access.Full else Access(Role.Staff, me?.perms.orEmpty(), me?.active ?: true)
            if (!access.active) return Blocked("حسابك موقوف من صاحب المحل", vm::signOut)
            QaydShell(shop, account, current.second, ledger, slots, actions, access, members, audit, prices, power) { pad, nav ->
                MoreScreen(
                    padding = pad, shop = shop, account = account, sync = current.second, op = op,
                    onRename = { vm.repo.renameShop(shop.id, it) },
                    onAddStaff = vm::createStaff,
                    onClearOp = vm::clearOp,
                    onSignOut = vm::signOut,
                    nav = nav,
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

private fun shopActions(vm: AppViewModel, shopId: String, ledger: Ledger, prices: Prices) = vm.repo.let { r ->
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
    )
}

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
