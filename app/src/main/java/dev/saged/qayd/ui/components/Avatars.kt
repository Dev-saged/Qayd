package dev.saged.qayd.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.ui.theme.LocalPalette

private enum class Hair { Short, Side, Hijab, Keffiyeh, Cap, Bald, Curly, Long }

private data class Look(
    val bg: Pair<Long, Long>, val shirt: Long, val skin: Long, val hair: Long,
    val style: Hair, val beard: Boolean = false, val glasses: Boolean = false, val accent: Long = 0xFF0E1322,
)

/** شخصيات مرسومة بهوية قيد: ألوان عميقة، خطوط هادئة، بلا صور ولا رموز تعبيرية. */
private val LOOKS = listOf(
    Look(0xFF1C3B4A to 0xFF0E1E28, 0xFFE4B24C, 0xFFE2B48C, 0xFF1B1410, Hair.Short, beard = true),
    Look(0xFF4A1C2C to 0xFF240C15, 0xFF2E5C55, 0xFFD9A47E, 0xFF120D0B, Hair.Hijab, accent = 0xFFC9A15A),
    Look(0xFF2C3A1C to 0xFF141C0B, 0xFFF4EEDC, 0xFFC68E66, 0xFF111111, Hair.Keffiyeh, beard = true, accent = 0xFFB3261E),
    Look(0xFF2A2350 to 0xFF140F2A, 0xFFE9C46A, 0xFFEBC09A, 0xFF3A2416, Hair.Side, glasses = true),
    Look(0xFF4B3A12 to 0xFF221A06, 0xFF1C3B4A, 0xFFB98063, 0xFF0F0F0F, Hair.Curly),
    Look(0xFF153A35 to 0xFF0A1D1A, 0xFF8E2C3D, 0xFFE6BB95, 0xFF2B1A12, Hair.Long),
    Look(0xFF3A1F12 to 0xFF1D0E07, 0xFFE4B24C, 0xFFD2A07A, 0xFF1A1A1A, Hair.Cap, accent = 0xFF1C3B4A),
    Look(0xFF20304F to 0xFF0F1828, 0xFFF2E6CC, 0xFFC89474, 0xFF2A2A2A, Hair.Bald, beard = true, glasses = true),
    Look(0xFF511E3D to 0xFF280E1E, 0xFF20304F, 0xFFF0C8A4, 0xFF5A3A22, Hair.Hijab, glasses = true, accent = 0xFFE9C46A),
    Look(0xFF1E4034 to 0xFF0E2019, 0xFFC08A2E, 0xFFA8714F, 0xFF0D0D0D, Hair.Short),
    Look(0xFF3D3D46 to 0xFF1C1C22, 0xFFB3261E, 0xFFDDAE88, 0xFF6B4A2C, Hair.Side, beard = true),
    Look(0xFF5A3A12 to 0xFF2A1A06, 0xFF153A35, 0xFFE8BC98, 0xFF1A1310, Hair.Long, glasses = true),
)

const val AVATAR_COUNT = 12

