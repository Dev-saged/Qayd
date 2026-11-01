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
import dev.saged.qayd.ui.home.HomeSummary
import dev.saged.qayd.ui.home.SampleHome
import dev.saged.qayd.ui.home.idleSlots
import dev.saged.qayd.ui.more.MoreScreen
import dev.saged.qayd.ui.shell.QaydShell
import dev.saged.qayd.ui.theme.QaydTheme
import dev.saged.qayd.ui.theme.oklch
import dev.saged.qayd.ui.theme.paletteOf
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

    private val home: @Composable () -> Unit = {
        QaydShell(shop, owner, SyncState.Synced, SampleHome.summary, SampleHome.slots(System.currentTimeMillis())) {}
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
        QaydShell(shop, owner, SyncState.Pending, HomeSummary.Empty, idleSlots(3)) {}
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
