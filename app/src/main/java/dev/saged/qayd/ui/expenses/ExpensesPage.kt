package dev.saged.qayd.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.data.ExpenseCategory
import dev.saged.qayd.data.Ledger
import dev.saged.qayd.data.RecurringStatus
import dev.saged.qayd.data.Tx
import dev.saged.qayd.ui.components.IconTile
import dev.saged.qayd.ui.components.QButton
import dev.saged.qayd.ui.components.QField
import dev.saged.qayd.ui.components.QIcon
import dev.saged.qayd.ui.components.QSheet
import dev.saged.qayd.ui.components.QStepper
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.components.entrance
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.theme.LocalPalette
import dev.saged.qayd.ui.util.cur
import dev.saged.qayd.ui.util.dayLabel

/** المصروفات: إجمالي الشهر حسب الفئة، الدفعات الشهرية الثابتة وحالتها، وسجل الشهر. */
@Composable
fun ExpensesPage(
    padding: PaddingValues,
    ledger: Ledger,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onPay: (RecurringStatus) -> Unit,
    onAddRecurring: (String, ExpenseCategory, Int) -> Unit,
    onDeleteRecurring: (String) -> Unit,
    onTxTap: (Tx) -> Unit,
) {
    val p = LocalPalette.current
    var adding by rememberSaveable { mutableStateOf(false) }
    val net = ledger.monthSales - ledger.monthExpenses

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.entrance(0), verticalAlignment = Alignment.CenterVertically) {
                IconTile(QIcons.Back, "رجوع", onBack)
                T("المصروفات", Modifier.weight(1f).padding(horizontal = 12.dp), 20.sp, FontWeight.Bold)
                IconTile(QIcons.Plus, "مصروف جديد", onAdd)
            }
        }
        item {
            val shape = RoundedCornerShape(24.dp)
            Column(
                Modifier.entrance(1).fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(p.surface, p.surface2)))
                    .border(1.dp, p.line, shape).padding(18.dp),
            ) {
                T("مصروفات هذا الشهر", size = 13.sp, color = p.mute)
                T(cur(ledger.monthExpenses), size = 32.sp, weight = FontWeight.SemiBold)
                T(
                    "مبيعات الشهر ${cur(ledger.monthSales)} · الفرق ${cur(net)}",
                    size = 12.5.sp, color = if (net >= 0) p.ok else p.danger, maxLines = 2,
                )
                val max = ledger.monthByCategory.values.maxOrNull() ?: 0.0
                ExpenseCategory.entries.filter { (ledger.monthByCategory[it] ?: 0.0) > 0 }.forEach { cat ->
                    val v = ledger.monthByCategory[cat] ?: 0.0
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(cat.label, Modifier.weight(.35f), 12.5.sp)
                        Box(
                            Modifier.weight(.4f).height(8.dp).drawBehind {
                                drawRoundRect(p.line, cornerRadius = CornerRadius(8f))
                                drawRoundRect(p.pri, size = Size(size.width * (v / max).toFloat(), size.height), cornerRadius = CornerRadius(8f))
                            },
                        )
                        T(cur(v), Modifier.weight(.25f).padding(start = 8.dp), 12.5.sp, FontWeight.Medium)
                    }
                }
            }
        }

        item {
            Row(Modifier.entrance(2).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                T("دفعات شهرية ثابتة", Modifier.weight(1f), 14.5.sp, FontWeight.SemiBold)
                T("+ إضافة", Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { adding = true }.padding(8.dp), 13.sp, FontWeight.Medium, p.pri)
            }
        }
        if (ledger.recurring.isEmpty()) {
            item { T("أضف ما تدفعه كل شهر: اشتراك الإنترنت، كهرباء المحل، الإيجار، الرواتب.", size = 13.sp, color = p.mute, maxLines = 2) }
        }
        items(ledger.recurring, key = { it.item.id }) { r -> RecurringRow(r, { onPay(r) }, { onDeleteRecurring(r.item.id) }) }

        item { T("سجل الشهر", Modifier.padding(top = 6.dp), 14.5.sp, FontWeight.SemiBold) }
        if (ledger.monthExpenseList.isEmpty()) item { T("لا مصروفات هذا الشهر.", size = 13.sp, color = p.mute) }
        items(ledger.monthExpenseList, key = { it.id }) { t ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface)
                    .clickable(role = Role.Button, onClickLabel = "خيارات") { onTxTap(t) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(p.surface2), contentAlignment = Alignment.Center) {
                    QIcon(QIcons.Wallet, p.pri, Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    T(ledger.recurring.firstOrNull { it.item.id == t.recurringId }?.item?.title ?: (t.category ?: ExpenseCategory.Other).label, size = 14.sp)
                    T("${dayLabel(t.at)}${if (t.byName.isNotBlank()) " · ${t.byName}" else ""}", size = 11.5.sp, color = p.mute)
                }
                T(cur(t.amount), size = 14.5.sp, weight = FontWeight.SemiBold)
            }
        }
    }

    if (adding) RecurringSheet({ t, c, d -> onAddRecurring(t, c, d); adding = false }) { adding = false }
}

@Composable
private fun RecurringRow(r: RecurringStatus, onPay: () -> Unit, onDelete: () -> Unit) {
    val p = LocalPalette.current
    val paid = r.paid != null
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, if (paid) p.ok.copy(alpha = .4f) else p.line, shape).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T(r.item.title, size = 14.5.sp, weight = FontWeight.Medium)
            T(
                if (paid) "دُفع هذا الشهر ${cur(r.paid!!.amount)}" else "يوم ${r.item.day} من كل شهر · ${r.item.category.label}",
                size = 12.sp, color = if (paid) p.ok else p.mute,
            )
        }
        if (!paid) {
            T("ادفع", Modifier.clip(RoundedCornerShape(12.dp)).background(p.pri).clickable(role = Role.Button, onClick = onPay)
                .padding(horizontal = 16.dp, vertical = 10.dp), 13.5.sp, FontWeight.SemiBold, p.onPri)
        }
        IconTile(QIcons.Trash, "حذف ${r.item.title}", onDelete, Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun RecurringSheet(onSave: (String, ExpenseCategory, Int) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var title by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf(ExpenseCategory.Internet) }
    var day by rememberSaveable { mutableStateOf(1) }
    QSheet("دفعة شهرية ثابتة", onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            QField(title, { title = it }, "الاسم", hint = "اشتراك الإنترنت")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpenseCategory.entries.forEach { c ->
                    val on = c == cat
                    T(
                        c.label,
                        Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) p.pri.copy(alpha = .18f) else p.surface2)
                            .border(1.dp, if (on) p.pri else p.line, RoundedCornerShape(14.dp))
                            .clickable(role = Role.RadioButton) { cat = c; if (title.isBlank()) title = c.label }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        13.sp, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) p.pri else p.ink,
                    )
                }
            }
            QStepper("يوم الاستحقاق", day, 1..28) { day = it }
            Spacer(Modifier.height(4.dp))
            QButton("حفظ", { if (title.isNotBlank()) onSave(title, cat, day) })
        }
    }
}