private fun DrawScope.look(l: Look) {
    val c = { v: Long -> Color(v) }
    drawCircle(Brush.linearGradient(listOf(c(l.bg.first), c(l.bg.second)), Offset(0f, 0f), Offset(100f, 100f)), 50f, Offset(50f, 50f))
    val skinShade = c(l.skin).copy(red = c(l.skin).red * .86f, green = c(l.skin).green * .84f, blue = c(l.skin).blue * .84f)
    // خلف الرأس: الشعر الطويل والحجاب والحطة
    when (l.style) {
        Hair.Long -> drawRoundRect(c(l.hair), Offset(29f, 26f), Size(42f, 50f), androidx.compose.ui.geometry.CornerRadius(20f))
        Hair.Hijab -> {
            drawPath(Path().apply { moveTo(24f, 100f); cubicTo(22f, 70f, 26f, 18f, 50f, 18f); cubicTo(74f, 18f, 78f, 70f, 76f, 100f); close() }, c(l.hair))
        }
        Hair.Keffiyeh -> {
            drawPath(Path().apply { moveTo(26f, 86f); cubicTo(24f, 60f, 27f, 20f, 50f, 20f); cubicTo(73f, 20f, 76f, 60f, 74f, 86f); close() }, Color(0xFFF4F1EA))
            for (i in 0..5) drawLine(c(l.accent).copy(alpha = .55f), Offset(28f + i * 9f, 24f), Offset(24f + i * 9f, 84f), 1.6f)
            for (i in 0..5) drawLine(c(l.accent).copy(alpha = .55f), Offset(26f, 30f + i * 10f), Offset(74f, 30f + i * 10f), 1.6f)
        }
        else -> Unit
    }
    // الكتفان والعنق
    drawPath(Path().apply { moveTo(12f, 100f); cubicTo(14f, 79f, 30f, 71f, 50f, 71f); cubicTo(70f, 71f, 86f, 79f, 88f, 100f); close() }, c(l.shirt))
    if (l.style != Hair.Hijab) drawRect(skinShade, Offset(43.5f, 56f), Size(13f, 17f))
    drawPath(Path().apply { moveTo(42f, 71.5f); lineTo(50f, 80f); lineTo(58f, 71.5f) }, c(l.accent).copy(alpha = .35f), style = Stroke(2f, cap = StrokeCap.Round))
    // الوجه
    drawOval(c(l.skin), Offset(35f, 27f), Size(30f, 35f))
    // الشعر الأمامي وأغطية الرأس
    when (l.style) {
        Hair.Short -> drawPath(Path().apply { moveTo(34.5f, 41f); cubicTo(33f, 24f, 46f, 20f, 52f, 21f); cubicTo(63f, 21f, 67f, 30f, 65.5f, 41f); cubicTo(62f, 33f, 56f, 31f, 50f, 31.5f); cubicTo(43f, 31f, 37f, 34f, 34.5f, 41f); close() }, c(l.hair))
        Hair.Side -> drawPath(Path().apply { moveTo(34.5f, 43f); cubicTo(32f, 24f, 47f, 19f, 55f, 21f); cubicTo(64f, 23f, 67f, 31f, 65.5f, 42f); cubicTo(60f, 33f, 50f, 36f, 42f, 30f); cubicTo(40f, 35f, 37f, 38f, 34.5f, 43f); close() }, c(l.hair))
        Hair.Curly -> for ((x, y) in listOf(36f to 33f, 42f to 26f, 50f to 23f, 58f to 26f, 64f to 33f, 39f to 29f, 61f to 29f)) drawCircle(c(l.hair), 6.5f, Offset(x, y))
        Hair.Long -> drawPath(Path().apply { moveTo(34.5f, 44f); cubicTo(33f, 25f, 44f, 20f, 50f, 20f); cubicTo(56f, 20f, 67f, 25f, 65.5f, 44f); cubicTo(62f, 34f, 54f, 30f, 50f, 29f); cubicTo(46f, 30f, 38f, 34f, 34.5f, 44f); close() }, c(l.hair))
        Hair.Hijab -> {
            drawPath(Path().apply { moveTo(33f, 40f); cubicTo(33f, 25f, 42f, 22f, 50f, 22f); cubicTo(58f, 22f, 67f, 25f, 67f, 40f); cubicTo(62f, 31f, 56f, 29f, 50f, 29f); cubicTo(44f, 29f, 38f, 31f, 33f, 40f); close() }, c(l.hair))
            drawPath(Path().apply { moveTo(35f, 52f); cubicTo(38f, 66f, 45f, 70f, 50f, 70f); cubicTo(55f, 70f, 62f, 66f, 65f, 52f); cubicTo(66f, 66f, 60f, 76f, 50f, 77f); cubicTo(40f, 76f, 34f, 66f, 35f, 52f); close() }, c(l.hair))
            drawLine(c(l.accent), Offset(36f, 31.5f), Offset(64f, 31.5f), 1.6f)
        }
        Hair.Keffiyeh -> {
            drawPath(Path().apply { moveTo(33f, 41f); cubicTo(33f, 25f, 42f, 22f, 50f, 22f); cubicTo(58f, 22f, 67f, 25f, 67f, 41f); cubicTo(62f, 32f, 56f, 30f, 50f, 30f); cubicTo(44f, 30f, 38f, 32f, 33f, 41f); close() }, Color(0xFFF4F1EA))
            drawRoundRect(Color(0xFF111111), Offset(33f, 25.5f), Size(34f, 4.5f), androidx.compose.ui.geometry.CornerRadius(3f))
            drawRoundRect(Color(0xFF111111), Offset(34f, 31f), Size(32f, 3f), androidx.compose.ui.geometry.CornerRadius(2f))
        }
        Hair.Cap -> {
            drawPath(Path().apply { moveTo(34f, 38f); cubicTo(34f, 22f, 44f, 19f, 50f, 19f); cubicTo(56f, 19f, 66f, 22f, 66f, 38f); close() }, c(l.accent))
            drawRoundRect(c(l.accent), Offset(46f, 34.5f), Size(26f, 4.5f), androidx.compose.ui.geometry.CornerRadius(3f))
            drawCircle(c(l.shirt), 1.8f, Offset(50f, 20.5f))
        }
        Hair.Bald -> drawArc(c(l.hair).copy(alpha = .5f), 160f, 220f, false, Offset(34f, 27f), Size(32f, 30f), style = Stroke(2.2f))
    }
    // الملامح
    drawCircle(Color(0xFF1A1410), 1.7f, Offset(44.5f, 45f))
    drawCircle(Color(0xFF1A1410), 1.7f, Offset(55.5f, 45f))
    drawLine(Color(0xFF1A1410).copy(alpha = .55f), Offset(41.5f, 40.5f), Offset(47f, 40f), 1.3f, StrokeCap.Round)
    drawLine(Color(0xFF1A1410).copy(alpha = .55f), Offset(53f, 40f), Offset(58.5f, 40.5f), 1.3f, StrokeCap.Round)
    if (l.beard) {
        drawPath(Path().apply { moveTo(35.5f, 47f); cubicTo(36f, 60f, 43f, 64f, 50f, 64f); cubicTo(57f, 64f, 64f, 60f, 64.5f, 47f); cubicTo(61f, 55f, 56f, 55.5f, 50f, 55.5f); cubicTo(44f, 55.5f, 39f, 55f, 35.5f, 47f); close() }, c(l.hair).copy(alpha = .92f))
        drawLine(c(l.skin), Offset(46.5f, 56.5f), Offset(53.5f, 56.5f), 1.6f, StrokeCap.Round)
    } else {
        drawArc(Color(0xFF7A3B2E), 20f, 140f, false, Offset(45.5f, 50f), Size(9f, 5f), style = Stroke(1.5f, cap = StrokeCap.Round))
    }
    if (l.glasses) {
        drawCircle(Color(0xFF0E1322), 5f, Offset(44.5f, 45f), style = Stroke(1.5f))
        drawCircle(Color(0xFF0E1322), 5f, Offset(55.5f, 45f), style = Stroke(1.5f))
        drawLine(Color(0xFF0E1322), Offset(49.5f, 45f), Offset(50.5f, 45f), 1.5f)
    }
}

