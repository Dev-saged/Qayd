package dev.saged.qayd.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.config.BrandConfig
import dev.saged.qayd.data.Access
import dev.saged.qayd.data.Account
import dev.saged.qayd.data.Perm
import dev.saged.qayd.data.Ledger
import dev.saged.qayd.data.Tx
import dev.saged.qayd.data.Module
import dev.saged.qayd.data.Shop
import dev.saged.qayd.data.SyncState
import dev.saged.qayd.ui.components.QIcon
import dev.saged.qayd.ui.components.SyncChip
import dev.saged.qayd.ui.components.Ring
import dev.saged.qayd.ui.components.Sparkline
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.components.entrance
import dev.saged.qayd.ui.components.pressScale
import dev.saged.qayd.ui.components.rememberNow
import dev.saged.qayd.ui.components.rememberPulse
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.theme.LocalPalette
import dev.saged.qayd.ui.util.clock
import dev.saged.qayd.ui.util.ltr
import dev.saged.qayd.ui.util.cur
import dev.saged.qayd.ui.util.money
import java.util.Locale

@Composable
fun HomeScreen(
    padding: PaddingValues,
    shop: Shop,
    account: Account,
    sync: SyncState,
    ledger: Ledger,
    initialSlots: List<PsSlot>,
    access: Access,
    onQuickSale: (() -> Unit)?,
    onTxTap: (Tx) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val now by rememberNow()
    val slots = remember(initialSlots) { mutableStateListOf(*initialSlots.toTypedArray()) }
    var hidden by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(shop, account, sync, Modifier.entrance(0))
        if (access.can(Perm.ViewBalances)) Hero(ledger, hidden, onToggle = { hidden = !hidden }, modifier = Modifier.entrance(1))
        if (onQuickSale != null) QuickSale(ledger, Modifier.entrance(2), onQuickSale, onTxTap)

        if (Module.Ps in shop.modules && slots.isNotEmpty()) {
            val running = slots.count { it.startedAt != null }
            SectionTitle("الشاشات", "$running من ${slots.size} قيد التشغيل", Modifier.entrance(3))
            PsGrid(slots, now, Modifier.entrance(4)) { i, start ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                slots[i] = slots[i].copy(startedAt = start)
            }
        }

        Row(Modifier.entrance(5), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("متأخرون", ledger.late.toString(), Modifier.weight(1f))
            MiniStat("الزبائن", ledger.customers.size.toString(), Modifier.weight(1f))
            if (Module.Sms in shop.modules) GatewayStat(false, Modifier.weight(1f))
        }
        Spacer(Modifier.height(96.dp))
    }
}

/** البيع السريع: مبيعات اليوم وزر بيع يفتح النمباد مباشرة. */
@Composable
private fun QuickSale(l: Ledger, modifier: Modifier, onSale: () -> Unit, onTxTap: (Tx) -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(24.dp)
    val source = remember { MutableInteractionSource() }
    Column(modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, p.line, shape).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("مبيعات اليوم", size = 13.sp, color = p.mute)
                T(cur(l.todaySales), size = 26.sp, weight = FontWeight.SemiBold)
                T(if (l.todaySalesCount == 0) "لا مبيعات بعد" else "${l.todaySalesCount} عملية بيع", size = 12.sp, color = p.mute)
            }
            Row(
                Modifier
                    .pressScale(source, .94f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(p.pri, lerp(p.pri, Color.Black, .35f))))
                    .clickable(source, indication = null, role = Role.Button, onClick = onSale)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QIcon(QIcons.Cart, p.onPri, Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                T("سجّل بيع", size = 15.sp, weight = FontWeight.SemiBold, color = p.onPri)
            }
        }
        if (l.todaySalesList.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            l.todaySalesList.take(5).forEach { t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClickLabel = "خيارات") { onTxTap(t) }
                        .drawWithContent {
                            drawContent()
                            drawLine(p.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                        }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(clock(t.at), size = 12.5.sp, color = p.mute)
                    T(t.byName, Modifier.weight(1f).padding(horizontal = 10.dp), 12.5.sp, color = p.mute)
                    if (t.pending) Box(Modifier.padding(end = 8.dp).size(6.dp).clip(CircleShape).background(p.warn))
                    T(cur(t.amount), size = 14.5.sp, weight = FontWeight.SemiBold, color = p.ok)
                }
            }
        } else {
            T("المبلغ فقط من لوحة الأرقام، بدون كيبورد الجوال.", Modifier.padding(top = 10.dp), 12.5.sp, color = p.mute, maxLines = 2)
        }
    }
}

@Composable
private fun Header(shop: Shop, account: Account, sync: SyncState, modifier: Modifier) {
    val p = LocalPalette.current
    Row(modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(p.pri, lerp(p.pri, Color.Black, .45f)))),
            contentAlignment = Alignment.Center,
        ) { T(shop.name.take(1), size = 18.sp, weight = FontWeight.Bold, color = p.onPri) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            T(shop.name, size = 16.sp, weight = FontWeight.SemiBold)
            T(ltr(account.email), size = 12.sp, color = p.mute)
        }
        SyncChip(sync, Modifier.padding(end = 10.dp))
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(p.surface)
                .border(1.dp, p.line, RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClickLabel = "الإشعارات") {},
            contentAlignment = Alignment.Center,
        ) {
            QIcon(QIcons.Bell, p.ink, Modifier.size(22.dp), "الإشعارات")
        }
    }
}

