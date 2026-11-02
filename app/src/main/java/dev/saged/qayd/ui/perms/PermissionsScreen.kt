package dev.saged.qayd.ui.perms

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.saged.qayd.config.BrandConfig
import dev.saged.qayd.system.Perms
import dev.saged.qayd.system.Perms.Key
import dev.saged.qayd.ui.components.Ambient
import dev.saged.qayd.ui.components.QButton
import dev.saged.qayd.ui.components.QIcon
import dev.saged.qayd.ui.components.QSheet
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.components.entrance
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.theme.LocalPalette

private fun meta(k: Key): Triple<ImageVector, String, String> = when (k) {
    Key.Notify -> Triple(QIcons.Bell, "الإشعارات", "تنبيه انتهاء وقت الشاشات وحالة جوال الإرسال")
    Key.Sms -> Triple(QIcons.Msg, "إرسال الرسائل", "ليعمل هذا الجوال جوال إرسال رسائل المحل")
    Key.Phone -> Triple(QIcons.Phone, "حالة الشريحة", "لمعرفة الشريحة والتغطية قبل الإرسال")
    Key.Mic -> Triple(QIcons.Mic, "الميكروفون", "للإدخال بالصوت: سجّل وأنت مشغول وراجع لاحقاً")
    Key.Storage -> Triple(QIcons.Folder, "حفظ الملفات", "حفظ النسخ الاحتياطية والفواتير في مجلد ${BrandConfig.NAME_LATIN}")
    Key.Battery -> Triple(QIcons.Bolt, "العمل في الخلفية", "حتى لا يوقف النظام الإرسال والمنبهات")
    Key.Install -> Triple(QIcons.Sync, "تثبيت التحديثات", "ليثبّت التطبيق تحديثاته بنفسه")
    Key.Alarms -> Triple(QIcons.Clock, "المنبهات الدقيقة", "تنبيه الشاشات في وقتها بالضبط")
}

/** صلاحيات التطبيق: تُعرض عند أول فتح، ومن "المزيد" لاحقاً. */
@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var chain by remember { mutableStateOf(emptyList<Key>()) }
    var smsTried by remember { mutableStateOf(false) }
    var restrictedOpen by remember { mutableStateOf(false) }
    val keys = Perms.applicable()
    val state = remember(tick) { keys.associateWith { Perms.granted(ctx, it) } }

    fun nextSetting() {
        val k = chain.firstOrNull { !Perms.granted(ctx, it) }
        chain = if (k == null) emptyList() else chain.dropWhile { it != k }.drop(1)
        k?.let { Perms.settingsIntent(ctx, it) }?.let { runCatching { ctx.startActivity(it) } }
    }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick++
        if (Key.Sms in keys && !Perms.granted(ctx, Key.Sms)) smsTried = true
        nextSetting()
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { tick++; if (chain.isNotEmpty()) nextSetting() } }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    fun grant(k: Key) {
        val rt = Perms.runtime(k)
        if (rt.isNotEmpty()) ask.launch(rt.toTypedArray()) else Perms.settingsIntent(ctx, k)?.let { runCatching { ctx.startActivity(it) } }
    }

    fun grantAll() {
        val rt = keys.filter { !Perms.granted(ctx, it) }.flatMap { Perms.runtime(it) }
        chain = keys.filter { Perms.runtime(it).isEmpty() && !Perms.granted(ctx, it) }
        if (rt.isNotEmpty()) ask.launch(rt.toTypedArray()) else nextSetting()
    }

    val missing = state.count { !it.value }
    Box(Modifier.fillMaxSize().background(p.bg)) {
        Ambient()
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            T("الصلاحيات", Modifier.entrance(0).padding(top = 18.dp), 26.sp, FontWeight.Bold)
            T(
                if (missing == 0) "كل الصلاحيات ممنوحة، و${BrandConfig.NAME} جاهز بالكامل." else "امنح ${BrandConfig.NAME} هذه الصلاحيات مرة واحدة ليعمل كاملاً.",
                Modifier.entrance(1).padding(bottom = 6.dp), 14.sp, color = p.mute, maxLines = 2,
            )
            keys.forEachIndexed { i, k ->
                val (icon, title, why) = meta(k)
                val ok = state[k] == true
                val shape = RoundedCornerShape(20.dp)
                Row(
                    Modifier.entrance(2 + i).fillMaxWidth().clip(shape).background(p.surface).border(1.dp, if (ok) p.ok.copy(alpha = .35f) else p.line, shape)
                        .clickable(enabled = !ok, role = Role.Button) { grant(k) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(p.surface2), contentAlignment = Alignment.Center) {
                        QIcon(icon, if (ok) p.ok else p.pri, Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        T(title, size = 15.sp, weight = FontWeight.SemiBold)
                        T(why, size = 12.5.sp, color = p.mute, maxLines = 2)
                    }
                    if (ok) QIcon(QIcons.Check, p.ok, Modifier.size(22.dp))
                    else T("منح", Modifier.clip(RoundedCornerShape(12.dp)).background(p.pri.copy(alpha = .16f)).padding(horizontal = 14.dp, vertical = 8.dp), 13.sp, FontWeight.SemiBold, p.pri)
                }
            }
            if (smsTried && state[Key.Sms] == false) RestrictedCard { restrictedOpen = true }
            Spacer(Modifier.height(8.dp))
            if (missing > 0) QButton("منح كل الصلاحيات", ::grantAll)
            QButton(if (missing == 0) "متابعة" else "لاحقاً", onDone, secondary = missing > 0)
        }
    }
    if (restrictedOpen) RestrictedSmsSheet { restrictedOpen = false }
}

@Composable
private fun RestrictedCard(onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(p.warn.copy(alpha = .12f)).border(1.dp, p.warn.copy(alpha = .4f), shape).clickable(role = Role.Button, onClick = onClick).padding(14.dp)) {
        T("الجوال قيّد صلاحية الرسائل", size = 14.5.sp, weight = FontWeight.SemiBold, color = p.warn)
        T("يحدث هذا مرة واحدة لأن التطبيق ثُبّت من ملف. اضغط لترى خطوات رفع القيد.", Modifier.padding(top = 4.dp), 12.5.sp, color = p.ink, maxLines = 3)
    }
}

/** خطوات السماح بالإعدادات المقيدة لصلاحية الرسائل. */
@Composable
fun RestrictedSmsSheet(onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    QSheet("رفع القيد عن الرسائل", onDismiss) {
        listOf(
            "اضغط الزر بالأسفل لفتح معلومات التطبيق.",
            "اضغط النقاط الثلاث أعلى الشاشة.",
            "اختر \"السماح بالإعدادات المقيدة\" وأكّد ببصمتك أو رمز القفل.",
            "ارجع إلى ${BrandConfig.NAME} وامنح صلاحية الرسائل.",
        ).forEachIndexed { i, s ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(p.pri.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                    T("${i + 1}", size = 13.sp, weight = FontWeight.Bold, color = p.pri)
                }
                T(s, Modifier.padding(start = 10.dp), 13.5.sp, maxLines = 2)
            }
        }
        T("بعدها تصل التحديثات من داخل التطبيق دون أن يتكرر القيد.", Modifier.padding(top = 6.dp), 12.sp, color = p.mute, maxLines = 2)
        Spacer(Modifier.height(14.dp))
        QButton("فتح معلومات التطبيق", { runCatching { ctx.startActivity(Perms.appInfo(ctx)) } })
    }
}
