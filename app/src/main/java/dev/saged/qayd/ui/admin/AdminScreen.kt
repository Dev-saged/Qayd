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
    presence: Map<String, dev.saged.qayd.data.Presence> = emptyMap(),
    plans: List<dev.saged.qayd.data.Plan> = emptyList(),
    payments: List<dev.saged.qayd.data.Payment> = emptyList(),
    broadcast: dev.saged.qayd.data.Broadcast? = null,
    onSavePlans: (List<dev.saged.qayd.data.Plan>) -> Unit = {},
    onApplyPlan: (Shop, dev.saged.qayd.data.Plan, Long?) -> Unit = { _, _, _ -> },
    onPayment: (Shop, Double, Int, String, String) -> Unit = { _, _, _, _, _ -> },
    onBroadcast: (String, Boolean) -> Unit = { _, _ -> },
    onSupport: (Shop) -> Unit = {},
) {
    val p = LocalPalette.current
    var creating by rememberSaveable { mutableStateOf(false) }
    var plansOpen by rememberSaveable { mutableStateOf(false) }
    var broadcastOpen by rememberSaveable { mutableStateOf(false) }
    val day = 86_400_000L
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
            val monthStart = java.util.Calendar.getInstance().apply { set(java.util.Calendar.DAY_OF_MONTH, 1); set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0) }.timeInMillis
            Row(Modifier.entrance(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("دخل الاشتراكات هذا الشهر", dev.saged.qayd.ui.util.cur(payments.filter { it.at >= monthStart }.sumOf { it.amount }), Modifier.weight(1.6f))
                Stat("نشطة اليوم", presence.values.count { System.currentTimeMillis() - it.lastSeen < day }.toString(), Modifier.weight(1f))
            }

            val soon = shops.filter { s -> s.active && (s.daysLeft()?.let { it <= 7 } ?: false) }.sortedBy { it.licenseUntil }
            if (soon.isNotEmpty()) Column(
                Modifier.entrance(2).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.warn.copy(alpha = .1f)).border(1.dp, p.warn.copy(alpha = .4f), RoundedCornerShape(20.dp)).padding(14.dp),
            ) {
                T("اشتراكات تنتهي قريباً", size = 14.5.sp, weight = FontWeight.SemiBold, color = p.warn)
                soon.forEach { s ->
                    val d = s.daysLeft() ?: 0
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            T(s.name, size = 14.sp, weight = FontWeight.Medium)
                            T(if (d < 0) "انتهى منذ ${-d} يوم" else if (d == 0) "ينتهي اليوم" else "باقٍ $d يوم", size = 12.sp, color = if (d < 0) p.danger else p.mute)
                        }
                        T("تجديد", Modifier.clip(RoundedCornerShape(10.dp)).background(p.pri.copy(alpha = .16f)).clickable(role = Role.Button) { editing = s }
                            .padding(horizontal = 14.dp, vertical = 8.dp), 13.sp, FontWeight.SemiBold, p.pri)
                    }
                }
            }

            Column(Modifier.entrance(2).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(20.dp)).padding(6.dp)) {
                dev.saged.qayd.ui.components.ActionRow(QIcons.Box, "الباقات", "${plans.size} باقة · تفعيل مجموعة مميزات بضغطة") { plansOpen = true }
                dev.saged.qayd.ui.components.ActionRow(QIcons.Lock, "المميزات المدفوعة", "حدد ما هو مجاني وما هو مدفوع") { featuresOpen = true }
                dev.saged.qayd.ui.components.ActionRow(QIcons.Msg, "رسالة لكل المحلات", if (broadcast?.active == true) "منشورة: ${broadcast.text}" else "إعلان يظهر في الرئيسية عند الجميع") { broadcastOpen = true }
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
                shops.forEachIndexed { i, s -> ShopCard(s, presence[s.id], Modifier.entrance(3 + i.coerceAtMost(6)).clickable(role = Role.Button) { editing = s }) { onToggle(s, it) } }
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
    editing?.let { s ->
        EditShopSheet(
            s, features, { m, n, paid, lic, name, quota, type -> onSaveShop(s, m, n, paid, lic, name, quota, type); editing = null },
            plans = plans, payments = payments.filter { it.shopId == s.id },
            onApplyPlan = { pl, until -> onApplyPlan(s, pl, until); editing = null },
            onPayment = { a, months, note -> onPayment(s, a, months, s.plan, note); editing = null },
            onSupport = { onSupport(s); editing = null },
        ) { editing = null }
    }
    if (plansOpen) PlansSheet(plans, onSavePlans) { plansOpen = false }
    if (broadcastOpen) BroadcastSheet(broadcast, onBroadcast) { broadcastOpen = false }
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
    plans: List<dev.saged.qayd.data.Plan> = emptyList(),
    payments: List<dev.saged.qayd.data.Payment> = emptyList(),
    onApplyPlan: (dev.saged.qayd.data.Plan, Long?) -> Unit = { _, _ -> },
    onPayment: (Double, Int, String) -> Unit = { _, _, _ -> },
    onSupport: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    var paying by remember { mutableStateOf(false) }
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
            if (plans.isNotEmpty()) {
                T("تطبيق باقة", Modifier.padding(top = 6.dp), 14.sp, FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    plans.forEach { pl ->
                        val on = pl.id == s.plan
                        T("${pl.name} · ${dev.saged.qayd.ui.util.cur(pl.price)}", Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) p.pri.copy(alpha = .18f) else p.surface2)
                            .clickable(role = Role.Button) { onApplyPlan(pl, until) }.padding(horizontal = 12.dp, vertical = 9.dp), 13.sp, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) p.pri else p.ink)
                    }
                }
                T("تطبيق الباقة يفعّل مميزاتها ويقفل غيرها لهذا المحل، مع تاريخ الاشتراك المحدد أعلاه.", size = 12.sp, color = p.mute, maxLines = 2)
            }
            T("الدفعات", Modifier.padding(top = 6.dp), 14.sp, FontWeight.SemiBold)
            payments.take(6).forEach { pay ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    T("${dev.saged.qayd.ui.util.dayLabel(pay.at)} · ${pay.months} شهر${if (pay.note.isNotBlank()) " · ${pay.note}" else ""}", Modifier.weight(1f), 12.5.sp, color = p.mute)
                    T(dev.saged.qayd.ui.util.cur(pay.amount), size = 13.sp, weight = FontWeight.SemiBold)
                }
            }
            QButton("تسجيل دفعة وتمديد الاشتراك", { paying = true }, secondary = true)
            QButton("عرض المحل للدعم (قراءة فقط)", onSupport, secondary = true)
            Spacer(Modifier.height(6.dp))
            if (paying) PaymentSheet(onPayment) { paying = false }
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
private fun ShopCard(s: Shop, pr: dev.saged.qayd.data.Presence?, modifier: Modifier, onToggle: (Boolean) -> Unit) {
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
                T("⁦@${s.suffix}⁩ · ${dev.saged.qayd.data.BizType.of(s.type).label}", size = 12.5.sp, color = p.mute)
            }
        }
        T(
            pr?.let { "آخر نشاط ${dev.saged.qayd.ui.util.dayLabel(it.lastSeen)} ${dev.saged.qayd.ui.util.clock(it.lastSeen)} · ⁦v${it.version}⁩ · ${it.members} حساب · ${it.customers} زبون · ${it.smsUsed} رسالة هذا الشهر" }
                ?: "لم يُفتح التطبيق بعد",
            Modifier.padding(top = 10.dp), 12.sp, color = p.mute, maxLines = 2,
        )
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

