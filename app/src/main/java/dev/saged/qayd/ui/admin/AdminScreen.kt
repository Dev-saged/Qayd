package dev.saged.qayd.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.data.Account
import dev.saged.qayd.data.FEATURES
import dev.saged.qayd.data.Module
import dev.saged.qayd.data.NewShop
import dev.saged.qayd.data.Shop
import dev.saged.qayd.data.SyncState
import dev.saged.qayd.data.Validate
import dev.saged.qayd.ui.Op
import dev.saged.qayd.ui.components.Ambient
import dev.saged.qayd.ui.components.IconTile
import dev.saged.qayd.ui.components.QButton
import dev.saged.qayd.ui.components.QField
import dev.saged.qayd.ui.components.QIcon
import dev.saged.qayd.ui.components.QNotice
import dev.saged.qayd.ui.components.QSheet
import dev.saged.qayd.ui.components.QStepper
import dev.saged.qayd.ui.components.QSwitchRow
import dev.saged.qayd.ui.components.SyncChip
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.components.entrance
import dev.saged.qayd.ui.components.pressScale
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.theme.LocalPalette

/** لوحة مالك المنصة: كل المحلات، إنشاء محل، إيقاف/تفعيل. */
@Composable
fun AdminScreen(
    account: Account,
    shops: List<Shop>,
    sync: SyncState,
    op: Op,
    onCreate: (NewShop) -> Unit,
    onToggle: (Shop, Boolean) -> Unit,
    onClearOp: () -> Unit,
    onSignOut: () -> Unit,
    features: Map<String, String> = emptyMap(),
    onFeature: (String, Boolean) -> Unit = { _, _ -> },
    onSaveShop: (Shop, Set<Module>, Int, Set<String>, Long?, String, Int, String) -> Unit = { _, _, _, _, _, _, _, _ -> },
) {
    val p = LocalPalette.current
    var creating by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Shop?>(null) }
    var featuresOpen by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        Ambient()
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.entrance(0).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("لوحة المالك", size = 22.sp, weight = FontWeight.Bold)
                    T(account.email, size = 12.sp, color = p.mute)
                }
                SyncChip(sync)
                Spacer(Modifier.width(10.dp))
                IconTile(QIcons.Shield, "المميزات المدفوعة", { featuresOpen = true })
                Spacer(Modifier.width(8.dp))
                IconTile(QIcons.Logout, "تسجيل الخروج", onSignOut)
            }

            Row(Modifier.entrance(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("المحلات", shops.size.toString(), Modifier.weight(1f))
                Stat("فعّالة", shops.count { it.blockReason() == null }.toString(), Modifier.weight(1f))
                Stat("موقوفة", shops.count { it.blockReason() != null }.toString(), Modifier.weight(1f))
            }

            T("المحلات", Modifier.entrance(2).padding(top = 4.dp), 14.5.sp, FontWeight.SemiBold)
            if (shops.isEmpty()) {
                Column(
                    Modifier.entrance(3).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.surface).padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    QIcon(QIcons.Shop, p.pri, Modifier.size(34.dp))
                    Spacer(Modifier.height(10.dp))
                    T("لا توجد محلات بعد", size = 16.sp, weight = FontWeight.SemiBold)
                    T("أنشئ أول محل من الزر في الأسفل.", size = 13.sp, color = p.mute)
                }
            } else {
                shops.forEachIndexed { i, s -> ShopCard(s, Modifier.entrance(3 + i.coerceAtMost(6)).clickable(role = Role.Button) { editing = s }) { onToggle(s, it) } }
            }
            Spacer(Modifier.height(110.dp))
        }

        val source = remember { MutableInteractionSource() }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
                .pressScale(source, .95f)
                .shadow(16.dp, RoundedCornerShape(20.dp), ambientColor = p.pri, spotColor = p.pri)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(p.pri, lerp(p.pri, Color.Black, .35f))))
                .clickable(source, indication = null, role = Role.Button) { onClearOp(); creating = true }
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QIcon(QIcons.Plus, p.onPri, Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            T("محل جديد", size = 15.sp, weight = FontWeight.SemiBold, color = p.onPri)
        }
    }

    if (creating) CreateShopSheet(op, onCreate) { creating = false; onClearOp() }
    editing?.let { s -> EditShopSheet(s, features, { m, n, paid, lic, name, quota, type -> onSaveShop(s, m, n, paid, lic, name, quota, type); editing = null }) { editing = null } }
    if (featuresOpen) {
        QSheet("المميزات: مجانية أم مدفوعة", { featuresOpen = false }) {
            T("الميزة المدفوعة تُقفل عند كل محل لم تفعّلها له، ويظهر له زر التواصل معك.", size = 12.5.sp, color = p.mute, maxLines = 2)
            FEATURES.forEach { (k, label) -> QSwitchRow("$label — ${if (features[k] == "paid") "مدفوعة" else "مجانية"}", features[k] == "paid", { onFeature(k, it) }) }
        }
    }
}

