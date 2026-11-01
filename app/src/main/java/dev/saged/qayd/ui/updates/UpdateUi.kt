package dev.saged.qayd.ui.updates

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.system.AppUpdate
import dev.saged.qayd.system.Release
import dev.saged.qayd.system.Updates
import dev.saged.qayd.ui.components.IconTile
import dev.saged.qayd.ui.components.QButton
import dev.saged.qayd.ui.components.QIcon
import dev.saged.qayd.ui.components.QSheet
import dev.saged.qayd.ui.components.Ring
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.theme.LocalPalette

/** طبقة التحديث فوق التطبيق: نافذة "تحديث جديد" مرة لكل تشغيل، ومؤشر تنزيل صغير لا يعيق الاستخدام. */
@Composable
fun UpdateLayer(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val latest by Updates.latest.collectAsState()
    val progress by Updates.progress.collectAsState()
    val prompted by Updates.prompted.collectAsState()
    LaunchedEffect(Unit) { if (latest == null) Updates.check() }
    val u = latest
    if (u != null && !prompted && progress == null) {
        UpdateSheet(u, onUpdate = { Updates.prompted.value = true; Updates.start(ctx) }) { Updates.prompted.value = true }
    }
    Box(modifier.fillMaxWidth().statusBarsPadding().padding(top = 6.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(progress != null, enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically()) {
            DownloadPill(progress ?: 0f)
        }
    }
}

@Composable
private fun DownloadPill(f: Float) {
    val p = LocalPalette.current
    Row(
        Modifier.clip(CircleShape).background(p.surface).border(1.dp, p.pri.copy(alpha = .45f), CircleShape).padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ring(f, p.pri, p.line, Modifier.size(20.dp), 2.5.dp)
        T("جارٍ تنزيل التحديث ⁦${(f * 100).toInt()}%⁩", Modifier.padding(start = 8.dp), 11.5.sp, FontWeight.Medium)
    }
}

@Composable
private fun UpdateSheet(u: AppUpdate, onUpdate: () -> Unit, onLater: () -> Unit) {
    val p = LocalPalette.current
    val notes = remember(u.notes) { Updates.parse(u.version, u.notes) }
    QSheet("تحديث جديد", onLater) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(p.pri.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                QIcon(QIcons.Sync, p.pri, Modifier.size(24.dp))
            }
            Column(Modifier.padding(start = 12.dp)) {
                T("قيد ⁦${u.version}⁩", size = 18.sp, weight = FontWeight.SemiBold)
                T("بناء ⁦${u.build}⁩ · نسختك الحالية ⁦${Updates.current}⁩", Modifier.padding(top = 2.dp), 12.sp, color = p.mute)
            }
        }
        Spacer(Modifier.height(12.dp))
        Notes(notes, Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface2).padding(14.dp), limit = 8)
        Spacer(Modifier.height(16.dp))
        QButton("تحديث الآن", onUpdate)
        Spacer(Modifier.height(8.dp))
        QButton("لاحقاً", onLater, secondary = true)
        T("التنزيل يكمل في الخلفية وتقدر تستخدم التطبيق. تجد التحديثات دائماً في \"المزيد\".", Modifier.padding(top = 10.dp), 11.5.sp, color = p.mute, maxLines = 2)
    }
}

@Composable
private fun Notes(r: Release, modifier: Modifier, limit: Int = Int.MAX_VALUE) {
    val p = LocalPalette.current
    Column(modifier) {
        var shown = 0
        r.sections.forEach { s ->
            if (shown >= limit) return@forEach
            T(s.title, Modifier.padding(top = if (shown == 0) 0.dp else 10.dp, bottom = 4.dp), 13.sp, FontWeight.SemiBold, p.pri)
            s.items.forEach { (lvl, text) ->
                if (shown >= limit) return@forEach
                shown++
                Row(Modifier.padding(start = (lvl * 14).dp, top = 3.dp)) {
                    Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(if (lvl == 0) p.pri else p.mute))
                    T(text, Modifier.padding(start = 8.dp), 12.5.sp, maxLines = 4)
                }
            }
        }
    }
}

/** صفحة التحديثات: التحديث المتاح، وسجل كل الإصدارات ومميزاتها. */
@Composable
fun UpdatesPage(onBack: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val latest by Updates.latest.collectAsState()
    val progress by Updates.progress.collectAsState()
    val failed by Updates.failed.collectAsState()
    val history = remember { Updates.history(ctx) }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { Updates.check() }
    Box(Modifier.fillMaxSize().background(p.bg)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(QIcons.Back, "رجوع", onBack)
                    T("التحديثات", Modifier.weight(1f).padding(horizontal = 12.dp), 20.sp, FontWeight.Bold)
                }
            }
            item {
                val shape = RoundedCornerShape(22.dp)
                val u = latest
                Column(Modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, if (u != null) p.pri.copy(alpha = .5f) else p.line, shape).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QIcon(if (u != null) QIcons.Sync else QIcons.Check, if (u != null) p.pri else p.ok, Modifier.size(22.dp))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            T(if (u != null) "متاح تحديث ⁦${u.version}⁩" else "تطبيقك محدّث", size = 15.5.sp, weight = FontWeight.SemiBold)
                            T("نسختك الحالية ⁦${Updates.current}⁩", Modifier.padding(top = 2.dp), 12.sp, color = p.mute)
                        }
                    }
                    if (u != null) {
                        Notes(Updates.parse(u.version, u.notes), Modifier.padding(top = 12.dp).fillMaxWidth())
                        Spacer(Modifier.height(14.dp))
                        val f = progress
                        if (f != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Ring(f, p.pri, p.line, Modifier.size(26.dp), 3.dp)
                                T("جارٍ التنزيل ⁦${(f * 100).toInt()}%⁩", Modifier.padding(start = 10.dp), 13.sp, FontWeight.Medium)
                            }
                        } else {
                            QButton(if (failed) "أعد المحاولة" else "تحديث الآن", { Updates.start(ctx) })
                        }
                    }
                }
            }
            item { T("سجل الإصدارات", Modifier.padding(top = 6.dp), 14.5.sp, FontWeight.SemiBold) }
            items(history, key = { it.version }) { r ->
                val shape = RoundedCornerShape(20.dp)
                Column(Modifier.fillMaxWidth().clip(shape).background(p.surface).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("الإصدار ⁦${r.version}⁩", Modifier.weight(1f), 15.sp, FontWeight.SemiBold)
                        if (r.version == Updates.current) {
                            T("المثبّت", Modifier.clip(RoundedCornerShape(8.dp)).background(p.ok.copy(alpha = .15f)).padding(horizontal = 8.dp, vertical = 2.dp), 11.sp, FontWeight.SemiBold, p.ok)
                        }
                    }
                    Notes(r, Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
