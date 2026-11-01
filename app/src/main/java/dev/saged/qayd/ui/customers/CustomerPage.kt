package dev.saged.qayd.ui.customers

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.data.Customer
import dev.saged.qayd.data.Ledger
import dev.saged.qayd.data.Tx
import dev.saged.qayd.data.TxKind
import dev.saged.qayd.ui.components.IconTile
import dev.saged.qayd.ui.components.QButton
import dev.saged.qayd.ui.components.QIcon
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.components.entrance
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.theme.LocalPalette
import dev.saged.qayd.ui.util.clock
import dev.saged.qayd.ui.util.cur
import dev.saged.qayd.ui.util.dayLabel
import dev.saged.qayd.ui.util.ltr

/** صفحة الزبون: رصيده، دين ودفعة بالنمباد، وسجله مع الرصيد بعد كل حركة. */
@Composable
fun CustomerPage(
    padding: PaddingValues,
    customer: Customer,
    ledger: Ledger,
    onBack: () -> Unit,
    onDebt: (() -> Unit)?,
    onPayment: (() -> Unit)?,
    onTxTap: (Tx) -> Unit,
    showBalance: Boolean = true,
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val b = ledger.balanceOf(customer.id)?.balance ?: 0.0
    val history = ledger.history(customer.id)
    // الرصيد بعد كل حركة: من الأقدم للأحدث ثم يُعرض الأحدث أولاً
    val after = HashMap<String, Double>().also { m ->
        var run = 0.0
        history.asReversed().forEach { t -> run += if (t.kind == TxKind.Debt) t.amount else -t.amount; m[t.id] = run }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.entrance(0), verticalAlignment = Alignment.CenterVertically) {
                IconTile(QIcons.Back, "رجوع", onBack)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    T(customer.name, size = 19.sp, weight = FontWeight.Bold)
                    if (customer.phone.isNotBlank()) T(ltr(customer.phone), size = 12.5.sp, color = p.mute)
                }
                if (customer.phone.isNotBlank()) {
                    IconTile(QIcons.Phone, "اتصال", {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone}"))) }
                    })
                }
            }
        }
        if (showBalance) item {
            val c = when { b > 0 -> p.danger; b < 0 -> p.ok; else -> p.mute }
            val shape = RoundedCornerShape(24.dp)
            Column(
                Modifier.entrance(1).fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(p.surface, p.surface2)))
                    .border(1.dp, c.copy(alpha = .45f), shape).padding(18.dp),
            ) {
                T(when { b > 0 -> "الرصيد المستحق عليه"; b < 0 -> "رصيد مقدم له"; else -> "الحساب مسدّد" }, size = 13.sp, color = p.mute)
                T(cur(kotlin.math.abs(b)), size = 36.sp, weight = FontWeight.SemiBold, color = c)
                T(
                    "مجموع الديون ${cur(history.filter { it.kind == TxKind.Debt }.sumOf { it.amount })} · الدفعات ${cur(history.filter { it.kind == TxKind.Payment }.sumOf { it.amount })}",
                    size = 12.sp, color = p.mute, maxLines = 2,
                )
            }
        }
        item {
            Row(Modifier.entrance(2), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onDebt != null) QButton("دين", onDebt, Modifier.weight(1f))
                if (onPayment != null) QButton("دفعة", onPayment, Modifier.weight(1f), secondary = true)
            }
        }
        item { T("السجل", Modifier.entrance(3).padding(top = 6.dp), 14.5.sp, FontWeight.SemiBold) }
        if (history.isEmpty()) item { T("لا حركات بعد.", Modifier.padding(vertical = 12.dp), 13.sp, color = p.mute) }
        items(history, key = { it.id }) { t ->
            val debt = t.kind == TxKind.Debt
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface)
                    .clickable(role = Role.Button, onClickLabel = "خيارات") { onTxTap(t) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(p.surface2), contentAlignment = Alignment.Center) {
                    QIcon(if (debt) QIcons.Up else QIcons.Down, if (debt) p.danger else p.ok, Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    T("${t.kind.label} · ${dayLabel(t.at)} ${clock(t.at)}", size = 13.5.sp)
                    T("الرصيد بعدها ${cur(after[t.id] ?: 0.0)}${if (t.byName.isNotBlank()) " · ${t.byName}" else ""}", size = 11.5.sp, color = p.mute)
                }
                if (t.pending) Box(Modifier.size(6.dp).clip(CircleShape).background(p.warn))
                Spacer(Modifier.width(8.dp))
                T(ltr((if (debt) "+" else "−") + cur(t.amount)), size = 14.5.sp, weight = FontWeight.SemiBold, color = if (debt) p.danger else p.ok)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
