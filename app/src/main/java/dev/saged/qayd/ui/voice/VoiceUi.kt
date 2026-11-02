package dev.saged.qayd.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.saged.qayd.data.Access
import dev.saged.qayd.data.Ledger
import dev.saged.qayd.data.Perm
import dev.saged.qayd.data.TxKind
import dev.saged.qayd.data.AmountInput
import dev.saged.qayd.ui.components.IconTile
import dev.saged.qayd.ui.components.NumpadSheet
import dev.saged.qayd.ui.components.QButton
import dev.saged.qayd.ui.components.QIcon
import dev.saged.qayd.ui.components.QSheet
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.shell.CustomerPickerSheet
import dev.saged.qayd.ui.theme.LocalPalette
import dev.saged.qayd.ui.util.clock
import dev.saged.qayd.ui.util.cur
import dev.saged.qayd.ui.util.dayLabel
import dev.saged.qayd.voice.VoiceEntry
import dev.saged.qayd.voice.VoiceInbox
import dev.saged.qayd.voice.VoiceParse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** تسجيل صوتي سريع: يُحفظ في صندوق الصوت ولا يُسجَّل في الدفتر قبل المراجعة. */
@Composable
fun VoiceRecordSheet(shopId: String, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val rec = remember { VoiceInbox.Recorder(ctx.applicationContext) }
    var recording by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf<String?>(null) }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(recording) { seconds = 0; while (recording) { delay(1000); seconds++ } }
    DisposableEffect(Unit) { onDispose { if (recording) rec.stop() } }
    fun finish() {
        recording = false
        val out = rec.stop() ?: run { msg = "التسجيل قصير جداً"; return }
        val (file, pcm) = out
        val e = VoiceEntry("v${System.currentTimeMillis()}", System.currentTimeMillis(), file.absolutePath, "", shopId)
        VoiceInbox.add(ctx.applicationContext, e)
        scope.launch { VoiceInbox.update(ctx.applicationContext, e.id, VoiceInbox.recognize(ctx.applicationContext, pcm)) }
        onDismiss()
    }
    QSheet("إدخال بالصوت", { if (!recording) onDismiss() }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            T("قل مثلاً: \"سجّل على أبو أحمد خمسة وعشرين شيكل\" أو \"دفع محمود مية\".", size = 13.sp, color = p.mute, maxLines = 2)
            Spacer(Modifier.height(22.dp))
            val level by animateFloatAsState(if (recording) rec.level.value else 0f, label = "lvl")
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(p.pri.copy(alpha = .10f + level * .25f), size.minDimension / 2 * (.72f + level * .28f))
                    drawCircle(p.pri.copy(alpha = .5f), size.minDimension / 2 * .72f, style = Stroke(2.dp.toPx()))
                }
                Box(
                    Modifier.size(96.dp).clip(CircleShape).background(if (recording) p.danger else p.pri)
                        .clickable(role = Role.Button) {
                            when {
                                !granted -> ask.launch(Manifest.permission.RECORD_AUDIO)
                                recording -> finish()
                                else -> { msg = null; recording = rec.start(); if (!recording) msg = "تعذر تشغيل الميكروفون" }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { QIcon(if (recording) QIcons.Stop else QIcons.Mic, p.onPri, Modifier.size(40.dp)) }
            }
            Spacer(Modifier.height(14.dp))
            T(
                when { !granted -> "اسمح باستخدام الميكروفون"; recording -> "جارٍ التسجيل ⁦0:${seconds.toString().padStart(2, '0')}⁩ · اضغط للإنهاء"; else -> "اضغط وتكلم" },
                size = 14.sp, weight = FontWeight.Medium, color = if (recording) p.danger else p.ink,
            )
            msg?.let { T(it, Modifier.padding(top = 6.dp), 12.5.sp, color = p.warn) }
            T(
                if (VoiceInbox.offlineAvailable(ctx)) "يعمل بدون إنترنت على هذا الجوال." else "لفهم الكلام بدون إنترنت نزّل اللغة العربية من إعدادات التعرّف على الكلام في الجوال.",
                Modifier.padding(top = 14.dp), 11.5.sp, color = p.mute, maxLines = 2,
            )
        }
    }
}

