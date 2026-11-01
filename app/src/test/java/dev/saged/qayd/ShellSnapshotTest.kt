package dev.saged.qayd

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.saged.qayd.ui.theme.LocalPalette
import dev.saged.qayd.config.BrandConfig
import dev.saged.qayd.data.Account
import dev.saged.qayd.data.Module
import dev.saged.qayd.data.Role
import dev.saged.qayd.data.Shop
import dev.saged.qayd.data.SyncState
import dev.saged.qayd.data.Validate
import dev.saged.qayd.ui.Op
import dev.saged.qayd.ui.admin.AdminScreen
import dev.saged.qayd.ui.auth.LoginScreen
import dev.saged.qayd.data.AmountInput
import dev.saged.qayd.data.Customer
import dev.saged.qayd.data.ExpenseCategory
import dev.saged.qayd.data.Ledger
import dev.saged.qayd.data.Recurring
import dev.saged.qayd.data.Tx
import dev.saged.qayd.data.TxKind
import dev.saged.qayd.ui.components.NumpadBody
import dev.saged.qayd.ui.components.PadChip
import dev.saged.qayd.ui.customers.CustomerPage
import dev.saged.qayd.ui.customers.CustomersScreen
import dev.saged.qayd.ui.expenses.ExpensesPage
import dev.saged.qayd.ui.shell.ShopActions
import androidx.compose.foundation.layout.padding
import dev.saged.qayd.ui.home.SampleHome
import dev.saged.qayd.ui.home.idleSlots
import dev.saged.qayd.ui.more.MoreScreen
import dev.saged.qayd.ui.shell.QaydShell
import dev.saged.qayd.ui.theme.QaydTheme
import dev.saged.qayd.ui.theme.oklch
import dev.saged.qayd.ui.theme.paletteOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** يرسم الهيكل بالنمطين إلى build/snapshots ويتحقق من تباين الألوان الأساسية. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi")
class ShellSnapshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val shop = Shop("s1", "كفي الأنوار", "anoar.ps", true, null, Module.entries.toSet(), 3)
    private val owner = Account("u1", "ahmad@anoar.ps", "أحمد", Role.Owner, "s1")

    private fun snap(name: String, dark: Boolean, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { QaydTheme(dark = dark) { content() } }
        rule.mainClock.advanceTimeBy(2_000)
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { root.draw(Canvas(bmp)) }
        val out = File("build/snapshots").apply { mkdirs() }
        File(out, "$name-${if (dark) "dark" else "light"}.png").outputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private val now = System.currentTimeMillis()
    private val h = 3_600_000L
    private val cs = listOf(
        Customer("c1", "محمود أبو سعدة", "0599123456", "", now - 40 * 24 * h),
        Customer("c2", "أم خالد", "0598765432", "", now - 10 * 24 * h),
        Customer("c3", "سامر العطار", "", "", now - 5 * 24 * h),
        Customer("c4", "ليلى منصور", "0597000111", "", now - 2 * 24 * h),
    )
    private val txs = listOf(
        Tx("t1", TxKind.Debt, 1200.0, now - 38 * 24 * h, "c1", byName = "أحمد"),
        Tx("t2", TxKind.Payment, 300.0, now - 36 * 24 * h, "c1", byName = "أحمد"),
        Tx("t3", TxKind.Debt, 86.4, now - 9 * 24 * h, "c2", byName = "محمد"),
        Tx("t4", TxKind.Debt, 450.0, now - 4 * 24 * h, "c3"),
        Tx("t5", TxKind.Payment, 150.0, now - 2 * h, "c3", byName = "أحمد"),
        Tx("t6", TxKind.Payment, 200.0, now - 30 * 24 * h, "c4"),
        Tx("s1", TxKind.Sale, 12.0, now - 5 * 60_000, byName = "أحمد"),
        Tx("s2", TxKind.Sale, 35.5, now - 40 * 60_000, byName = "محمد"),
        Tx("s3", TxKind.Sale, 8.0, now - 2 * h, byName = "أحمد", pending = true),
        Tx("e1", TxKind.Expense, 120.0, now - h, category = ExpenseCategory.Internet, recurringId = "r1", period = Ledger.period(now)),
        Tx("e2", TxKind.Expense, 900.0, now - 2 * h, category = ExpenseCategory.Goods),
    )
    private val rec = listOf(Recurring("r1", "اشتراك الإنترنت", ExpenseCategory.Internet, 5), Recurring("r2", "إيجار المحل", ExpenseCategory.Rent, 1))
    private val ledger = Ledger.of(cs, txs, rec, now, 30)

    private val home: @Composable () -> Unit = {
        QaydShell(shop, owner, SyncState.Synced, ledger, SampleHome.slots(now), ShopActions.None) { _, _ -> }
    }

    @Test fun homeDark() = snap("home", true, home)

    @Test fun homeLight() = snap("home", false, home)

    @Test fun login() = snap("login", true) { LoginScreen(Op(error = "اسم المستخدم أو كلمة السر غير صحيحة")) { _, _ -> } }

    @Test fun admin() = snap("admin", true) {
        AdminScreen(
            Account("a", "sajed@qayd.ps", "ساجد", Role.SuperAdmin, null),
            listOf(shop, shop.copy(id = "s2", name = "مشاوي العبادلة", suffix = "abadla.ps", active = false, modules = setOf(Module.Debts))),
            SyncState.Synced, Op(), {}, { _, _ -> }, {}, {},
        )
    }

    @Test fun newShop() = snap("home-new", true) {
        QaydShell(shop, owner, SyncState.Pending, Ledger.Empty, idleSlots(3), ShopActions.None) { _, _ -> }
    }

    private fun page(content: @Composable () -> Unit): @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(LocalPalette.current.bg)) { content() }
    }

    @Test fun customers() = snap("customers", true, page { CustomersScreen(PaddingValues(top = 24.dp), ledger, true, {}, {}) })

    @Test fun customer() = snap("customer", true, page { CustomerPage(PaddingValues(top = 24.dp), cs[0], ledger, {}, {}, {}, {}) })

    @Test fun expenses() = snap("expenses", true, page { ExpensesPage(PaddingValues(top = 24.dp), ledger, {}, {}, {}, { _, _, _ -> }, {}, {}) })

    @Test fun numpad() = snap("numpad", true, page {
        Box(Modifier.padding(20.dp)) {
            NumpadBody("المبلغ", "عليه 900 ₪", LocalPalette.current.danger, "أضف الدين", "35.5",
                ExpenseCategory.entries.take(3).map { PadChip(it.key, it.label) }) { _, _ -> }
        }
    })

    @Test fun ledgerMath() {
        assertEquals(900.0, ledger.balanceOf("c1")!!.balance, 0.001)
        assertEquals(86.4, ledger.balanceOf("c2")!!.balance, 0.001)
        assertEquals(300.0, ledger.balanceOf("c3")!!.balance, 0.001)
        assertEquals(-200.0, ledger.balanceOf("c4")!!.balance, 0.001)
        assertEquals(1286.4, ledger.totalDebt, 0.001)
        assertEquals(200.0, ledger.totalCredit, 0.001)
        assertEquals(1, ledger.late)
        assertTrue(ledger.balanceOf("c1")!!.late)
        assertEquals(3, ledger.todaySalesCount)
        assertEquals(55.5, ledger.todaySales, 0.001)
        assertEquals(150.0, ledger.todayCollected, 0.001)
        assertEquals(1020.0, ledger.monthExpenses, 0.001)
        assertTrue(ledger.recurring.first { it.item.id == "r1" }.paid != null)
        assertTrue(ledger.recurring.first { it.item.id == "r2" }.paid == null)
        assertEquals(1286.4 - 200.0, ledger.debtTrend.last().toDouble(), 0.01)
    }

    @Test fun amountInput() {
        var t = ""
        "12.345".forEach { t = AmountInput.press(t, it) }
        assertEquals("12.34", t)
        assertEquals("12.3", AmountInput.back(t))
        assertEquals("0.", AmountInput.press("", '.'))
        assertEquals("5", AmountInput.press("0", '5'))
        assertEquals("62.34", AmountInput.add(t, 50))
        assertEquals(12.34, AmountInput.value(t), 0.0001)
        assertEquals("9999999", AmountInput.press("9999999", '9').let { if (it.length > 7) "over" else it })
        assertEquals("", AmountInput.of(0.0))
        assertEquals("120", AmountInput.of(120.0))
    }

    @Test fun more() = snap("more", true) {
        Box(Modifier.fillMaxSize().background(LocalPalette.current.bg)) {
            MoreScreen(PaddingValues(top = 24.dp), shop, owner, SyncState.Pending, Op(), {}, { _, _, _ -> }, {}, {})
        }
    }

    @Test fun validation() {
        assertTrue(Validate.suffix("anoar.ps") == null)
        assertTrue(Validate.suffix("Anoar") != null)
        assertTrue(Validate.username("ahmad") == null)
        assertTrue(Validate.username("أحمد") != null)
        assertTrue(Validate.login("ahmad@anoar.ps") == null)
        assertTrue(Validate.login("ahmad") != null)
        assertTrue(shop.copy(active = false).blockReason() != null)
        assertTrue(shop.copy(licenseUntil = 1L).blockReason() != null)
        assertTrue(shop.blockReason() == null)
    }

    @Test fun inkContrast() {
        for (dark in listOf(true, false)) {
            val p = paletteOf(dark)
            assertTrue(contrast(p.ink.luminance(), p.bg.luminance()) >= 4.5)
            assertTrue(contrast(p.mute.luminance(), p.surface.luminance()) >= 4.5)
        }
        assertTrue(oklch(BrandConfig.Palette.pri.dark).luminance() > .4f)
    }

    private fun contrast(a: Float, b: Float) = (maxOf(a, b) + .05) / (minOf(a, b) + .05)
}
