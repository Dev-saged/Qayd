package dev.saged.qayd.ui.team

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.data.AuditEntry
import dev.saged.qayd.data.Ledger
import dev.saged.qayd.data.Member
import dev.saged.qayd.data.Perm
import dev.saged.qayd.data.shift
import dev.saged.qayd.ui.components.IconTile
import dev.saged.qayd.ui.components.QButton
import dev.saged.qayd.ui.components.QSheet
import dev.saged.qayd.ui.components.QSwitchRow
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.theme.LocalPalette
import dev.saged.qayd.ui.util.clock
import dev.saged.qayd.ui.util.cur
import dev.saged.qayd.ui.util.dayLabel
import dev.saged.qayd.ui.util.ltr
import dev.saged.qayd.data.Role as MemberRole

/** الفريق: كل عضو مع تقرير ورديته اليوم، والضغط يفتح صلاحياته. */
@Composable
fun TeamPage(
    padding: PaddingValues,
    members: List<Member>,
    ledger: Ledger,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onSave: (Member, Set<Perm>, Boolean) -> Unit,
) {
    val p = LocalPalette.current
    var editing by remember { mutableStateOf<Member?>(null) }
    val today = Ledger.dayStart(System.currentTimeMillis())

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(QIcons.Back, "رجوع", onBack)
                T("الفريق", Modifier.weight(1f).padding(horizontal = 12.dp), 20.sp, FontWeight.Bold)
                IconTile(QIcons.UserPlus, "إضافة موظف", onAdd)
            }
        }
        item { T("وردية اليوم لكل عضو: ما باعه وما حصّله وما سجّله من ديون.", size = 12.5.sp, color = p.mute, maxLines = 2) }
        items(members.sortedBy { it.role != MemberRole.Owner }, key = { it.uid }) { m ->
            val s = ledger.shift(m.uid, today)
            val shape = RoundedCornerShape(18.dp)
            Column(
                Modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, if (m.active) p.line else p.danger.copy(alpha = .4f), shape)
                    .clickable(enabled = m.role != MemberRole.Owner, role = Role.Button) { editing = m }.padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    dev.saged.qayd.ui.components.Avatar(m.avatar, m.displayName, 42.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        T(m.displayName, size = 15.sp, weight = FontWeight.Medium)
                        T("${if (m.role == MemberRole.Owner) "صاحب المحل" else if (m.active) "موظف" else "موقوف"} · ${ltr(m.username)}", size = 12.sp, color = if (m.active) p.mute else p.danger)
                    }
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("مبيعات", cur(s.sales), Modifier.weight(1f))
                    Stat("تحصيل", cur(s.collected), Modifier.weight(1f))
                    Stat("ديون", cur(s.debts), Modifier.weight(1f))
                }
            }
        }
    }

    editing?.let { m -> PermsSheet(m, { perms, active -> onSave(m, perms, active); editing = null }) { editing = null } }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(p.surface2).padding(horizontal = 10.dp, vertical = 8.dp)) {
        T(label, size = 11.sp, color = p.mute)
        T(value, size = 13.5.sp, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun PermsSheet(m: Member, onSave: (Set<Perm>, Boolean) -> Unit, onDismiss: () -> Unit) {
    var perms by rememberSaveable(m.uid) { mutableStateOf(m.perms.map { it.key }.toSet()) }
    var active by rememberSaveable(m.uid) { mutableStateOf(m.active) }
    val repo = dev.saged.qayd.data.QaydRepo.get(androidx.compose.ui.platform.LocalContext.current)
    QSheet(
        "صلاحيات ${m.displayName}", onDismiss,
        footer = {
            QButton("حفظ الصلاحيات", { onSave(Perm.entries.filter { it.key in perms }.toSet(), active); onDismiss() })
        },
    ) {
        dev.saged.qayd.ui.components.QSwitchGroup(null, listOf(
            dev.saged.qayd.ui.components.SwitchItem("الحساب فعّال", active, { active = it }, if (active) "يدخل ويعمل حسب صلاحياته" else "موقوف: لا يستطيع الدخول"),
        ))
        Spacer(Modifier.height(12.dp))
        ResetPasswordPanel(m.username, m.uid) { repo.resetPassword(m.uid, it, true) }
        Perm.entries.groupBy { it.group }.forEach { (group, list) ->
            Spacer(Modifier.height(12.dp))
            dev.saged.qayd.ui.components.QSwitchGroup(group, list.map { pr ->
                dev.saged.qayd.ui.components.SwitchItem(pr.label, pr.key in perms, { on -> perms = if (on) perms + pr.key else perms - pr.key })
            })
        }
    }
}

/** سجل التدقيق: الحذف وتغيير الصلاحيات، من قام به ومتى. */
@Composable
fun AuditPage(padding: PaddingValues, entries: List<AuditEntry>, onBack: () -> Unit) {
    val p = LocalPalette.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(QIcons.Back, "رجوع", onBack)
                T("سجل التدقيق", Modifier.weight(1f).padding(horizontal = 12.dp), 20.sp, FontWeight.Bold)
            }
        }
        if (entries.isEmpty()) item { T("لا عمليات حساسة بعد.", Modifier.padding(top = 12.dp), 13.5.sp, color = p.mute) }
        items(entries.sortedByDescending { it.at }, key = { it.id }) { e ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("${e.action} · ${e.detail}", size = 14.sp, maxLines = 2)
                    T("${e.byName} · ${dayLabel(e.at)} ${clock(e.at)}", size = 11.5.sp, color = p.mute)
                }
                if (e.amount != null) T(cur(e.amount), size = 14.sp, weight = FontWeight.SemiBold, color = p.danger)
            }
        }
    }
}