/** صندوق الصوت: استمع، راجع ما فُهم، وعدّل ثم أكّد أو احذف. */
@Composable
fun VoiceInboxPage(
    padding: PaddingValues, shopId: String, ledger: Ledger, access: Access,
    onDebt: (String, Double) -> Unit, onPayment: (String, Double) -> Unit, onSale: (Double) -> Unit,
    onAddCustomer: (String, String) -> String, onRecord: () -> Unit, onBack: () -> Unit,
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { VoiceInbox.load(ctx.applicationContext) }
    val list = VoiceInbox.items.value.filter { it.shopId == shopId }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(QIcons.Back, "رجوع", onBack)
                T("صندوق الصوت", Modifier.weight(1f).padding(horizontal = 12.dp), 20.sp, FontWeight.Bold)
                IconTile(QIcons.Mic, "تسجيل جديد", onRecord)
            }
        }
        if (list.isEmpty()) item {
            T("لا تسجيلات بانتظار المراجعة. سجّل وأنت مشغول، وراجعها هنا لاحقاً بهدوء.", Modifier.padding(vertical = 14.dp), 13.5.sp, color = p.mute, maxLines = 3)
        }
        items(list, key = { it.id }) { e ->
            EntryCard(e, ledger, access, onDebt, onPayment, onSale, onAddCustomer) { VoiceInbox.remove(ctx.applicationContext, e.id) }
        }
    }
}

@Composable
private fun EntryCard(
    e: VoiceEntry, ledger: Ledger, access: Access,
    onDebt: (String, Double) -> Unit, onPayment: (String, Double) -> Unit, onSale: (Double) -> Unit,
    onAddCustomer: (String, String) -> String, onDone: () -> Unit,
) {
    val p = LocalPalette.current
    val parsed = remember(e.text, ledger.customers.size) { VoiceParse.parse(e.text, ledger.customers) }
    var kind by remember(parsed) { mutableStateOf(parsed.kind ?: TxKind.Debt) }
    var amount by remember(parsed) { mutableStateOf(parsed.amount) }
    var customerId by remember(parsed) { mutableStateOf(parsed.customer?.customer?.id) }
    var editAmount by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val c = customerId?.let { ledger.balanceOf(it) }
    val needsCustomer = kind != TxKind.Sale
    val ready = (amount ?: 0.0) > 0 && (!needsCustomer || c != null)
    val allowed = when (kind) { TxKind.Debt -> access.can(Perm.AddDebt); TxKind.Payment -> access.can(Perm.AddPayment); else -> access.can(Perm.QuickSale) }
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, if (ready) p.pri.copy(alpha = .4f) else p.line, shape).padding(14.dp)) {
        Player(e.audio)
        T("${dayLabel(e.at)} ${clock(e.at)}", Modifier.padding(top = 8.dp), 11.5.sp, color = p.mute)
        T(
            e.text.ifBlank { "لم يُفهم الكلام بعد. استمع للتسجيل وأكمل البيانات يدوياً." },
            Modifier.padding(top = 4.dp), 14.5.sp, if (e.text.isBlank()) FontWeight.Normal else FontWeight.Medium, if (e.text.isBlank()) p.mute else p.ink, maxLines = 4,
        )
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(TxKind.Debt to "دين", TxKind.Payment to "دفعة", TxKind.Sale to "بيع").forEach { (k, l) ->
                val on = k == kind
                T(l, Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) p.pri.copy(alpha = .18f) else p.surface2)
                    .clickable(role = Role.RadioButton) { kind = k }.padding(vertical = 9.dp), 13.5.sp, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) p.pri else p.ink)
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(p.surface2).clickable(role = Role.Button) { editAmount = true }.padding(12.dp)) {
                T("المبلغ", size = 11.5.sp, color = p.mute)
                T(amount?.let(::cur) ?: "اضغط للإدخال", size = 16.sp, weight = FontWeight.SemiBold, color = if (amount == null) p.warn else p.ink)
            }
            if (needsCustomer) Column(Modifier.weight(1.4f).clip(RoundedCornerShape(14.dp)).background(p.surface2).clickable(role = Role.Button) { picking = true }.padding(12.dp)) {
                T("الزبون", size = 11.5.sp, color = p.mute)
                T(c?.customer?.name ?: "اختر الزبون", size = 15.sp, weight = FontWeight.SemiBold, color = if (c == null) p.warn else p.ink)
            }
        }
        if (c != null && amount != null && needsCustomer) {
            val after = c.balance + if (kind == TxKind.Debt) amount!! else -amount!!
            T("رصيده الآن ${cur(c.balance)} ← بعد التأكيد ${cur(after)}", Modifier.padding(top = 8.dp), 12.5.sp, FontWeight.Medium, p.pri, maxLines = 2)
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QButton("حذف", onDone, Modifier.weight(1f), secondary = true)
            QButton("تأكيد", {
                if (ready && allowed) {
                    when (kind) { TxKind.Debt -> onDebt(customerId!!, amount!!); TxKind.Payment -> onPayment(customerId!!, amount!!); else -> onSale(amount!!) }
                    onDone()
                }
            }, Modifier.weight(1.4f), secondary = !(ready && allowed))
        }
        if (!allowed) T("ليست لديك صلاحية هذا النوع من الحركات.", Modifier.padding(top = 6.dp), 12.sp, color = p.danger)
    }
    if (editAmount) NumpadSheet("المبلغ", e.text.take(60), p.pri, "حفظ", initial = AmountInput.of(amount), onDismiss = { editAmount = false }) { v, _ -> amount = v; editAmount = false }
    if (picking) CustomerPickerSheet("اختر الزبون", ledger.customers, { cu -> customerId = cu.id; picking = false }, { n, ph -> customerId = onAddCustomer(n, ph); picking = false }) { picking = false }
}

