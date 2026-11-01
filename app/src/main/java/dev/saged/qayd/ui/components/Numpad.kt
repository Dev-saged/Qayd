package dev.saged.qayd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.config.BrandConfig
import dev.saged.qayd.data.AmountInput
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.theme.LocalPalette
import java.util.Locale

/** خيار يظهر فوق المبلغ (فئة مصروف مثلاً). */
data class PadChip(val key: String, val label: String)

/** نمباد المبالغ داخل التطبيق: لا يفتح كيبورد الجوال أبداً. */
@Composable
fun NumpadBody(
    title: String,
    subtitle: String?,
    accent: Color,
    confirm: String,
    initial: String = "",
    chips: List<PadChip> = emptyList(),
    initialChip: String? = null,
    onConfirm: (Double, String?) -> Unit,
) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    var text by rememberSaveable { mutableStateOf(initial) }
    var chip by rememberSaveable { mutableStateOf(initialChip ?: chips.firstOrNull()?.key) }
    val value = AmountInput.value(text)
    val tap = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (subtitle != null) T(subtitle, size = 13.sp, color = p.mute)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.surface2).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T(title, Modifier.weight(1f), 14.sp, FontWeight.Medium, accent)
            T(
                "⁦${display(text)} ${BrandConfig.CURRENCY}⁩",
                size = 34.sp, weight = FontWeight.SemiBold, color = if (value > 0) p.ink else p.mute,
            )
        }

        if (chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { c ->
                    val on = c.key == chip
                    T(
                        c.label,
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) accent.copy(alpha = .18f) else p.surface2)
                            .border(1.dp, if (on) accent else Color.Transparent, RoundedCornerShape(14.dp))
                            .combinedClickable(role = Role.RadioButton, onClick = { tap(); chip = c.key })
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        13.sp, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) accent else p.ink,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrandConfig.QUICK_ADDS.forEach { d ->
                Key("+$d", Modifier.weight(1f), small = true) { tap(); text = AmountInput.add(text, d) }
            }
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("123", "456", "789", ".0<").forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { k ->
                            when (k) {
                                '<' -> Key("مسح", Modifier.weight(1f), icon = QIcons.Backspace, onLong = { tap(); text = "" }) { tap(); text = AmountInput.back(text) }
                                else -> Key(k.toString(), Modifier.weight(1f)) { tap(); text = AmountInput.press(text, k) }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(2.dp))
        QButton(
            confirm,
            {
                if (value > 0) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirm(value, chip)
                }
            },
            secondary = value <= 0,
        )
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, small: Boolean = false, icon: ImageVector? = null, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = if (small) 44.dp else 58.dp)
            .pressScale(source, .92f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (small) p.surface2 else p.bg)
            .border(1.dp, p.line, RoundedCornerShape(16.dp))
            .combinedClickable(source, indication = null, role = Role.Button, onLongClick = onLong, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) QIcon(icon, p.ink, Modifier.size(24.dp), label)
        else T(label, size = if (small) 14.sp else 22.sp, weight = if (small) FontWeight.Medium else FontWeight.SemiBold, color = if (small) p.pri else p.ink)
    }
}

private fun display(text: String): String {
    if (text.isEmpty()) return "0"
    val whole = text.substringBefore('.')
    val grouped = String.format(Locale.US, "%,d", whole.toLongOrNull() ?: 0L)
    return if (text.contains('.')) "$grouped.${text.substringAfter('.')}" else grouped
}

/** النمباد داخل شيت سفلي. */
@Composable
fun NumpadSheet(
    title: String,
    subtitle: String? = null,
    accent: Color,
    confirm: String,
    initial: String = "",
    chips: List<PadChip> = emptyList(),
    initialChip: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (Double, String?) -> Unit,
) {
    QSheet(title, onDismiss) {
        NumpadBody("المبلغ", subtitle, accent, confirm, initial, chips, initialChip) { v, c ->
            onConfirm(v, c)
            onDismiss()
        }
    }
}
