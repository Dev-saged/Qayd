package dev.saged.qayd.ui.more

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.BuildConfig
import dev.saged.qayd.config.BrandConfig
import dev.saged.qayd.ui.components.QIcon
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.theme.LocalPalette

private fun iconOf(key: String): ImageVector = when (key) {
    "whatsapp" -> QIcons.WhatsApp
    "telegram" -> QIcons.Telegram
    "instagram" -> QIcons.Instagram
    "facebook" -> QIcons.Facebook
    "x" -> QIcons.X
    else -> QIcons.GitHub
}

private val LABELS = mapOf("whatsapp" to "واتساب", "telegram" to "تلجرام", "instagram" to "إنستغرام", "facebook" to "فيسبوك", "x" to "إكس", "github" to "GitHub")

/** بطاقة المصمم: الاسم، روابط التواصل، وصُنع في غزة. */
@Composable
fun DevCard(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(24.dp)
    val d = BrandConfig.Dev
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(p.surface, p.surface2)))
            .border(1.dp, Brush.verticalGradient(listOf(p.pri.copy(alpha = .45f), p.line)), shape)
            .padding(horizontal = 14.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        T("تصميم وتطوير", size = 12.sp, color = p.mute)
        T(d.TITLE, Modifier.padding(top = 2.dp), 18.sp, FontWeight.Bold, p.pri)
        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(56.dp).height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, p.pri.copy(alpha = .6f)))))
            Canvas(Modifier.padding(horizontal = 8.dp).size(8.dp)) {
                drawPath(Path().apply { moveTo(size.width / 2, 0f); lineTo(size.width, size.height / 2); lineTo(size.width / 2, size.height); lineTo(0f, size.height / 2); close() }, p.pri)
            }
            Box(Modifier.width(56.dp).height(1.dp).background(Brush.horizontalGradient(listOf(p.pri.copy(alpha = .6f), Color.Transparent))))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            d.LINKS.forEach { (k, url) ->
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(p.bg.copy(alpha = .55f)).border(1.dp, p.line, RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button, onClickLabel = LABELS[k]) {
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        },
                    contentAlignment = Alignment.Center,
                ) { QIcon(iconOf(k), p.ink, Modifier.size(21.dp), LABELS[k]) }
            }
        }
        Row(
            Modifier.padding(top = 14.dp).clip(CircleShape).background(p.bg.copy(alpha = .55f)).border(1.dp, p.line, CircleShape)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Flag(Modifier.size(width = 24.dp, height = 15.dp).clip(RoundedCornerShape(3.dp)))
            T(d.PLACE, Modifier.padding(horizontal = 8.dp), 12.5.sp, FontWeight.SemiBold)
            QIcon(QIcons.Heart, p.danger, Modifier.size(15.dp))
        }
        T("© ${d.YEAR} ${d.NAME} — جميع الحقوق محفوظة · ${BrandConfig.NAME} ⁦v${BuildConfig.VERSION_NAME}⁩", Modifier.padding(top = 12.dp), 11.sp, color = p.mute, maxLines = 2)
    }
}

/** علم فلسطين مرسوماً: ثلاثة أشرطة ومثلث أحمر عند السارية (يسار العلم). */
@Composable
private fun Flag(modifier: Modifier) = Canvas(modifier) {
    val h = size.height / 3
    drawRect(Color(0xFF111111), Offset.Zero, Size(size.width, h))
    drawRect(Color.White, Offset(0f, h), Size(size.width, h))
    drawRect(Color(0xFF149954), Offset(0f, 2 * h), Size(size.width, h))
    drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width * .42f, size.height / 2); lineTo(0f, size.height); close() }, Color(0xFFE4312B))
}
