package dev.saged.qayd.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.BuildConfig
import dev.saged.qayd.config.BrandConfig
import dev.saged.qayd.data.Account
import dev.saged.qayd.data.Role
import dev.saged.qayd.data.Shop
import dev.saged.qayd.data.SyncState
import dev.saged.qayd.data.Validate
import dev.saged.qayd.ui.Op
import dev.saged.qayd.ui.components.ActionRow
import dev.saged.qayd.ui.shell.MoreNav
import dev.saged.qayd.ui.components.QButton
import dev.saged.qayd.ui.components.QField
import dev.saged.qayd.ui.components.QNotice
import dev.saged.qayd.ui.components.QSheet
import dev.saged.qayd.ui.components.SyncChip
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.components.entrance
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.theme.LocalPalette

private enum class MoreSheet { Rename, Staff }

@Composable
fun MoreScreen(
    padding: PaddingValues,
    shop: Shop,
    account: Account,
    sync: SyncState,
    op: Op,
    onRename: (String) -> Unit,
    onAddStaff: (String, String, String) -> Unit,
    onClearOp: () -> Unit,
    onSignOut: () -> Unit,
    nav: MoreNav = MoreNav(null, null, null),
) {
    val p = LocalPalette.current
    var sheet by rememberSaveable { mutableStateOf<MoreSheet?>(null) }
    val owner = account.role == Role.Owner

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        T("المزيد", Modifier.entrance(0).padding(top = 10.dp), 22.sp, FontWeight.Bold)

        val shape = RoundedCornerShape(22.dp)
        Row(
            Modifier.entrance(1).fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(p.surface, p.surface2)))
                .border(1.dp, p.line, shape).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(p.pri, lerp(p.pri, Color.Black, .45f)))),
                contentAlignment = Alignment.Center,
            ) { T(account.displayName.take(1), size = 20.sp, weight = FontWeight.Bold, color = p.onPri) }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                T(account.displayName, size = 16.sp, weight = FontWeight.SemiBold)
                T("${account.role.label} · ${shop.name}", size = 12.5.sp, color = p.mute, maxLines = 2)
                T("⁦${account.email}⁩", size = 12.sp, color = p.mute)
                SyncChip(sync, Modifier.padding(top = 8.dp))
            }
        }

        Column(Modifier.entrance(2).fillMaxWidth().clip(shape).background(p.surface).border(1.dp, p.line, shape).padding(6.dp)) {
            nav.expenses?.let { ActionRow(QIcons.Wallet, "المصروفات", "بضاعة، إنترنت، كهرباء، إيجار، ودفعات شهرية ثابتة", onClick = it) }
            nav.team?.let { ActionRow(QIcons.Users, "الفريق والصلاحيات", "وردية اليوم لكل عضو، وصلاحياته، وإيقافه", onClick = it) }
            nav.audit?.let { ActionRow(QIcons.Receipt, "سجل التدقيق", "كل حذف أو تغيير صلاحيات: من ومتى", onClick = it) }
            if (owner) {
                ActionRow(QIcons.Edit, "اسم المحل", shop.name) { onClearOp(); sheet = MoreSheet.Rename }
                ActionRow(QIcons.UserPlus, "إضافة موظف", "حساب دخول جديد على نطاق ⁦@${shop.suffix}⁩") { onClearOp(); sheet = MoreSheet.Staff }
            }
            ActionRow(QIcons.Logout, "تسجيل الخروج", danger = true, onClick = onSignOut)
        }

        T("${BrandConfig.NAME} ${BuildConfig.VERSION_NAME}", Modifier.entrance(3).fillMaxWidth().padding(top = 6.dp), 12.sp, color = p.mute)
        Spacer(Modifier.height(96.dp))
    }

    when (sheet) {
        MoreSheet.Rename -> RenameSheet(shop.name, onRename) { sheet = null }
        MoreSheet.Staff -> StaffSheet(shop.suffix, op, onAddStaff) { sheet = null; onClearOp() }
        null -> Unit
    }
}

@Composable
private fun RenameSheet(current: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    QSheet("اسم المحل", onDismiss) {
        QField(name, { name = it }, "الاسم الظاهر للموظفين وفي الفواتير", error = if (name.isBlank()) "اكتب اسم المحل" else null)
        Spacer(Modifier.height(16.dp))
        QButton("حفظ", { if (name.isNotBlank()) { onRename(name); onDismiss() } })
    }
}

@Composable
private fun StaffSheet(suffix: String, op: Op, onAdd: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var user by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    QSheet("إضافة موظف", onDismiss) {
        if (op.done != null) {
            QNotice(op.done, error = false)
            Spacer(Modifier.height(16.dp))
            QButton("تم", onDismiss)
            return@QSheet
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            QField(name, { name = it }, "اسم الموظف", hint = "محمد", enabled = !op.busy)
            QField(user, { user = it.trim().lowercase() }, "اسم الدخول", hint = "mohammad", ltr = true, suffix = "@$suffix",
                error = if (tried) Validate.username(user) else null, enabled = !op.busy)
            QField(pass, { pass = it }, "كلمة السر", ltr = true, password = true,
                error = if (tried) Validate.password(pass) else null, enabled = !op.busy)
            T("صلاحيات الموظف الافتراضية: تسجيل الديون والدفعات وتشغيل الشاشات وإرسال الرسائل. التحكم الكامل بالصلاحيات يصل في المرحلة القادمة.",
                size = 12.sp, color = LocalPalette.current.mute, maxLines = 3)
            op.error?.let { QNotice(it, error = true) }
            QButton("إضافة", {
                tried = true
                if (Validate.username(user) == null && Validate.password(pass) == null) onAdd(user, name.ifBlank { user }, pass)
            }, busy = op.busy, busyText = "جارٍ الإضافة…")
        }
    }
}