/** الصورة الشخصية: شخصية مختارة، أو أول حرف من الاسم إن لم تُختر. */
@Composable
fun Avatar(id: Int?, name: String, size: Dp, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val l = id?.let { LOOKS.getOrNull(it) }
    if (l == null) {
        Box(modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(p.pri, p.pri.copy(alpha = .6f)))), contentAlignment = Alignment.Center) {
            T(name.take(1), size = (size.value * .4f).sp, weight = FontWeight.Bold, color = p.onPri)
        }
        return
    }
    Canvas(modifier.size(size).clip(CircleShape)) { scale(this.size.width / 100f, pivot = Offset.Zero) { look(l) } }
}

/** شبكة اختيار الصورة الشخصية. */
@Composable
fun AvatarPicker(current: Int?, name: String, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    QSheet("الصورة الشخصية", onDismiss) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 4) {
            (listOf<Int?>(null) + (0 until AVATAR_COUNT)).forEach { i ->
                val on = i == current
                Box(
                    Modifier.size(68.dp).clip(CircleShape).border(if (on) 3.dp else 1.dp, if (on) p.pri else p.line, CircleShape)
                        .clickable(role = Role.RadioButton) { onPick(i); onDismiss() },
                    contentAlignment = Alignment.Center,
                ) { Avatar(i, name, 60.dp) }
            }
        }
    }
}
