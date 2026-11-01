package dev.saged.qayd.ui.power

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.data.AmountInput
import dev.saged.qayd.data.PowerState
import dev.saged.qayd.data.Subscriber
import dev.saged.qayd.data.round2
import dev.saged.qayd.ui.components.IconTile
import dev.saged.qayd.ui.components.NumpadSheet
import dev.saged.qayd.ui.components.QButton
import dev.saged.qayd.ui.components.QField
import dev.saged.qayd.ui.components.QSheet
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.theme.LocalPalette
import dev.saged.qayd.ui.util.cur
import dev.saged.qayd.ui.util.ltr
import dev.saged.qayd.ui.util.money

private const val KWH = "كيلو"

/** الكهرباء: المشتركون وحالة قراءة هذا الشهر، جولة القراءات، وإضافة مشترك. */
@Composable
fun PowerPage(
    padding: PaddingValues,
    state: PowerState,
    price: Double,
    canRead: Boolean,
    canManage: Boolean,
    onBack: () -> Unit,
    onOpenCustomer: (String) -> Unit,
    onReading: (String, Double, Double) -> Unit,
    onAddSubscriber: (String, String, String, Double) -> Unit,
) {
    val p = LocalPalette.current
    var reading by remember { mutableStateOf<Subscriber?>(null) }
    var round by remember { mutableStateOf<List<String>?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var initialFor by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(QIcons.Back, "رجوع", onBack)
                T("الكهرباء", Modifier.weight(1f).padding(horizontal = 12.dp), 20.sp, FontWeight.Bold)
                if (canManage) IconTile(QIcons.UserPlus, "مشترك جديد", { adding = true })
            }
        }
        item {
            val shape = RoundedCornerShape(24.dp)
            Column(
                Modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(p.surface, p.surface2))).border(1.dp, p.line, shape).padding(18.dp),
            ) {
                T("هذا الشهر", size = 13.sp, color = p.mute)
                T(ltr("${money(state.monthKwh)} $KWH"), size = 30.sp, weight = FontWeight.SemiBold)
                T("فواتير ${cur(state.monthAmount)} · قُرئ ${state.subscribers.size - state.pending} من ${state.subscribers.size}", size = 13.sp, color = p.mute)
                T(if (price > 0) "سعر الكيلو ${cur(price)}" else "حدد سعر الكيلو من المزيد ← الأسعار", size = 12.5.sp, color = if (price > 0) p.mute else p.danger)
                if (canRead && state.pending > 0) {
                    Spacer(Modifier.height(12.dp))
                    QButton("ابدأ جولة القراءات (${state.pending})", {
                        val ids = state.subscribers.filter { it.thisMonth == null }.map { it.customer.id }
                        round = ids
                        reading = state.subscribers.firstOrNull { it.customer.id == ids.first() }
                    })
                }
            }
        }
        if (state.subscribers.isEmpty()) item { T("لا مشتركين بعد. أضف أول مشترك من الزر في الأعلى.", size = 13.sp, color = p.mute, maxLines = 2) }
        items(state.subscribers, key = { it.customer.id }) { s ->
            val done = s.thisMonth != null
            val shape = RoundedCornerShape(18.dp)
            Row(
                Modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, if (done) p.ok.copy(alpha = .4f) else p.line, shape)
                    .clickable(role = Role.Button) { if (!done && canRead) reading = s else onOpenCustomer(s.customer.id) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    T(s.customer.name, size = 15.sp, weight = FontWeight.Medium)
                    T(
                        listOfNotNull(s.customer.meter.takeIf { it.isNotBlank() }?.let { "عداد ${ltr(it)}" }, "آخر قراءة ${ltr(money(s.last))}").joinToString(" · "),
                        size = 12.sp, color = p.mute,
                    )
                }
                T(
                    if (done) ltr("${money(s.thisMonth!!.kwh)} $KWH · ${cur(s.thisMonth.amount)}") else "بانتظار القراءة",
                    size = 13.sp, weight = FontWeight.SemiBold, color = if (done) p.ok else p.warn,
                )
            }
        }
    }

    reading?.let { s ->
        val next = round?.let { r -> r.getOrNull(r.indexOf(s.customer.id) + 1) }
        NumpadSheet(
            title = "قراءة ${s.customer.name}",
            subtitle = "القراءة السابقة ${ltr(money(s.last))} ${if (round != null) "· ${round!!.indexOf(s.customer.id) + 1} من ${round!!.size}" else ""}",
            accent = p.pri, confirm = if (next != null) "حفظ والتالي" else "حفظ الفاتورة",
            label = "القراءة الحالية", unit = KWH,
            preview = { curr ->
                when {
                    curr <= 0 -> null
                    curr < s.last -> "القراءة أقل من السابقة (${money(s.last)})" to true
                    else -> {
                        val (kwh, amount) = PowerState.bill(s.last, curr, price)
                        val odd = s.avgKwh > 0 && kwh > s.avgKwh * 3
                        "الاستهلاك ${money(kwh)} $KWH · المبلغ ${cur(amount)}${if (odd) " · استهلاك أعلى بكثير من المعتاد" else ""}" to false
                    }
                }
            },
            onDismiss = { reading = null; round = null },
        ) { curr, _ ->
            onReading(s.customer.id, s.last, round2(curr))
            if (next != null) {
                reading = state.subscribers.firstOrNull { it.customer.id == next }
            } else {
                round = null
            }
        }
    }

    if (adding) {
        var name by rememberSaveable { mutableStateOf("") }
        var phone by rememberSaveable { mutableStateOf("") }
        var meter by rememberSaveable { mutableStateOf("") }
        QSheet("مشترك كهرباء جديد", { adding = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QField(name, { name = it }, "الاسم", hint = "أم خالد")
                QField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, "رقم الجوال", hint = "0599123456", ltr = true, keyboard = KeyboardType.Phone)
                QField(meter, { meter = it }, "رقم العداد (اختياري)", ltr = true)
                QButton("التالي: قراءة البداية", {
                    if (name.isNotBlank()) { initialFor = Triple(name, phone, meter); adding = false }
                })
            }
        }
    }

    initialFor?.let { (n, ph, m) ->
        NumpadSheet(
            "قراءة البداية لـ $n", "الرقم الظاهر على العداد الآن", p.pri, "إضافة المشترك",
            initial = AmountInput.of(null), label = "القراءة", unit = KWH, onDismiss = { initialFor = null },
        ) { v, _ -> onAddSubscriber(n, ph, m, v) }
    }
}