@Composable
private fun PaymentSheet(onPay: (Double, Int, String) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var amount by rememberSaveable { mutableStateOf("") }
    var months by rememberSaveable { mutableStateOf(1) }
    var note by rememberSaveable { mutableStateOf("") }
    QSheet("دفعة اشتراك", onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            QField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, "المبلغ المدفوع", ltr = true, keyboard = KeyboardType.Decimal)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "شهر", 3 to "3 أشهر", 6 to "6 أشهر", 12 to "سنة").forEach { (m, l) ->
                    val on = m == months
                    T(l, Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) p.pri.copy(alpha = .18f) else p.surface2)
                        .clickable(role = Role.RadioButton) { months = m }.padding(vertical = 10.dp), 13.sp, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) p.pri else p.ink)
                }
            }
            QField(note, { note = it }, "ملاحظة (اختياري)", hint = "نقداً، تحويل")
            QButton("تسجيل وتمديد $months شهر", { amount.toDoubleOrNull()?.let { onPay(it, months, note); onDismiss() } })
        }
    }
}

@Composable
private fun PlansSheet(plans: List<dev.saged.qayd.data.Plan>, onSave: (List<dev.saged.qayd.data.Plan>) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var editing by remember { mutableStateOf<dev.saged.qayd.data.Plan?>(null) }
    QSheet("الباقات", onDismiss) {
        val e = editing
        if (e == null) {
            plans.forEach { pl ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(p.surface2).clickable(role = Role.Button) { editing = pl }.padding(14.dp)) {
                    Row {
                        T(pl.name, Modifier.weight(1f), 15.sp, FontWeight.SemiBold)
                        T("${dev.saged.qayd.ui.util.cur(pl.price)} / شهر", size = 13.5.sp, color = p.pri)
                    }
                    T(FEATURES.filter { it.first in pl.features }.joinToString("، ") { it.second }.ifBlank { "بدون مميزات مدفوعة" }, Modifier.padding(top = 4.dp), 12.sp, color = p.mute, maxLines = 3)
                }
            }
            if (plans.isEmpty()) T("أنشئ باقات مثل: أساسية، متقدمة، كاملة.", size = 13.sp, color = p.mute)
            Spacer(Modifier.height(10.dp))
            QButton("باقة جديدة", { editing = dev.saged.qayd.data.Plan("", "", 0.0, emptySet()) })
        } else {
            var name by remember(e) { mutableStateOf(e.name) }
            var price by remember(e) { mutableStateOf(if (e.price > 0) dev.saged.qayd.ui.util.money(e.price) else "") }
            var feats by remember(e) { mutableStateOf(e.features) }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QField(name, { name = it }, "اسم الباقة", hint = "المتقدمة")
                QField(price, { price = it.filter { c -> c.isDigit() || c == '.' } }, "السعر الشهري", ltr = true, keyboard = KeyboardType.Decimal)
                FEATURES.forEach { (k, l) -> QSwitchRow(l, k in feats, { on -> feats = if (on) feats + k else feats - k }) }
                QButton("حفظ الباقة", {
                    if (name.isNotBlank()) {
                        val pl = e.copy(id = e.id.ifBlank { "p" + System.currentTimeMillis().toString(36) }, name = name.trim(), price = price.toDoubleOrNull() ?: 0.0, features = feats)
                        onSave(plans.filter { it.id != pl.id } + pl)
                        editing = null
                    }
                })
                if (e.id.isNotBlank()) QButton("حذف الباقة", { onSave(plans.filter { it.id != e.id }); editing = null }, secondary = true)
                QButton("رجوع", { editing = null }, secondary = true)
            }
        }
    }
}

@Composable
private fun BroadcastSheet(b: dev.saged.qayd.data.Broadcast?, onSave: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(b?.text.orEmpty()) }
    QSheet("رسالة لكل المحلات", onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            QField(text, { text = it }, "نص الرسالة", hint = "تحديث جديد متاح، أو عرض خاص هذا الشهر…", lines = 4)
            QButton("نشر لكل المحلات", { if (text.isNotBlank()) { onSave(text, true); onDismiss() } })
            if (b?.active == true) QButton("إيقاف الرسالة الحالية", { onSave(b.text, false); onDismiss() }, secondary = true)
        }
    }
}
