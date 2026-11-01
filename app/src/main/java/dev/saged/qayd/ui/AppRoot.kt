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
            val slots = remember(shop.psScreens) { idleSlots(shop.psScreens) }
            val ledger = rememberLedger(vm, shop.id)
            val actions = remember(shop.id) { shopActions(vm, shop.id) }
            QaydShell(shop, account, current.second, ledger, slots, actions) { pad, openExpenses ->
                MoreScreen(
                    padding = pad, shop = shop, account = account, sync = current.second, op = op,
                    onRename = { vm.repo.renameShop(shop.id, it) },
                    onAddStaff = vm::createStaff,
                    onClearOp = vm::clearOp,
                    onSignOut = vm::signOut,
                    onExpenses = openExpenses.takeIf { account.role == Role.Owner },
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

private fun shopActions(vm: AppViewModel, shopId: String) = vm.repo.let { r ->
    ShopActions(
        sale = { r.addTx(shopId, TxKind.Sale, it) },
        debt = { c, v -> r.addTx(shopId, TxKind.Debt, v, customerId = c) },
        payment = { c, v -> r.addTx(shopId, TxKind.Payment, v, customerId = c) },
        expense = { cat, v, rec -> r.addTx(shopId, TxKind.Expense, v, category = cat, recurringId = rec) },
        deleteTx = { r.deleteTx(shopId, it) },
        addCustomer = { n, ph -> r.addCustomer(shopId, n, ph) },
        addRecurring = { t, c, d -> r.addRecurring(shopId, t, c, d) },
        deleteRecurring = { r.deleteRecurring(shopId, it) },
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
