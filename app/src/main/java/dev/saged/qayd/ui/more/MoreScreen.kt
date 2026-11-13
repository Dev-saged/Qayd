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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.foundation.clickable
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
    autoBackup: Boolean? = null,
    onAvatar: (Int?) -> Unit = {},
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val p = LocalPalette.current
    var sheet by rememberSaveable { mutableStateOf<MoreSheet?>(null) }
    var paidFeature by rememberSaveable { mutableStateOf<String?>(null) }
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
            Box(Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) { picking = true }) {
                dev.saged.qayd.ui.components.Avatar(account.avatar, account.displayName, 56.dp)
                Box(Modifier.align(Alignment.BottomEnd).size(20.dp).clip(RoundedCornerShape(10.dp)).background(p.pri), contentAlignment = Alignment.Center) {
                    dev.saged.qayd.ui.components.QIcon(QIcons.Edit, p.onPri, Modifier.size(12.dp))
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                T(account.displayName, size = 16.sp, weight = FontWeight.SemiBold)
                T("${account.role.label} · ${shop.name}", size = 12.5.sp, color = p.mute, maxLines = 2)
                T("⁦${account.email}⁩", size = 12.sp, color = p.mute)
                SyncChip(sync, Modifier.padding(top = 8.dp))
            }
        }

        Column(Modifier.entrance(2).fillMaxWidth().clip(shape).background(p.surface).border(1.dp, p.line, shape).padding(6.dp)) {
            nav.reports?.let { ActionRow(QIcons.Chart, "التقارير", "مبيعات الأسبوع، صافي الشهر، أكبر المدينين، وأداء الفريق", onClick = it) }
            nav.orders?.let { ActionRow(QIcons.Bowl, "الطلبات وقائمة الطعام", "طلب جديد، حالة الطلبات، الطباعة، وأصناف القائمة", onClick = it) }
            nav.voice?.let { ActionRow(QIcons.Mic, "صندوق الصوت", "تسجيلات بانتظار المراجعة والتأكيد", badge = nav.voiceCount.takeIf { n -> n > 0 }?.toString(), onClick = it) }
            nav.transfers?.let { ActionRow(QIcons.Wallet, "التحويلات", "تأكيد وصول المبالغ المحوّلة بالمحافظ والبنوك", badge = nav.pendingTransfers.takeIf { n -> n > 0 }?.toString(), onClick = it) }
            nav.cashClose?.let { ActionRow(QIcons.Receipt, "إغلاق الصندوق", "عدّ الدرج آخر اليوم ومطابقته مع نقد حركاتك", onClick = it) }
            nav.batteries?.let { ActionRow(QIcons.Battery, "بطاريات السيارات", "الرقم التسلسلي والكفالة، الاستبدال، وخردة القديمة", onClick = it) }
            nav.holds?.let { ActionRow(QIcons.Calendar, "العربون والحجز", "زبون يحجز بعربون ويستلم عند الإكمال", onClick = it) }
            nav.stock?.let { ActionRow(QIcons.Box, "الأصناف والمخزون", "الأسعار والكميات، الباركود، وتنبيه النفاد", onClick = it) }
            nav.suppliers?.let { ActionRow(QIcons.Truck, "الموردون", "ما علينا لكل تاجر، فواتير الشراء والدفعات", onClick = it) }
            nav.lockedModules.forEach { m -> ActionRow(QIcons.Box, m, "ميزة مدفوعة لمحلك", locked = true) { paidFeature = m } }
            if (nav.reportsLocked) ActionRow(QIcons.Chart, "التقارير", "مبيعات الأسبوع، صافي الشهر، أكبر المدينين، وأداء الفريق", locked = true) { paidFeature = "التقارير" }
            nav.power?.let { ActionRow(QIcons.Bolt, "الكهرباء", "المشتركون وجولة القراءات وفواتير الشهر", onClick = it) }
            nav.expenses?.let { ActionRow(QIcons.Wallet, "المصروفات", "بضاعة، إنترنت، كهرباء، إيجار، ودفعات شهرية ثابتة", onClick = it) }
            nav.team?.let { ActionRow(QIcons.Users, "الفريق والصلاحيات", "وردية اليوم لكل عضو، وصلاحياته، وإيقافه", onClick = it) }
            nav.prices?.let { ActionRow(QIcons.Edit, "الأسعار", "سعر ساعة الشاشة وكيلو الكهرباء", onClick = it) }
            nav.audit?.let { ActionRow(QIcons.Receipt, "سجل التدقيق", "كل حذف أو تغيير صلاحيات: من ومتى", onClick = it) }
            if (owner) {
                ActionRow(QIcons.Edit, "اسم المحل", shop.name) { onClearOp(); sheet = MoreSheet.Rename }
                ActionRow(QIcons.UserPlus, "إضافة موظف", "حساب دخول جديد على نطاق ⁦@${shop.suffix}⁩") { onClearOp(); sheet = MoreSheet.Staff }
            }
            nav.backup?.let { ActionRow(QIcons.Shield, "نسخة احتياطية", "تُحفظ تلقائياً في ${dev.saged.qayd.system.QaydFiles.label(dev.saged.qayd.system.QaydFiles.BACKUPS)} ويمكنك إرسالها", onClick = it) }
            autoBackup?.let { AutoBackupRow(it) }
            ThemeRow()
            val update by dev.saged.qayd.system.Updates.latest.collectAsState()
            ActionRow(QIcons.Sync, "التحديثات", "النسخة ⁦${dev.saged.qayd.system.Updates.current}⁩ وسجل كل الإصدارات ومميزاتها", badge = update?.let { "جديد" }) {
                dev.saged.qayd.system.Updates.pageOpen.value = true
            }
            ActionRow(QIcons.Shield, "الصلاحيات", "الإشعارات، الرسائل، العمل في الخلفية، التحديثات") { dev.saged.qayd.system.Perms.open.value = true }
            LockRow()
            ActionRow(QIcons.Logout, "تسجيل الخروج", danger = true, onClick = onSignOut)
        }

        DevCard(Modifier.entrance(3))
        Spacer(Modifier.height(36.dp))
    }

    if (picking) dev.saged.qayd.ui.components.AvatarPicker(account.avatar, account.displayName, onAvatar) { picking = false }
    paidFeature?.let { dev.saged.qayd.ui.components.PaidSheet(it) { paidFeature = null } }
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