/** شريط تشغيل التسجيل: زر تشغيل وخط تقدّم. */
@Composable
private fun Player(path: String) {
    val p = LocalPalette.current
    val player = remember(path) { MediaPlayer().apply { runCatching { setDataSource(path); prepare() } } }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableFloatStateOf(0f) }
    DisposableEffect(player) { onDispose { runCatching { player.release() } } }
    LaunchedEffect(playing) {
        while (playing) {
            pos = runCatching { player.currentPosition.toFloat() / player.duration.coerceAtLeast(1) }.getOrDefault(0f)
            if (!player.isPlaying) { playing = false; pos = 0f }
            delay(80)
        }
    }
    val total = runCatching { player.duration / 1000 }.getOrDefault(0)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface2).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(p.pri).clickable(role = Role.Button) {
                if (playing) { player.pause(); playing = false } else { runCatching { player.start() }; playing = true }
            },
            contentAlignment = Alignment.Center,
        ) { QIcon(if (playing) QIcons.Pause else QIcons.Play, p.onPri, Modifier.size(20.dp)) }
        Canvas(Modifier.weight(1f).height(26.dp).padding(horizontal = 12.dp)) {
            val bars = 32
            val w = size.width / bars
            for (i in 0 until bars) {
                val h = size.height * (.25f + .75f * kotlin.math.abs(kotlin.math.sin(i * 1.7f + path.length)))
                val x = size.width - (i + .5f) * w
                drawLine(if (i.toFloat() / bars <= pos) p.pri else p.line, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), w * .55f)
            }
        }
        T("⁦0:${total.toString().padStart(2, '0')}⁩", size = 12.sp, color = p.mute)
    }
    if (!File(path).exists()) T("الملف غير موجود", size = 11.sp, color = p.danger)
}
