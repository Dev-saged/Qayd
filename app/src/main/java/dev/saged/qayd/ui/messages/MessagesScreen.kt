package dev.saged.qayd.ui.messages

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.saged.qayd.data.CustomerBalance
import dev.saged.qayd.data.Gateway
import dev.saged.qayd.data.Ledger
import dev.saged.qayd.data.PowerState
import dev.saged.qayd.data.Reading
import dev.saged.qayd.data.Shop
import dev.saged.qayd.data.SmsMsg
import dev.saged.qayd.data.SmsStatus
import dev.saged.qayd.data.Template
import dev.saged.qayd.data.TemplateKind
import dev.saged.qayd.data.Templates
import dev.saged.qayd.ui.components.IconTile
import dev.saged.qayd.ui.components.QButton
import dev.saged.qayd.ui.components.QField
import dev.saged.qayd.ui.components.QSheet
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.components.rememberNow
import dev.saged.qayd.ui.components.rememberPulse
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.theme.LocalPalette
import dev.saged.qayd.ui.util.clock
import dev.saged.qayd.ui.util.cur
import dev.saged.qayd.ui.util.dayLabel
import dev.saged.qayd.ui.util.ltr
import dev.saged.qayd.ui.util.money
import java.util.Calendar
import java.util.Locale

/** متغيرات القالب لزبون (ومعه آخر قراءة كهرباء إن وُجدت). */
fun smsVars(shop: Shop, c: CustomerBalance, reading: Reading? = null, amount: Double? = null): Map<String, String> {
    val now = Calendar.getInstance()
    return mapOf(
        "الاسم" to c.customer.name,
        "المحل" to shop.name,
        "الرصيد" to cur(c.balance.coerceAtLeast(0.0)),
        "المبلغ" to cur(amount ?: reading?.amount ?: c.balance.coerceAtLeast(0.0)),
        "التاريخ" to dayLabel(System.currentTimeMillis()).let { if (it == "اليوم") String.format(Locale.US, "%d/%d/%d", now.get(Calendar.DAY_OF_MONTH), now.get(Calendar.MONTH) + 1, now.get(Calendar.YEAR)) else it },
        "الشهر" to String.format(Locale.US, "%d/%d", now.get(Calendar.MONTH) + 1, now.get(Calendar.YEAR)),
        "القراءة_السابقة" to (reading?.prev?.let(::money) ?: ""),
        "القراءة_الحالية" to (reading?.curr?.let(::money) ?: ""),
        "الاستهلاك" to (reading?.kwh?.let(::money) ?: ""),
        "سعر_الكيلو" to (reading?.price?.let(::cur) ?: ""),
        "رابط_الكشف" to (dev.saged.qayd.data.StatementLinks.of(c.customer)?.let { dev.saged.qayd.data.StatementLinks.BASE + it } ?: ""),
    )
}

/** فئات الإرسال الجماعي: حسب الرصيد، ثم حسب القسم. */
private enum class Bulk(val label: String, val byDept: Boolean = false) {
    All("الكل"), Debtors("المدينون"), Late("المتأخرون"), Credit("لهم رصيد"),
    Shop("زبائن المحل", true), Subscribers("مشتركو الكهرباء", true), Power("فواتير الشهر", true),
}

private fun targetsOf(b: Bulk, ledger: Ledger, power: PowerState): List<Pair<CustomerBalance, Reading?>> = when (b) {
    Bulk.All -> ledger.customers.map { it to null }
    Bulk.Debtors -> ledger.customers.filter { it.balance > 0 }.map { it to null }
    Bulk.Late -> ledger.customers.filter { it.late }.map { it to null }
    Bulk.Credit -> ledger.customers.filter { it.balance < 0 }.map { it to null }
    Bulk.Shop -> ledger.customers.filter { !it.customer.power }.map { it to null }
    Bulk.Subscribers -> ledger.customers.filter { it.customer.power }.map { c -> c to power.subscribers.firstOrNull { it.customer.id == c.customer.id }?.history?.firstOrNull() }
    Bulk.Power -> power.subscribers.mapNotNull { s -> s.thisMonth?.let { r -> ledger.balanceOf(s.customer.id)?.let { it to r } } }
}.filter { it.first.customer.phone.isNotBlank() }

