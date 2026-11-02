package dev.saged.qayd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.theme.LocalPalette
import dev.saged.qayd.ui.theme.Readex

/** حقل إدخال بهوية قيد. ltr للأسماء والنطاقات والأرقام. */
@Composable
fun QField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String = "",
    ltr: Boolean = false,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    error: String? = null,
    enabled: Boolean = true,
    onDone: (() -> Unit)? = null,
    suffix: String? = null,
    lines: Int = 1,
) {
    val p = LocalPalette.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    var shown by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val border = when {
        error != null -> p.danger
        focused -> p.pri
        else -> p.line
    }
    Column(modifier.fillMaxWidth()) {
        T(label, Modifier.padding(start = 4.dp, bottom = 6.dp), 12.5.sp, color = p.mute)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(shape)
                .background(p.surface)
                .border(if (focused) 1.5.dp else 1.dp, border, shape)
                .padding(horizontal = 14.dp, vertical = if (lines > 1) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides if (ltr) LayoutDirection.Ltr else LayoutDirection.Rtl) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && hint.isNotEmpty()) T(hint, size = 15.sp, color = p.mute.copy(alpha = .6f))
                        BasicTextField(
                            value = value,
                            onValueChange = onChange,
                            enabled = enabled,
                            singleLine = lines == 1,
                            minLines = lines,
                            maxLines = if (lines == 1) 1 else lines + 6,
                            interactionSource = source,
                            textStyle = TextStyle(fontFamily = Readex, fontSize = 15.sp, color = p.ink),
                            cursorBrush = SolidColor(p.pri),
                            visualTransformation = if (password && !shown) PasswordVisualTransformation() else VisualTransformation.None,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (password) KeyboardType.Password else keyboard,
                                autoCorrectEnabled = false,
                            ),
                            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (suffix != null) T(suffix, Modifier.padding(start = 4.dp), 15.sp, color = p.mute)
                }
            }
            if (password) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { shown = !shown },
                    contentAlignment = Alignment.Center,
                ) { QIcon(if (shown) QIcons.EyeOff else QIcons.Eye, p.mute, Modifier.size(20.dp), if (shown) "إخفاء كلمة السر" else "إظهار كلمة السر") }
            }
        }
        if (error != null) T(error, Modifier.padding(start = 4.dp, top = 6.dp), 12.sp, color = p.danger, maxLines = 2)
    }
}

/** زر رئيسي ذهبي؛ أثناء الانشغال يتحول نصه إلى حالة الانتظار ولا يقبل ضغطاً. */
@Composable
fun QButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, busy: Boolean = false, busyText: String = "لحظة…", secondary: Boolean = false) {
    val p = LocalPalette.current
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    val pulse = if (busy) rememberPulse() else 1f
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .pressScale(source, .97f)
            .clip(shape)
            .then(
                if (secondary) Modifier.background(p.surface).border(1.dp, p.line, shape)
                else Modifier.background(Brush.linearGradient(listOf(p.pri, lerp(p.pri, Color.Black, .3f)))),
            )
            .clickable(source, indication = null, enabled = !busy, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        T(if (busy) busyText else text, size = 15.sp, weight = FontWeight.SemiBold, color = (if (secondary) p.ink else p.onPri).copy(alpha = pulse))
    }
}

@Composable
fun QSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(label, Modifier.weight(1f), 14.5.sp)
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = p.onPri, checkedTrackColor = p.pri,
                uncheckedThumbColor = p.mute, uncheckedTrackColor = p.surface2, uncheckedBorderColor = p.line,
            ),
        )
    }
}

/** عدّاد صغير (−/+) للأعداد كعدد الشاشات. */
@Composable
fun QStepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, Modifier.weight(1f), 14.5.sp)
        listOf(-1 to "−", 1 to "+").forEachIndexed { i, (d, s) ->
            if (i == 1) T(value.toString(), Modifier.width(36.dp).padding(horizontal = 8.dp), 16.sp, FontWeight.SemiBold)
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(p.surface2)
                    .clickable(enabled = (value + d) in range, role = Role.Button) { onChange(value + d) },
                contentAlignment = Alignment.Center,
            ) { T(s, size = 20.sp, weight = FontWeight.SemiBold, color = if ((value + d) in range) p.ink else p.line) }
        }
    }
}

/** شريط رسالة: نجاح أو خطأ. */
@Composable
fun QNotice(text: String, error: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val c = if (error) p.danger else p.ok
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.copy(alpha = .12f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(10.dp))
        T(text, size = 13.5.sp, color = c, maxLines = 4)
    }
}

/** صف مفتاح داخل مجموعة. */
data class SwitchItem(val label: String, val checked: Boolean, val onChange: (Boolean) -> Unit, val sub: String? = null)

/** مجموعة مفاتيح في بطاقة واحدة بفواصل رفيعة بين الصفوف وعنوان واضح. */
@Composable
fun QSwitchGroup(title: String?, rows: List<SwitchItem>, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth().padding(top = 6.dp)) {
        if (title != null) Row(Modifier.padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 3.dp, height = 14.dp).clip(RoundedCornerShape(2.dp)).background(p.pri))
            T(title, Modifier.padding(start = 8.dp), 13.5.sp, FontWeight.SemiBold, p.pri)
            T("${rows.count { it.checked }}/${rows.size}", Modifier.padding(start = 8.dp), 12.sp, color = p.mute)
        }
        val shape = RoundedCornerShape(18.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(p.surface2.copy(alpha = .55f)).border(1.dp, p.line, shape)) {
            rows.forEachIndexed { i, r ->
                if (i > 0) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(p.line))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(role = Role.Switch) { r.onChange(!r.checked) }.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        T(r.label, size = 14.5.sp, weight = if (r.checked) FontWeight.Medium else FontWeight.Normal)
                        r.sub?.let { T(it, Modifier.padding(top = 2.dp), 11.5.sp, color = p.mute, maxLines = 2) }
                    }
                    Switch(
                        checked = r.checked, onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = p.onPri, checkedTrackColor = p.pri,
                            uncheckedThumbColor = p.mute, uncheckedTrackColor = p.surface, uncheckedBorderColor = p.line,
                        ),
                    )
                }
            }
        }
    }
}
