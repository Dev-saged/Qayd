package dev.saged.qayd.ui.team

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.ui.components.QButton
import dev.saged.qayd.ui.components.QField
import dev.saged.qayd.ui.components.QNotice
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.theme.LocalPalette
import kotlinx.coroutines.launch
import java.security.SecureRandom

private const val ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"

/** كلمة سر عشوائية بلا حروف متشابهة (l,1,O,0) لسهولة قراءتها وإملائها. */
fun suggestPassword(): String = SecureRandom().let { r -> (1..10).map { ALPHABET[r.nextInt(ALPHABET.length)] }.joinToString("") }

/** إعادة تعيين كلمة سر حساب: اقتراح قوي قابل للتعديل، ثم نسخ أو مشاركة النتيجة. */
@Composable
fun ResetPasswordPanel(account: String, key: Any, reset: suspend (String) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pass by rememberSaveable(key) { mutableStateOf(suggestPassword()) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var done by remember { mutableStateOf(false) }
    val text = "بيانات الدخول إلى قيد\nالحساب: $account\nكلمة السر: $pass"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        T("كلمة سر جديدة", size = 14.sp, weight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        T("كلمة السر القديمة لا يمكن عرضها. تُعيَّن جديدة ويُسجَّل خروج الحساب من أجهزته.", size = 12.sp, color = p.mute, maxLines = 2)
        QField(pass, { pass = it.trim(); done = false; msg = null }, "كلمة السر الجديدة", ltr = true, enabled = !busy)
        msg?.let { (t, e) -> QNotice(t, error = e) }
        QButton(if (done) "تغيير مرة أخرى" else "تعيين كلمة السر", {
            if (pass.length < 8) { msg = "8 أحرف على الأقل" to true; return@QButton }
            busy = true
            scope.launch {
                msg = runCatching { reset(pass) }.fold(
                    { done = true; "تم التعيين. انسخ البيانات وأرسلها لصاحب الحساب." to false },
                    { (it.message ?: "تعذر") to true },
                )
                busy = false
            }
        }, Modifier.fillMaxWidth(), busy = busy)
        if (done) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QButton("نسخ", {
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("qayd", text))
                msg = "تم النسخ" to false
            }, Modifier.weight(1f), secondary = true)
            QButton("مشاركة", {
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }, Modifier.weight(1f), secondary = true)
        }
    }
}
