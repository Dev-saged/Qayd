package dev.saged.qayd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import dev.saged.qayd.R
import dev.saged.qayd.config.BrandConfig
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

@Immutable
data class Palette(
    val bg: Color, val surface: Color, val surface2: Color,
    val ink: Color, val mute: Color, val line: Color,
    val pri: Color, val onPri: Color,
    val danger: Color, val ok: Color, val warn: Color,
    val dark: Boolean,
)

val Readex = FontFamily(
    Font(R.font.readex_regular, FontWeight.Normal),
    Font(R.font.readex_medium, FontWeight.Medium),
    Font(R.font.readex_semibold, FontWeight.SemiBold),
    Font(R.font.readex_bold, FontWeight.Bold),
)

val LocalPalette = staticCompositionLocalOf { paletteOf(dark = true) }

/** OKLCH → sRGB مع قصّ ما يخرج عن النطاق. */
fun oklch(o: BrandConfig.Oklch): Color {
    val r = Math.toRadians(o.h.toDouble())
    val a = o.c * cos(r)
    val b = o.c * sin(r)
    val l = o.l.toDouble()
    val l3 = (l + 0.3963377774 * a + 0.2158037573 * b).pow(3)
    val m3 = (l - 0.1055613458 * a - 0.0638541728 * b).pow(3)
    val s3 = (l - 0.0894841775 * a - 1.2914855480 * b).pow(3)
    fun g(x: Double): Float {
        val v = if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055
        return v.coerceIn(0.0, 1.0).toFloat()
    }
    return Color(
        g(4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3),
        g(-1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3),
        g(-0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3),
    )
}

fun paletteOf(dark: Boolean): Palette {
    val p = BrandConfig.Palette
    fun c(t: BrandConfig.Tone) = oklch(if (dark) t.dark else t.light)
    return Palette(
        bg = c(p.bg), surface = c(p.surface), surface2 = c(p.surface2),
        ink = c(p.ink), mute = c(p.mute), line = c(p.line),
        pri = c(p.pri), onPri = c(p.onPri),
        danger = c(p.danger), ok = c(p.ok), warn = c(p.warn),
        dark = dark,
    )
}

object Type {
    val base = TextStyle(fontFamily = Readex)
    val title = base.copy(fontWeight = FontWeight.SemiBold)
}

@Composable
fun QaydTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalPalette provides paletteOf(dark),
        LocalLayoutDirection provides LayoutDirection.Rtl,
        content = content,
    )
}
