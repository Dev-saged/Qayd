package dev.saged.qayd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.data.SyncState
import dev.saged.qayd.ui.theme.LocalPalette

/** شارة حالة المزامنة. */
@Composable
fun SyncChip(state: SyncState, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val c = when (state) {
        SyncState.Synced -> p.ok
        SyncState.Pending -> p.warn
        SyncState.Offline -> p.mute
    }
    val pulse = if (state == SyncState.Pending) rememberPulse() else 1f
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = .12f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(c.copy(alpha = pulse)))
        Spacer(Modifier.width(6.dp))
        T(state.label, size = 11.5.sp, color = c)
    }
}

/** زر أيقونة مربع بحدود. */
@Composable
fun IconTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.size(44.dp).clip(shape).background(p.surface).border(1.dp, p.line, shape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { QIcon(icon, p.ink, Modifier.size(21.dp), label) }
}

/** صف إعداد قابل للضغط. */
@Composable
fun ActionRow(icon: ImageVector, title: String, sub: String? = null, danger: Boolean = false, locked: Boolean = false, badge: String? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    val c = if (danger) p.danger else p.ink
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(p.surface2), contentAlignment = Alignment.Center) {
            QIcon(icon, if (danger) p.danger else p.pri, Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(title, size = 14.5.sp, weight = FontWeight.Medium, color = c)
                if (locked) QIcon(dev.saged.qayd.ui.icons.QIcons.Lock, p.pri, Modifier.padding(start = 6.dp).size(14.dp), "مدفوعة")
                if (badge != null) T(badge, Modifier.padding(start = 8.dp).clip(RoundedCornerShape(8.dp)).background(p.pri).padding(horizontal = 7.dp, vertical = 1.dp), 10.5.sp, FontWeight.SemiBold, p.onPri)
            }
            if (sub != null) T(sub, Modifier.padding(top = 4.dp), 12.sp, color = p.mute, maxLines = 2)
        }
    }
}

/** شيت سفلي بهوية قيد مع تمرير ورفع فوق لوحة المفاتيح. */
@Composable
fun QSheet(title: String, onDismiss: () -> Unit, footer: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.surface,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(p.line))
        },
    ) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                T(title, Modifier.padding(vertical = 8.dp), 19.sp, FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                content()
                Spacer(Modifier.height(if (footer == null) 24.dp else 12.dp))
            }
            // زر الحفظ ثابت أسفل اللوحة فوق خط فاصل
            footer?.let {
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
                Box(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) { it() }
            }
        }
    }
}

/** ميزة مدفوعة غير مفعّلة لهذا المحل: توضيح وزر تواصل مع مالك المنصة. */
@Composable
fun LockedCard(
    feature: String,
    modifier: Modifier = Modifier,
    title: String = "$feature — ميزة مدفوعة",
    body: String = "تواصل مع المالك لتفعيلها لمحلك.",
) {
    val p = LocalPalette.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, p.pri.copy(alpha = .35f), shape).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QIcon(dev.saged.qayd.ui.icons.QIcons.Lock, p.pri, Modifier.size(20.dp))
            T(title, Modifier.padding(start = 8.dp), 14.5.sp, FontWeight.SemiBold)
        }
        T(body, Modifier.padding(top = 6.dp), 12.5.sp, color = p.mute, maxLines = 3)
        Spacer(Modifier.height(10.dp))
        QButton("تواصل عبر واتساب", {
            runCatching {
                ctx.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://wa.me/${dev.saged.qayd.config.BrandConfig.OWNER_WHATSAPP}?text=" + android.net.Uri.encode("أريد تفعيل ميزة $feature في قيد")),
                    ),
                )
            }
        }, secondary = true)
    }
}

/** نافذة الميزة المدفوعة: شرح مختصر وزر واتساب برسالة جاهزة. */
@Composable
fun PaidSheet(feature: String, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    QSheet("ميزة مدفوعة", onDismiss) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(p.pri.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                QIcon(dev.saged.qayd.ui.icons.QIcons.Lock, p.pri, Modifier.size(30.dp))
            }
            T(feature, Modifier.padding(top = 14.dp), 18.sp, FontWeight.SemiBold)
            T("هذه الميزة غير مفعّلة لمحلك. تواصل مع مسؤول التطبيق لتفعيلها.", Modifier.padding(top = 6.dp), 13.5.sp, color = p.mute, maxLines = 3)
            Spacer(Modifier.height(18.dp))
            QButton("تواصل عبر واتساب", {
                runCatching {
                    ctx.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://wa.me/${dev.saged.qayd.config.BrandConfig.OWNER_WHATSAPP}?text=" + android.net.Uri.encode("أريد تفعيل ميزة $feature في قيد")),
                        ),
                    )
                }
                onDismiss()
            })
        }
    }
}