@Composable
fun MessagesScreen(
    padding: PaddingValues,
    shop: Shop,
    gateway: Gateway,
    queue: List<SmsMsg>,
    ledger: Ledger,
    power: PowerState,
    templates: List<Template>,
    smsLeft: Int?,
    smsQuota: Int,
    canSend: Boolean,
    canTemplates: Boolean,
    gatewayHere: Boolean,
    onGateway: (Boolean) -> Unit,
    onRetry: (String) -> Unit,
    onEnqueue: (List<Triple<String, String, String>>, String) -> Unit,
    onTemplates: () -> Unit,
    settings: dev.saged.qayd.data.SmsSettings = dev.saged.qayd.data.SmsSettings(),
    isOwner: Boolean = false,
    request: dev.saged.qayd.data.GatewayRequest? = null,
    onSaveSettings: (dev.saged.qayd.data.SmsSettings) -> Unit = {},
    onRequest: () -> Unit = {},
    onAnswer: (Boolean) -> Unit = {},
) {
    val ctx0 = LocalContext.current
    val deviceId = remember { dev.saged.qayd.system.DeviceId.get(ctx0) }
    val designated = settings.gatewayDevice.isNotBlank()
    val mine = designated && settings.gatewayDevice == deviceId
    var settingsOpen by remember { mutableStateOf(false) }
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val now = rememberNow().value
    val live = gateway.live(now)
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var bulk by remember { mutableStateOf<Bulk?>(null) }
    var restricted by remember { mutableStateOf(false) }
    val askSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) onGateway(true) else restricted = true }
    val today = Ledger.dayStart(now)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("الرسائل", Modifier.weight(1f), 22.sp, FontWeight.Bold)
                if (isOwner) IconTile(QIcons.Calendar, "إعدادات الرسائل", { settingsOpen = true }, Modifier.padding(end = 8.dp))
                if (canTemplates) IconTile(QIcons.Edit, "القوالب", onTemplates)
            }
        }
        item {
            val shape = RoundedCornerShape(24.dp)
            val c = if (live) p.ok else p.mute
            val pulse = if (live) rememberPulse() else 1f
            Column(Modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(p.surface, p.surface2))).border(1.dp, c.copy(alpha = .45f), shape).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(c.copy(alpha = pulse)))
                    T(if (live) "جوال الإرسال شغال" else "جوال الإرسال متوقف", Modifier.padding(start = 10.dp), 16.sp, FontWeight.SemiBold, c)
                }
                T(
                    if (live) "${gateway.device}${if (gatewayHere) " (هذا الجوال)" else ""} · آخر نبضة ${clock(gateway.lastSeen)}"
                    else "الرسائل تبقى في الطابور وتُرسل فور تشغيله على الجوال الذي فيه الشريحة.",
                    Modifier.padding(top = 6.dp), 12.5.sp, color = p.mute, maxLines = 2,
                )
                val start = {
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) onGateway(true)
                    else askSms.launch(Manifest.permission.SEND_SMS)
                }
                if (designated) T(
                    if (mine) "هذا الجوال هو جوال الإرسال المعتمد" else "جوال الإرسال المعتمد: ${settings.gatewayName}",
                    Modifier.padding(top = 8.dp), 12.5.sp, FontWeight.Medium, p.pri, maxLines = 2,
                )
                if (mine && request?.pending == true && !live) {
                    Row(
                        Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.warn.copy(alpha = .14f)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        T("${request.byName} يطلب تشغيل الإرسال", Modifier.weight(1f), 13.5.sp, FontWeight.Medium, maxLines = 2)
                        T("رفض", Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { onAnswer(false) }.padding(8.dp), 13.sp, FontWeight.Medium, p.mute)
                        T("قبول", Modifier.clip(RoundedCornerShape(10.dp)).background(p.pri).clickable(role = Role.Button) { onAnswer(true); start() }
                            .padding(horizontal = 14.dp, vertical = 8.dp), 13.sp, FontWeight.SemiBold, p.onPri)
                    }
                }
                if (canSend) {
                    Spacer(Modifier.height(12.dp))
                    when {
                        gatewayHere -> QButton("إيقاف الإرسال من هذا الجوال", { onGateway(false) }, secondary = true)
                        !designated || mine -> QButton(if (mine) "تشغيل الإرسال" else "اجعل هذا الجوال جوال الإرسال", { start() })
                        live -> Unit
                        request?.pending == true -> T("أُرسل طلبك إلى ${settings.gatewayName}، بانتظار القبول.", size = 12.5.sp, color = p.mute, maxLines = 2)
                        else -> QButton("اطلب تشغيل الإرسال", onRequest, secondary = true)
                    }
                    if (isOwner && !mine) T(
                        "اعتماد هذا الجوال جوال الإرسال الوحيد",
                        Modifier.padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) {
                            onSaveSettings(settings.copy(gatewayDevice = deviceId, gatewayName = dev.saged.qayd.system.DeviceId.name))
                        }.padding(8.dp), 12.5.sp, FontWeight.Medium, p.pri,
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Count("بالانتظار", queue.count { it.status == SmsStatus.Queued || it.status == SmsStatus.Sending }, p.warn, Modifier.weight(1f))
                Count("أُرسلت اليوم", queue.count { (it.status == SmsStatus.Sent || it.status == SmsStatus.Delivered) && it.at >= today }, p.ok, Modifier.weight(1f))
                Count("فشلت", queue.count { it.status == SmsStatus.Failed }, p.danger, Modifier.weight(1f))
            }
        }
        if (canSend) {
            item {
                val shape = RoundedCornerShape(22.dp)
                Column(Modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, p.line, shape).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("إرسال جماعي", Modifier.weight(1f), 15.sp, FontWeight.SemiBold)
                        if (smsLeft != null) T("المتبقي $smsLeft من $smsQuota هذا الشهر", size = 12.sp, color = if (smsLeft == 0) p.danger else p.mute)
                    }
                    val depts = power.subscribers.isNotEmpty()
                    listOf(false, true).filter { it == false || depts }.forEach { dept ->
                        T(if (dept) "حسب القسم" else "حسب الرصيد", Modifier.padding(top = 12.dp, bottom = 8.dp), 12.sp, color = p.mute)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Bulk.entries.filter { it.byDept == dept }.forEach { b -> Segment(b.label, targetsOf(b, ledger, power).size) { bulk = b } }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf<SmsStatus?>(null) + SmsStatus.entries).forEach { st ->
                    val on = filter == st?.key
                    T(
                        st?.label ?: "الكل",
                        Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) p.pri.copy(alpha = .18f) else p.surface)
                            .clickable(role = Role.RadioButton) { filter = st?.key }.padding(horizontal = 12.dp, vertical = 8.dp),
                        12.5.sp, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) p.pri else p.ink,
                    )
                }
            }
        }
        val list = queue.filter { filter == null || it.status.key == filter }.sortedByDescending { it.at }.take(100)
        if (list.isEmpty()) item { T("لا رسائل.", Modifier.padding(vertical = 10.dp), 13.sp, color = p.mute) }
        items(list, key = { it.id }) { m ->
            val c = when (m.status) {
                SmsStatus.Delivered, SmsStatus.Sent -> p.ok
                SmsStatus.Failed -> p.danger
                else -> p.warn
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(m.name.ifBlank { ltr(m.to) }, Modifier.weight(1f), 14.sp, FontWeight.Medium)
                    T(m.status.label, Modifier.clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = .14f)).padding(horizontal = 10.dp, vertical = 4.dp), 11.5.sp, FontWeight.SemiBold, c)
                }
                T(m.text, Modifier.padding(top = 6.dp), 12.5.sp, color = p.mute, maxLines = 3)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("${m.template} · ${m.byName} · ${dayLabel(m.at)} ${clock(m.at)}${if (m.error.isNotBlank()) " · ${m.error}" else ""}", Modifier.weight(1f), 11.sp, color = p.mute, maxLines = 2)
                    if (m.status == SmsStatus.Failed && canSend) {
                        T("أعد", Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { onRetry(m.id) }.padding(8.dp), 12.5.sp, FontWeight.SemiBold, p.pri)
                    }
                }
            }
        }
    }

    if (restricted) dev.saged.qayd.ui.perms.RestrictedSmsSheet { restricted = false }
    if (settingsOpen) SmsSettingsSheet(settings, templates, mine, onSaveSettings) { settingsOpen = false }

    bulk?.let { b ->
        val all = templates
        val targets = targetsOf(b, ledger, power)
        val kind = if (b == Bulk.Power || b == Bulk.Subscribers) TemplateKind.Power else TemplateKind.Debt
        SendSheet(
            title = "${b.label} · ${targets.size}",
            left = smsLeft,
            templates = all.filter { it.kind == kind } + all.filter { it.kind == TemplateKind.General },
            count = targets.size,
            preview = { t -> targets.firstOrNull()?.let { (c, r) -> Templates.fill(t.body, smsVars(shop, c, r)) } ?: "لا أحد برقم جوال في هذه الفئة" },
            onSend = { t ->
                onEnqueue(targets.map { (c, r) -> Triple(c.customer.phone, c.customer.name, Templates.fill(t.body, smsVars(shop, c, r))) }, t.title)
            },
        ) { bulk = null }
    }
}

