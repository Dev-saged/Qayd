package dev.saged.qayd.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.data.Report
import dev.saged.qayd.ui.components.IconTile
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.theme.LocalPalette
import dev.saged.qayd.ui.util.cur
import java.util.Calendar

private val DAYS = listOf("أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت")

/** تقارير المحل: مبيعات الأسبوع، أرقام الشهر، الشاشات والكهرباء، أكبر المدينين، وأداء الفريق. */
@Composable
fun ReportsPage(padding: PaddingValues, r: Report, onBack: () -> Unit) {
    val p = LocalPalette.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(QIcons.Back, "رجوع", onBack)
                T("التقارير", Modifier.weight(1f).padding(horizontal = 12.dp), 20.sp, FontWeight.Bold)
            }
        }
        item {
            Card {
                T("مبيعات آخر 7 أيام", size = 13.sp, color = p.mute)
                T(cur(r.week.sumOf { it.second }), size = 24.sp, weight = FontWeight.SemiBold)
                val max = r.week.maxOf { it.second }.takeIf { it > 0 } ?: 1.0
                // الأقدم يميناً والأحدث يساراً، كقراءة الواجهة العربية
                Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 12.dp)) {
                    val gap = 10.dp.toPx()
                    val bw = (size.width - gap * (r.week.size - 1)) / r.week.size
                    r.week.forEachIndexed { i, (_, v) ->
                        val x = size.width - (i + 1) * bw - i * gap
                        val h = (size.height * (v / max)).toFloat().coerceAtLeast(4f)
                        drawRoundRect(Brush.verticalGradient(listOf(p.pri, p.pri.copy(alpha = .45f))), Offset(x, size.height - h), Size(bw, h), CornerRadius(8f))
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    r.week.forEach { (day, _) ->
                        T(DAYS[Calendar.getInstance().apply { timeInMillis = day }.get(Calendar.DAY_OF_WEEK) - 1], Modifier.weight(1f), 10.5.sp, color = p.mute)
                    }
                }
            }
        }
        item {
            Card {
                T("هذا الشهر", size = 13.sp, color = p.mute)
                Line("المبيعات", cur(r.monthSales))
                Line("التحصيل من الزبائن", cur(r.monthCollected))
                Line("ديون جديدة", cur(r.monthDebts), p.danger)
                Line("المصروفات", cur(r.monthExpenses), p.danger)
                Line("الدخل النقدي", cur(r.income), bold = true)
                Line("الصافي بعد المصروفات", cur(r.net), if (r.net >= 0) p.ok else p.danger, bold = true)
                if (r.psMonth > 0) Line("دخل الشاشات", cur(r.psMonth))
                if (r.powerMonth > 0) Line("فواتير الكهرباء", cur(r.powerMonth))
                if (r.monthDiscounts > 0) Line("الخصومات الممنوحة", cur(r.monthDiscounts), p.warn)
            }
        }
        if (r.byWallet.size > 1 || r.byWallet.any { it.first != "نقدي" }) item {
            Card {
                T("الدخل حسب طريقة الدفع", size = 13.sp, color = p.mute)
                r.byWallet.forEach { (w, v) -> Line(w, cur(v)) }
            }
        }
        if (r.topCustomers.isNotEmpty()) item {
            Card {
                T("أهم الزبائن (آخر 90 يوماً)", size = 13.sp, color = p.mute)
                r.topCustomers.forEach { (c, paid) -> Line(c.customer.name, "دفع ${cur(paid)}", p.ok) }
            }
        }
        if (r.topDebtors.isNotEmpty()) item {
            Card {
                T("أكبر المدينين", size = 13.sp, color = p.mute)
                r.topDebtors.forEach { Line(it.customer.name, cur(it.balance), p.danger) }
            }
        }
        if (r.byMember.isNotEmpty()) item {
            Card {
                T("الفريق هذا الشهر (مبيعات + تحصيل)", size = 13.sp, color = p.mute)
                r.byMember.forEach { (n, v) -> Line(n, cur(v)) }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, p.line, shape).padding(16.dp)) { content() }
}

@Composable
private fun Line(label: String, value: String, color: Color = LocalPalette.current.ink, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, Modifier.weight(1f), 14.sp, if (bold) FontWeight.SemiBold else FontWeight.Normal)
        T(value, size = 14.sp, weight = FontWeight.SemiBold, color = color)
    }
}
