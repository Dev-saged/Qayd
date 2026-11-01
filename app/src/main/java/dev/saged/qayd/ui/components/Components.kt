package dev.saged.qayd.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.ui.theme.LocalPalette
import dev.saged.qayd.ui.theme.Readex
import kotlinx.coroutines.delay

/** منحنى الحركة الفيزيائي الموحّد. */
val Silk = CubicBezierEasing(.22f, 1f, .36f, 1f)

/** يحترم إعداد "إزالة الحركات" في النظام. */
@Composable
fun rememberMotionEnabled(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/** دخول متتابع: شفافية وانزلاق خفيف حسب الترتيب. */
fun Modifier.entrance(index: Int): Modifier = composed {
    val motion = rememberMotionEnabled()
    val a = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (motion) {
            delay(60L * index)
            a.animateTo(1f, tween(520, easing = Silk))
        }
    }
    graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 18.dp.toPx()
    }
}

/** تصغير لحظي عند الضغط. */
fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = .95f): Modifier = composed {
    val down by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (down) pressed else 1f, tween(180, easing = Silk), label = "press")
    graphicsLayer { scaleX = s; scaleY = s }
}

/** ساعة تتقدم كل ثانية للعدادات. */
@Composable
fun rememberNow(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(1000L - System.currentTimeMillis() % 1000L)
        value = System.currentTimeMillis()
    }
}

@Composable
fun T(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = LocalPalette.current.ink,
    maxLines: Int = 1,
) = Text(
    text = text,
    modifier = modifier,
    maxLines = maxLines,
    style = TextStyle(fontFamily = Readex, fontSize = size, fontWeight = weight, color = color),
)

@Composable
fun QIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, contentDescription: String? = null) =
    Icon(icon, contentDescription, modifier, tint)

@Composable
fun Ring(progress: Float, color: Color, track: Color, modifier: Modifier = Modifier, stroke: Dp = 5.dp) {
    val p by animateFloatAsState(progress, tween(600, easing = Silk), label = "ring")
    Canvas(modifier) {
        val w = stroke.toPx()
        val tl = Offset(w / 2, w / 2)
        val sz = Size(size.width - w, size.height - w)
        drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(w))
        if (p > 0f) drawArc(color, -90f, 360f * p, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** خط أداء مع تعبئة متدرجة. */
@Composable
fun Sparkline(points: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val max = points.max()
        val min = points.min()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val step = size.width / (points.size - 1)
        fun pt(i: Int) = Offset(i * step, size.height * (1f - (points[i] - min) / span) * .82f + size.height * .09f)
        val line = Path().apply {
            moveTo(pt(0).x, pt(0).y)
            for (i in 1 until points.size) {
                val a = pt(i - 1)
                val b = pt(i)
                val mx = (a.x + b.x) / 2
                cubicTo(mx, a.y, mx, b.y, b.x, b.y)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = .22f), Color.Transparent)))
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** خلفية حيّة: توهّج ذهبي وأخضر ينجرف ببطء. */
@Composable
fun Ambient(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val motion = rememberMotionEnabled()
    val drift = if (motion) {
        val tr = rememberInfiniteTransition(label = "ambient")
        tr.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d").value
    } else 0f
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val g1 = Offset(w * (.82f - .1f * drift), h * (.06f + .05f * drift))
            drawCircle(
                Brush.radialGradient(listOf(p.pri.copy(alpha = if (p.dark) .20f else .14f), Color.Transparent), g1, w * .75f),
                w * .75f, g1,
            )
            val g2 = Offset(w * (.08f + .12f * drift), h * (.58f - .06f * drift))
            drawCircle(
                Brush.radialGradient(listOf(p.ok.copy(alpha = .08f), Color.Transparent), g2, w * .7f),
                w * .7f, g2,
            )
        }
    }
}

/** نبض للنقاط الحية. */
@Composable
fun rememberPulse(): Float {
    if (!rememberMotionEnabled()) return 1f
    val tr = rememberInfiniteTransition(label = "pulse")
    return tr.animateFloat(1f, .4f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p").value
}