@Composable
private fun Segment(label: String, n: Int, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.clip(shape).background(p.surface2).border(1.dp, p.line, shape).clickable(enabled = n > 0, role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(label, size = 13.sp, weight = FontWeight.Medium, color = if (n > 0) p.ink else p.mute)
        T(n.toString(), Modifier.padding(start = 8.dp).clip(RoundedCornerShape(8.dp)).background(if (n > 0) p.pri.copy(alpha = .16f) else p.line)
            .padding(horizontal = 7.dp, vertical = 2.dp), 11.5.sp, FontWeight.SemiBold, if (n > 0) p.pri else p.mute)
    }
}

@Composable
private fun Count(label: String, n: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(16.dp)).padding(12.dp)) {
        T(label, size = 11.5.sp, color = p.mute)
        T(n.toString(), size = 20.sp, weight = FontWeight.SemiBold, color = if (n > 0) color else p.ink)
    }
}

/** اختيار قالب ومعاينته ثم الإضافة للطابور. */
@Composable
fun SendSheet(
    title: String,
    templates: List<Template>,
    count: Int,
    preview: (Template) -> String,
    onSend: (Template) -> Unit,
    left: Int? = null,
    smsEnabled: Boolean = true,
    waPrefix: String? = null,
    onWhatsApp: ((Template, String) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    var pick by rememberSaveable { mutableStateOf(templates.firstOrNull()?.id) }
    val t = templates.firstOrNull { it.id == pick }
    QSheet(title, onDismiss) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            templates.forEach { tp ->
                val on = tp.id == pick
                T(
                    tp.title,
                    Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) p.pri.copy(alpha = .18f) else p.surface2)
                        .border(1.dp, if (on) p.pri else p.line, RoundedCornerShape(14.dp))
                        .clickable(role = Role.RadioButton) { pick = tp.id }.padding(horizontal = 14.dp, vertical = 9.dp),
                    13.sp, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) p.pri else p.ink,
                )
            }
        }
        if (t != null) {
            T(preview(t), Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface2).padding(14.dp), 13.5.sp, maxLines = 14)
        }
        Spacer(Modifier.height(16.dp))
        if (onWhatsApp != null && t != null) {
            // نفس الرقم قد يكون على واتساب بمقدمة 970 أو 972؛ المختارة تُحفظ للزبون
            Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (waPrefix.isNullOrBlank()) {
                    listOf("970", "972").forEach { pre -> QButton("واتساب ⁦+$pre⁩", { onWhatsApp(t, pre); onDismiss() }, Modifier.weight(1f), secondary = true) }
                } else {
                    QButton("واتساب ⁦+$waPrefix⁩", { onWhatsApp(t, waPrefix); onDismiss() }, Modifier.weight(1f), secondary = true)
                    QButton("⁦+${if (waPrefix == "970") "972" else "970"}⁩", { onWhatsApp(t, if (waPrefix == "970") "972" else "970"); onDismiss() }, Modifier.weight(.6f), secondary = true)
                }
            }
        }
        if (!smsEnabled) {
            T("رسائل SMS غير مفعّلة لمحلك، والإرسال عبر واتساب مجاني.", size = 12.sp, color = p.mute, maxLines = 2)
        } else if (left != null && count > left) {
            dev.saged.qayd.ui.components.LockedCard(
                "رسائل إضافية",
                title = if (left == 0) "انتهت رسائل هذا الشهر" else "المتبقي $left رسالة فقط",
                body = "حد رسائل محلك الشهري لا يكفي لهذا الإرسال. تواصل مع مسؤول التطبيق للاشتراك.",
            )
        } else {
            QButton(if (count > 1) "أضف $count رسالة للطابور" else "أرسل", { if (t != null && count > 0) { onSend(t); onDismiss() } }, secondary = count == 0)
        }
    }
}

