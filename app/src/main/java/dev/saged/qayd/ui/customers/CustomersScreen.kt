package dev.saged.qayd.ui.customers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.data.Customer
import dev.saged.qayd.data.CustomerBalance
import dev.saged.qayd.data.Ledger
import dev.saged.qayd.ui.components.IconTile
import dev.saged.qayd.ui.components.QField
import dev.saged.qayd.ui.components.QIcon
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.components.entrance
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.theme.LocalPalette
import dev.saged.qayd.ui.util.balanceText
import dev.saged.qayd.ui.util.cur
import dev.saged.qayd.ui.util.dayLabel
import dev.saged.qayd.ui.util.ltr
import dev.saged.qayd.ui.util.norm

private enum class Sort(val label: String) { Debt("الأكثر ديناً"), Recent("الأحدث"), Alpha("أبجدي"), Late("متأخرون") }

@Composable
fun CustomersScreen(padding: PaddingValues, ledger: Ledger, onOpen: (Customer) -> Unit, onAdd: () -> Unit) {
    val p = LocalPalette.current
    var q by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(Sort.Debt) }
    val list = ledger.customers
        .filter { q.isBlank() || norm(it.customer.name).contains(norm(q)) || it.customer.phone.contains(q.trim()) }
        .let { l ->
            when (sort) {
                Sort.Debt -> l.sortedByDescending { it.balance }
                Sort.Recent -> l.sortedByDescending { it.lastAt }
                Sort.Alpha -> l.sortedBy { norm(it.customer.name) }
                Sort.Late -> l.filter { it.late }.sortedByDescending { it.balance }
            }
        }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.entrance(0), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("الزبائن", size = 22.sp, weight = FontWeight.Bold)
                    T("${ledger.customers.size} زبون · عليهم ${cur(ledger.totalDebt)}", size = 12.5.sp, color = p.mute)
                }
                IconTile(QIcons.UserPlus, "زبون جديد", onAdd)
            }
        }
        item { QField(q, { q = it }, "بحث", hint = "الاسم أو رقم الجوال", modifier = Modifier.entrance(1)) }
        item {
            Row(Modifier.entrance(2).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sort.entries.forEach { s ->
                    val on = s == sort
                    T(
                        if (s == Sort.Late && ledger.late > 0) "${s.label} ${ledger.late}" else s.label,
                        Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) p.pri.copy(alpha = .18f) else p.surface)
                            .border(1.dp, if (on) p.pri else p.line, RoundedCornerShape(14.dp))
                            .clickable(role = Role.RadioButton) { sort = s }.padding(horizontal = 14.dp, vertical = 9.dp),
                        13.sp, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) p.pri else p.ink,
                    )
                }
            }
        }
        if (list.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    QIcon(QIcons.Users, p.pri, Modifier.size(34.dp))
                    Spacer(Modifier.height(10.dp))
                    T(if (ledger.customers.isEmpty()) "لا يوجد زبائن بعد" else "لا نتائج", size = 16.sp, weight = FontWeight.SemiBold)
                    if (ledger.customers.isEmpty()) T("أضف أول زبون من الزر في الأعلى.", size = 13.sp, color = p.mute)
                }
            }
        }
        items(list, key = { it.customer.id }) { c -> CustomerRow(c, onOpen) }
    }
}

@Composable
private fun CustomerRow(c: CustomerBalance, onOpen: (Customer) -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, if (c.late) p.warn.copy(alpha = .5f) else p.line, shape)
            .clickable(role = Role.Button) { onOpen(c.customer) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(p.surface2), contentAlignment = Alignment.Center) {
            T(c.customer.name.take(1), size = 16.sp, weight = FontWeight.SemiBold, color = p.pri)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            T(c.customer.name, size = 15.sp, weight = FontWeight.Medium)
            T(
                listOfNotNull(c.customer.phone.takeIf { it.isNotBlank() }?.let(::ltr), dayLabel(c.lastAt), if (c.late) "متأخر" else null).joinToString(" · "),
                size = 12.sp, color = if (c.late) p.warn else p.mute,
            )
        }
        val (txt, col) = balanceText(c.balance, p)
        T(txt, size = 14.sp, weight = FontWeight.SemiBold, color = col)
    }
}
