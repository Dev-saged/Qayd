package dev.saged.qayd.ui.ps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.ui.components.QButton
import dev.saged.qayd.ui.components.QSheet
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.components.rememberNow
import dev.saged.qayd.ui.home.PsSlot
import dev.saged.qayd.ui.theme.LocalPalette
import dev.saged.qayd.ui.util.cur
import dev.saged.qayd.ui.util.ltr
import java.util.Locale

private val DURATIONS = listOf(30 to "نصف ساعة", 60 to "ساعة", 120 to "ساعتان", 180 to "3 ساعات", 0 to "مفتوح")

@Composable
fun PsStartSheet(number: Int, rate: Double, onStart: (Int) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var minutes by rememberSaveable { mutableStateOf(60) }
    QSheet("تشغيل شاشة $number", onDismiss) {
        T(if (rate > 0) "سعر الساعة ${cur(rate)}" else "لم يُحدد سعر الساعة بعد (من المزيد ← الأسعار)", size = 13.sp, color = p.mute)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DURATIONS.forEach { (m, label) ->
                val on = m == minutes
                T(
                    label,
                    Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) p.pri.copy(alpha = .18f) else p.surface2)
                        .border(1.dp, if (on) p.pri else p.line, RoundedCornerShape(14.dp))
                        .clickable(role = Role.RadioButton) { minutes = m }.padding(horizontal = 16.dp, vertical = 11.dp),
                    14.sp, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) p.pri else p.ink,
                )
            }
        }
        if (minutes > 0 && rate > 0) T("التكلفة المتوقعة ${cur(minutes / 60.0 * rate)}", Modifier.padding(top = 12.dp), 13.sp, color = p.mute)
        Spacer(Modifier.height(18.dp))
        QButton("ابدأ الآن", { onStart(minutes); onDismiss() })
    }
}

@Composable
fun PsSessionSheet(
    slot: PsSlot,
    onExtend: () -> Unit,
    onPause: () -> Unit,
    onEndCash: () -> Unit,
    onEndDebt: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    val now = rememberNow().value
    val sec = slot.activeMs(now) / 1000
    QSheet("شاشة ${slot.number}", onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.surface2).padding(18.dp),
        ) {
            T(ltr(String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60)), size = 34.sp, weight = FontWeight.SemiBold)
            T(
                "التكلفة حتى الآن ${cur(slot.cost(now))}" +
                    if (slot.open) " · مفتوح" else " · محجوز ${slot.bookedMinutes} د",
                size = 13.sp, color = p.mute,
            )
            if (slot.pausedAt != null) T("متوقفة مؤقتاً، والوقت لا يُحسب", size = 13.sp, color = p.warn)
            if (slot.byName.isNotBlank()) T("شغّلها ${slot.byName}", size = 12.sp, color = p.mute)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!slot.open) QButton("+30 دقيقة", onExtend, Modifier.weight(1f), secondary = true)
            QButton(if (slot.pausedAt == null) "إيقاف مؤقت" else "استئناف", onPause, Modifier.weight(1f), secondary = true)
        }
        Spacer(Modifier.height(10.dp))
        QButton("إنهاء ودفع ${cur(slot.cost(now))}", { onEndCash(); onDismiss() })
        Spacer(Modifier.height(10.dp))
        QButton("إنهاء وتسجيلها ديناً على زبون", { onEndDebt(); onDismiss() }, secondary = true)
    }
}