/** القوالب: الثابتة من النظام، والمخصصة يضيفها ويعدّلها صاحب الصلاحية. */
@Composable
fun TemplatesPage(
    padding: PaddingValues,
    custom: List<Template>,
    onBack: () -> Unit,
    onSave: (String?, String, String, TemplateKind) -> Unit,
    onDelete: (String) -> Unit,
) {
    val p = LocalPalette.current
    var editing by remember { mutableStateOf<Template?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(QIcons.Back, "رجوع", onBack)
                T("قوالب الرسائل", Modifier.weight(1f).padding(horizontal = 12.dp), 20.sp, FontWeight.Bold)
                IconTile(QIcons.Plus, "قالب جديد", { editing = Template("", "", "", TemplateKind.General) })
            }
        }
        items(Templates.BUILT_IN + custom, key = { it.id }) { t ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface)
                    .clickable(enabled = !t.builtIn, role = Role.Button) { editing = t }.padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(t.title, Modifier.weight(1f), 14.5.sp, FontWeight.SemiBold)
                    T(if (t.builtIn) "ثابت" else "مخصص", size = 11.5.sp, color = if (t.builtIn) p.mute else p.pri)
                }
                T(t.body, Modifier.padding(top = 6.dp), 12.5.sp, color = p.mute, maxLines = 6)
            }
        }
    }
    editing?.let { t -> TemplateSheet(t, { title, body, kind -> onSave(t.id.ifBlank { null }, title, body, kind); editing = null }, if (t.id.isBlank()) null else ({ onDelete(t.id); editing = null })) { editing = null } }
}