@Composable
private fun Hero(s: Ledger, hidden: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(p.surface, p.surface2)))
            .border(1.dp, p.line, shape)
            .drawWithContent {
                drawContent()
                drawLine(
                    Brush.horizontalGradient(listOf(Color.Transparent, p.pri, Color.Transparent)),
                    Offset(0f, 1f), Offset(size.width, 1f), 2.dp.toPx(),
                )
            }
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("إجمالي الديون المستحقة", Modifier.weight(1f), 13.sp, color = p.mute)
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                QIcon(if (hidden) QIcons.EyeOff else QIcons.Eye, p.mute, Modifier.size(22.dp), if (hidden) "إظهار المبلغ" else "إخفاء المبلغ")
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            T(if (hidden) "••••••" else money(s.totalDebt), size = 40.sp, weight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            T(BrandConfig.CURRENCY, Modifier.padding(bottom = 8.dp), 18.sp, FontWeight.Medium, p.pri)
        }
        if (s.todayCollected > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QIcon(QIcons.Down, p.ok, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                T("انخفض ${cur(s.todayCollected)} بتحصيل اليوم", size = 12.5.sp, color = p.ok)
            }
        } else {
            T("لا تحصيل اليوم بعد", size = 12.5.sp, color = p.mute)
        }
        if (s.totalCredit > 0) T("للزبائن رصيد مقدم ${cur(s.totalCredit)}", size = 12.sp, color = p.mute)
        if (s.debtTrend.size >= 2) Sparkline(s.debtTrend, p.pri, Modifier.fillMaxWidth().height(46.dp).padding(top = 8.dp))
    }
}

@Composable
private fun SectionTitle(title: String, aside: String, modifier: Modifier) {
    val p = LocalPalette.current
    Row(modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        T(title, Modifier.weight(1f), 14.5.sp, FontWeight.SemiBold)
        T(aside, size = 13.sp, color = p.mute)
    }
}

/** شبكة الشاشات: ثلاث في كل صف. ضغطة تبدأ جلسة، وضغطة مطولة تنهيها. */
@Composable
private fun PsGrid(slots: List<PsSlot>, now: Long, modifier: Modifier, onChange: (Int, Long?) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        slots.chunked(3).forEachIndexed { row, chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                chunk.forEachIndexed { j, s ->
                    val i = row * 3 + j
                    PsTile(
                        slot = s,
                        now = now,
                        modifier = Modifier.weight(1f),
                        onTap = { if (s.startedAt == null) onChange(i, System.currentTimeMillis()) },
                        onLong = { if (s.startedAt != null) onChange(i, null) },
                    )
                }
                repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PsTile(slot: PsSlot, now: Long, modifier: Modifier, onTap: () -> Unit, onLong: () -> Unit) {
    val p = LocalPalette.current
    val start = slot.startedAt
    val elapsed = if (start != null) (now - start).coerceAtLeast(0L) else 0L
    val booked = slot.bookedMinutes * 60_000L
    val left = booked - elapsed
    val over = start != null && left <= 0L
    val warn = start != null && !over && left <= BrandConfig.PS_WARN_MINUTES * 60_000L
    val accent = when {
        over -> p.danger
        warn -> p.warn
        else -> p.pri
    }
    val shape = RoundedCornerShape(20.dp)
    val source = remember { MutableInteractionSource() }
    val pulse = if (warn || over) rememberPulse() else 1f

    Column(
        modifier
            .pressScale(source)
            .heightIn(min = 118.dp)
            .clip(shape)
            .background(p.surface)
            .border(1.dp, if (start != null && (warn || over)) accent.copy(alpha = .65f) else p.line, shape)
            .combinedClickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClickLabel = if (start == null) "ابدأ جلسة" else null,
                onLongClickLabel = if (start != null) "أنهِ الجلسة" else null,
                onClick = onTap,
                onLongClick = onLong,
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        T(slot.number.toString(), Modifier.align(Alignment.Start).padding(start = 2.dp), 11.sp, color = p.mute)
        if (start == null) {
            QIcon(QIcons.Pad, p.mute, Modifier.size(30.dp))
            T("متاحة", size = 15.sp, weight = FontWeight.SemiBold)
            T("ابدأ جلسة", size = 11.sp, color = p.mute)
        } else {
            Ring((elapsed.toFloat() / booked).coerceIn(0f, 1f), accent, p.line, Modifier.size(42.dp))
            val sec = elapsed / 1000
            T(String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60), size = 15.sp, weight = FontWeight.SemiBold)
            T(
                when {
                    over -> "انتهى الوقت"
                    else -> "باقي ${(left + 59_999) / 60_000} د"
                },
                Modifier.then(if (warn || over) Modifier.alphaPulse(pulse) else Modifier),
                11.sp,
                color = if (warn || over) accent else p.mute,
            )
        }
    }
}

private fun Modifier.alphaPulse(a: Float) = graphicsLayer { alpha = a }

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).background(p.surface).border(1.dp, p.line, shape).padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        T(label, size = 11.5.sp, color = p.mute)
        T(value, size = 19.sp, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun GatewayStat(online: Boolean, modifier: Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    val c = if (online) p.ok else p.danger
    val pulse = rememberPulse()
    Column(
        modifier.clip(shape).background(p.surface).border(1.dp, p.line, shape).padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        T("جوال الإرسال", size = 11.5.sp, color = p.mute)
        Row(Modifier.height(27.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.copy(alpha = if (online) pulse else 1f)))
            Spacer(Modifier.width(6.dp))
            T(if (online) "متصل" else "متوقف", size = 13.sp, weight = FontWeight.SemiBold, color = c)
        }
    }
}
