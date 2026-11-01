package dev.saged.qayd

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.saged.qayd.config.BrandConfig
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

    private fun snap(dark: Boolean) {
        rule.mainClock.autoAdvance = false
        rule.setContent { QaydTheme(dark = dark) { QaydShell() } }
        rule.mainClock.advanceTimeBy(2_000)
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { root.draw(Canvas(bmp)) }
        val out = File("build/snapshots").apply { mkdirs() }
        File(out, if (dark) "home-dark.png" else "home-light.png").outputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun homeDark() = snap(dark = true)

    @Test fun homeLight() = snap(dark = false)

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