@Composable
private fun TemplateSheet(t: Template, onSave: (String, String, TemplateKind) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var title by rememberSaveable { mutableStateOf(t.title) }
    var body by rememberSaveable { mutableStateOf(t.body) }
    var kind by rememberSaveable { mutableStateOf(t.kind) }
    QSheet(if (t.id.isBlank()) "قالب جديد" else "تعديل القالب", onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            QField(title, { title = it }, "اسم القالب", hint = "تذكير قبل العيد")
            QField(body, { body = it }, "نص الرسالة", hint = "مرحباً {الاسم}…", lines = 5)
            T("اضغط لإدراج متغير:", size = 12.sp, color = p.mute)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Templates.VARS.forEach { v ->
                    T("{$v}", Modifier.clip(RoundedCornerShape(10.dp)).background(p.surface2).clickable(role = Role.Button) { body += "{$v}" }
                        .padding(horizontal = 10.dp, vertical = 6.dp), 12.sp, color = p.pri)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TemplateKind.Debt to "ديون", TemplateKind.Power to "كهرباء", TemplateKind.General to "عام").forEach { (k, l) ->
                    val on = k == kind
                    T(l, Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) p.pri.copy(alpha = .18f) else p.surface2)
                        .clickable(role = Role.RadioButton) { kind = k }.padding(horizontal = 14.dp, vertical = 8.dp), 13.sp, color = if (on) p.pri else p.ink)
                }
            }
            QButton("حفظ", { if (title.isNotBlank() && body.isNotBlank()) onSave(title, body, kind) })
            if (onDelete != null) QButton("حذف القالب", onDelete, secondary = true)
        }
    }
}

