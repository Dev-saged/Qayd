package dev.saged.qayd.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.R
import dev.saged.qayd.config.BrandConfig
import dev.saged.qayd.data.Validate
import dev.saged.qayd.ui.Op
import dev.saged.qayd.ui.components.Ambient
import dev.saged.qayd.ui.components.QButton
import dev.saged.qayd.ui.components.QField
import dev.saged.qayd.ui.components.QNotice
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.components.entrance
import dev.saged.qayd.ui.theme.LocalPalette

@Composable
fun LoginScreen(op: Op, onSubmit: (String, String) -> Unit) {
    val p = LocalPalette.current
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    val userErr = if (tried) Validate.login(user.trim()) else null
    val passErr = if (tried && pass.isEmpty()) "اكتب كلمة السر" else null
    val submit = {
        tried = true
        if (Validate.login(user.trim()) == null && pass.isNotEmpty()) onSubmit(user, pass)
    }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        Ambient()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(40.dp))
            Box(
                Modifier
                    .entrance(0)
                    .size(104.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(p.surface2, p.surface))),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.qayd_logo), contentDescription = BrandConfig.NAME, Modifier.size(112.dp))
            }
            Spacer(Modifier.height(18.dp))
            T(BrandConfig.NAME, Modifier.entrance(1), 34.sp, FontWeight.Bold)
            T(BrandConfig.TAGLINE, Modifier.entrance(1), 14.sp, color = p.mute)
            Spacer(Modifier.height(36.dp))

            Column(Modifier.entrance(2), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                QField(
                    value = user, onChange = { user = it.trim() }, label = "اسم المستخدم",
                    hint = "ahmad@anoar.ps", ltr = true, keyboard = KeyboardType.Email,
                    error = userErr, enabled = !op.busy,
                )
                QField(
                    value = pass, onChange = { pass = it }, label = "كلمة السر",
                    ltr = true, password = true, error = passErr, enabled = !op.busy, onDone = submit,
                )
                op.error?.let { QNotice(it, error = true) }
                Spacer(Modifier.height(4.dp))
                QButton("دخول", submit, busy = op.busy, busyText = "جارٍ الدخول…")
            }
            Spacer(Modifier.height(20.dp))
            T("أول دخول يحتاج إنترنت، وبعدها يعمل بدونه ويزامن وحده.", Modifier.entrance(3), 12.sp, color = p.mute, maxLines = 2)
            Spacer(Modifier.height(40.dp))
        }
    }
}