@Composable
private fun EditShopSheet(
    s: Shop,
    features: Map<String, String>,
    onSave: (Set<Module>, Int, Set<String>, Long?, String, Int, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    var name by rememberSaveable(s.id) { mutableStateOf(s.name) }
    var mods by rememberSaveable(s.id) { mutableStateOf(s.modules.map { it.key }.toSet()) }
    var screens by rememberSaveable(s.id) { mutableStateOf(s.psScreens) }
    var paid by rememberSaveable(s.id) { mutableStateOf(s.paid) }
    var until by rememberSaveable(s.id) { mutableStateOf(s.licenseUntil) }
    var quota by rememberSaveable(s.id) { mutableStateOf(if (s.smsQuota >= 0) s.smsQuota.toString() else "50") }
    var unlimited by rememberSaveable(s.id) { mutableStateOf(s.smsQuota < 0) }
    var type by rememberSaveable(s.id) { mutableStateOf(s.type) }
    val day = 86_400_000L
    QSheet("تعديل ${s.name}", onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QField(name, { name = it }, "اسم المحل")
            T("نوع النشاط", Modifier.padding(top = 6.dp), 14.sp, FontWeight.SemiBold)
            BizPicker(type) { t -> type = t.key }
            T("الوحدات", Modifier.padding(top = 6.dp), 14.sp, FontWeight.SemiBold)
            Module.entries.forEach { m -> QSwitchRow(m.label, m.key in mods, { on -> mods = if (on) mods + m.key else mods - m.key }) }
            if (Module.Ps.key in mods) QStepper("عدد الشاشات", screens, 1..12) { screens = it }
            if (Module.Sms.key in mods) {
                T("حد الرسائل الشهري", Modifier.padding(top = 6.dp), 14.sp, FontWeight.SemiBold)
                QSwitchRow("غير محدود", unlimited, { unlimited = it })
                if (!unlimited) {
                    QField(quota, { v -> quota = v.filter(Char::isDigit).take(6) }, "عدد الرسائل المسموح في الشهر", hint = "50", ltr = true,
                        error = if (quota.isBlank()) "اكتب رقماً" else null)
                    T("عند انتهائها يظهر للمحل أن يتواصل معك للاشتراك.", size = 12.sp, color = p.mute)
                }
            }
            val paidFeatures = FEATURES.filter { features[it.first] == "paid" }
            if (paidFeatures.isNotEmpty()) {
                T("المميزات المدفوعة المفعّلة لهذا المحل", Modifier.padding(top = 6.dp), 14.sp, FontWeight.SemiBold)
                paidFeatures.forEach { (k, label) -> QSwitchRow(label, k in paid, { on -> paid = if (on) paid + k else paid - k }) }
            }
            T("الاشتراك", Modifier.padding(top = 6.dp), 14.sp, FontWeight.SemiBold)
            T(
                until?.let { "ينتهي ${dev.saged.qayd.ui.util.dayLabel(it)} (${((it - System.currentTimeMillis()) / day).coerceAtLeast(0)} يوم)" } ?: "بدون تاريخ انتهاء",
                size = 13.sp, color = p.mute,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30 to "+شهر", 90 to "+3 أشهر", 365 to "+سنة").forEach { (d, l) ->
                    QButton(l, { until = maxOf(until ?: 0L, System.currentTimeMillis()) + d * day }, Modifier.weight(1f), secondary = true)
                }
            }
            QButton("بدون تاريخ انتهاء", { until = null }, secondary = true)
            Spacer(Modifier.height(6.dp))
            QButton("حفظ", {
                onSave(Module.entries.filter { it.key in mods }.toSet(), screens, paid, until, name.ifBlank { s.name }, if (unlimited) -1 else quota.toIntOrNull() ?: 0, type)
            })
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.clip(shape).background(p.surface).border(1.dp, p.line, shape).padding(horizontal = 14.dp, vertical = 12.dp)) {
        T(label, size = 11.5.sp, color = p.mute)
        T(value, size = 22.sp, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun ShopCard(s: Shop, modifier: Modifier, onToggle: (Boolean) -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(20.dp)
    val blocked = s.blockReason()
    Column(modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, if (blocked != null) p.danger.copy(alpha = .4f) else p.line, shape).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(p.pri, lerp(p.pri, Color.Black, .45f)))),
                contentAlignment = Alignment.Center,
            ) { T(s.name.take(1), size = 17.sp, weight = FontWeight.Bold, color = p.onPri) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                T(s.name, size = 15.5.sp, weight = FontWeight.SemiBold)
                T("⁦@${s.suffix}⁩", size = 12.5.sp, color = p.mute)
            }
        }
        if (s.modules.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.modules.sortedBy { it.ordinal }.forEach { m ->
                    T(
                        when (m) {
                            Module.Ps -> "${m.label} · ${s.psScreens}"
                            Module.Sms -> "${m.label} · ${if (s.smsQuota < 0) "غير محدود" else "${s.smsQuota}/شهر"}"
                            else -> m.label
                        },
                        Modifier.clip(RoundedCornerShape(10.dp)).background(p.surface2).padding(horizontal = 10.dp, vertical = 4.dp),
                        11.5.sp, color = p.ink,
                    )
                }
            }
        }
        QSwitchRow(if (blocked == null) "المحل فعّال" else blocked, s.active, onToggle, Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun CreateShopSheet(op: Op, onCreate: (NewShop) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var suffix by rememberSaveable { mutableStateOf("") }
    var ownerName by rememberSaveable { mutableStateOf("") }
    var ownerUser by rememberSaveable { mutableStateOf("") }
    var ownerPass by rememberSaveable { mutableStateOf("") }
    var debts by rememberSaveable { mutableStateOf(true) }
    var ps by rememberSaveable { mutableStateOf(false) }
    var power by rememberSaveable { mutableStateOf(false) }
    var sms by rememberSaveable { mutableStateOf(true) }
    var inventory by rememberSaveable { mutableStateOf(false) }
    var suppliersOn by rememberSaveable { mutableStateOf(false) }
    var menuOn by rememberSaveable { mutableStateOf(false) }
    var layaway by rememberSaveable { mutableStateOf(false) }
    var type by rememberSaveable { mutableStateOf(dev.saged.qayd.data.BizType.General.key) }
    var screens by rememberSaveable { mutableStateOf(3) }
    var tried by rememberSaveable { mutableStateOf(false) }

    val errName = if (tried && name.isBlank()) "اكتب اسم المحل" else null
    val errSuffix = if (tried) Validate.suffix(suffix) else null
    val errUser = if (tried) Validate.username(ownerUser) else null
    val errPass = if (tried) Validate.password(ownerPass) else null

    QSheet("محل جديد", onDismiss) {
        if (op.done != null) {
            QNotice(op.done, error = false)
            Spacer(Modifier.height(16.dp))
            QButton("تم", onDismiss)
            return@QSheet
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            QField(name, { name = it }, "اسم المحل", hint = "كفي الأنوار", error = errName, enabled = !op.busy)
            QField(suffix, { suffix = it.trim().lowercase() }, "نطاق المحل", hint = "anoar.ps", ltr = true, keyboard = KeyboardType.Uri, error = errSuffix, enabled = !op.busy)
            QField(ownerName, { ownerName = it }, "اسم صاحب المحل", hint = "أحمد", enabled = !op.busy)
            QField(
                ownerUser, { ownerUser = it.trim().lowercase() }, "اسم دخول صاحب المحل", hint = "ahmad", ltr = true,
                error = errUser, enabled = !op.busy, suffix = if (suffix.isNotBlank()) "@$suffix" else null,
            )
            QField(ownerPass, { ownerPass = it }, "كلمة سر صاحب المحل", ltr = true, password = true, error = errPass, enabled = !op.busy)
            T("نوع النشاط", Modifier.padding(top = 6.dp), 14.sp, FontWeight.SemiBold)
            BizPicker(type) { t ->
                type = t.key
                debts = Module.Debts in t.modules; ps = Module.Ps in t.modules; power = Module.Electricity in t.modules
                sms = Module.Sms in t.modules; inventory = Module.Inventory in t.modules; suppliersOn = Module.Suppliers in t.modules
                menuOn = Module.Menu in t.modules; layaway = Module.Layaway in t.modules
            }
            T("الوحدات", Modifier.padding(top = 6.dp), 14.sp, FontWeight.SemiBold)
            QSwitchRow(Module.Debts.label, debts, { debts = it })
            QSwitchRow(Module.Inventory.label, inventory, { inventory = it })
            QSwitchRow(Module.Suppliers.label, suppliersOn, { suppliersOn = it })
            QSwitchRow(Module.Menu.label, menuOn, { menuOn = it })
            QSwitchRow(Module.Layaway.label, layaway, { layaway = it })
            QSwitchRow(Module.Ps.label, ps, { ps = it })
            if (ps) QStepper("عدد الشاشات", screens, 1..12) { screens = it }
            QSwitchRow(Module.Electricity.label, power, { power = it })
            QSwitchRow(Module.Sms.label, sms, { sms = it })
            op.error?.let { QNotice(it, error = true) }
            QButton("إنشاء المحل", {
                tried = true
                if (listOf(errName, Validate.suffix(suffix), Validate.username(ownerUser), Validate.password(ownerPass)).all { it == null } && name.isNotBlank()) {
                    onCreate(
                        NewShop(
                            name, suffix, ownerName.ifBlank { name }, ownerUser, ownerPass,
                            buildSet {
                                if (debts) add(Module.Debts)
                                if (ps) add(Module.Ps)
                                if (power) add(Module.Electricity)
                                if (sms) add(Module.Sms)
                                if (inventory) add(Module.Inventory)
                                if (suppliersOn) add(Module.Suppliers)
                                if (menuOn) add(Module.Menu)
                                if (layaway) add(Module.Layaway)
                            },
                            screens,
                            type,
                        ),
                    )
                }
            }, busy = op.busy, busyText = "جارٍ الإنشاء…")
        }
    }
}

/** اختيار نوع النشاط: اختيار نوع جديد يجهّز وحداته، ويمكن تعديلها بعده. */
@Composable
private fun BizPicker(current: String, onPick: (dev.saged.qayd.data.BizType) -> Unit) {
    val p = LocalPalette.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        dev.saged.qayd.data.BizType.entries.forEach { t ->
            val on = t.key == current
            T(
                t.label,
                Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) p.pri.copy(alpha = .18f) else p.surface2)
                    .border(1.dp, if (on) p.pri else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable(role = Role.RadioButton) { onPick(t) }.padding(horizontal = 12.dp, vertical = 9.dp),
                13.sp, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) p.pri else p.ink,
            )
        }
    }
}