/** يفتح محادثة واتساب بالنص جاهزاً، بالمقدمة المختارة للرقم المحلي. */
fun openWhatsApp(ctx: android.content.Context, phone: String, prefix: String, text: String) {
    val d = phone.filter { it.isDigit() }
    val num = when {
        d.startsWith("970") || d.startsWith("972") -> prefix + d.drop(3)
        d.startsWith("00970") || d.startsWith("00972") -> prefix + d.drop(5)
        d.startsWith("0") -> prefix + d.drop(1)
        else -> prefix + d
    }
    runCatching {
        ctx.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/$num?text=" + android.net.Uri.encode(text)))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** إعدادات الرسائل: رابط الكشف والتذكير الشهري المجدول. */
@Composable
private fun SmsSettingsSheet(
    s: dev.saged.qayd.data.SmsSettings, templates: List<Template>, mine: Boolean,
    onSave: (dev.saged.qayd.data.SmsSettings) -> Unit, onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    var v by remember { mutableStateOf(s) }
    QSheet("إعدادات الرسائل", onDismiss, footer = { QButton("حفظ", { onSave(v); onDismiss() }) }) {
        dev.saged.qayd.ui.components.QSwitchGroup("رابط الكشف", listOf(
            dev.saged.qayd.ui.components.SwitchItem("إضافة رابط كشف الحساب للرسائل", v.link, { v = v.copy(link = it) },
                "رابط قصير في آخر الرسالة يفتح فيه الزبون رصيده وحركاته بدون تطبيق"),
        ))
        Spacer(Modifier.height(14.dp))
        dev.saged.qayd.ui.components.QSwitchGroup("التذكير الشهري", listOf(
            dev.saged.qayd.ui.components.SwitchItem("تذكير المدينين تلقائياً كل شهر", v.reminderOn, { v = v.copy(reminderOn = it) },
                "يُرسل من جوال الإرسال المعتمد، مع تنبيه قبل الموعد بساعة"),
            dev.saged.qayd.ui.components.SwitchItem("المتأخرون فقط", v.reminderLateOnly, { v = v.copy(reminderLateOnly = it) }, "بدل كل من عليه رصيد"),
        ))
        if (v.reminderOn) {
            Spacer(Modifier.height(10.dp))
            dev.saged.qayd.ui.components.QStepper("يوم الشهر", v.reminderDay, 1..28) { v = v.copy(reminderDay = it) }
            dev.saged.qayd.ui.components.QStepper("الساعة", v.reminderHour, 0..23) { v = v.copy(reminderHour = it) }
            T("القالب", Modifier.padding(top = 8.dp, bottom = 6.dp), 13.sp, FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                templates.filter { it.kind == TemplateKind.Debt || it.kind == TemplateKind.General }.forEach { t ->
                    val on = t.id == v.reminderTemplate
                    T(t.title, Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) p.pri.copy(alpha = .18f) else p.surface2)
                        .clickable(role = Role.RadioButton) { v = v.copy(reminderTemplate = t.id) }.padding(horizontal = 12.dp, vertical = 8.dp), 13.sp,
                        if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) p.pri else p.ink)
                }
            }
            T(
                if (v.gatewayDevice.isBlank()) "اعتمد جوال الإرسال من بطاقة الإرسال أولاً، فالتذكير يعمل منه فقط."
                else if (mine) "هذا الجوال سيرسل التذكير في الموعد." else "سيرسل التذكير جوال: ${v.gatewayName}",
                Modifier.padding(top = 10.dp), 12.sp, color = if (v.gatewayDevice.isBlank()) p.warn else p.mute, maxLines = 2,
            )
        }
    }
}