@Composable
private fun LockRow() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    if (!dev.saged.qayd.system.AppLock.available(ctx)) return
    var on by androidx.compose.runtime.remember { mutableStateOf(dev.saged.qayd.system.AppLock.enabled(ctx)) }
    dev.saged.qayd.ui.components.QSwitchRow("قفل التطبيق بالبصمة", on, {
        on = it
        dev.saged.qayd.system.AppLock.setEnabled(ctx, it)
    }, Modifier.padding(horizontal = 6.dp))
}

@Composable
private fun ThemeRow() {
    val p = LocalPalette.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cur = dev.saged.qayd.system.ThemePref.mode.value
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        dev.saged.qayd.ui.components.QIcon(QIcons.Sun, p.ink, Modifier.size(22.dp))
        T("المظهر", Modifier.weight(1f).padding(horizontal = 12.dp), 15.sp, FontWeight.Medium)
        Row(Modifier.clip(RoundedCornerShape(14.dp)).background(p.surface2).padding(3.dp)) {
            dev.saged.qayd.system.ThemePref.Mode.entries.forEach { m ->
                val on = m == cur
                T(
                    m.label,
                    Modifier.clip(RoundedCornerShape(11.dp)).background(if (on) p.pri else Color.Transparent)
                        .clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { dev.saged.qayd.system.ThemePref.set(ctx, m) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    12.5.sp, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) p.onPri else p.ink,
                )
            }
        }
    }
}

@Composable
private fun AutoBackupRow(unlocked: Boolean) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    if (!unlocked) {
        var paid by androidx.compose.runtime.remember { mutableStateOf(false) }
        ActionRow(QIcons.Shield, "النسخ الاحتياطي التلقائي", "نسخة يومية تلقائية في مجلد قيد", locked = true) { paid = true }
        if (paid) dev.saged.qayd.ui.components.PaidSheet("النسخ الاحتياطي التلقائي") { paid = false }
        return
    }
    var on by androidx.compose.runtime.remember { mutableStateOf(dev.saged.qayd.system.AutoBackup.enabled(ctx)) }
    val last = dev.saged.qayd.system.AutoBackup.lastAt(ctx)
    Column(Modifier.padding(horizontal = 6.dp)) {
        dev.saged.qayd.ui.components.QSwitchRow("نسخ احتياطي تلقائي يومي", on, {
            on = it
            dev.saged.qayd.system.AutoBackup.setEnabled(ctx, it)
        })
        if (on) T(
            if (last > 0) "آخر نسخة ${dev.saged.qayd.ui.util.dayLabel(last)} ${dev.saged.qayd.ui.util.clock(last)} · في ${dev.saged.qayd.system.QaydFiles.label(dev.saged.qayd.system.QaydFiles.BACKUPS)}" else "أول نسخة عند فتح التطبيق القادم · في ${dev.saged.qayd.system.QaydFiles.label(dev.saged.qayd.system.QaydFiles.BACKUPS)}",
            Modifier.padding(start = 6.dp, bottom = 8.dp), 12.sp, color = LocalPalette.current.mute,
        )
    }
}
