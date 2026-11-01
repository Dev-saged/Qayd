package dev.saged.qayd.system

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** قفل التطبيق بالبصمة أو الوجه، ورمز قفل الجوال بديلاً. */
object AppLock {
    private const val PREFS = "qayd_lock"
    private const val AFTER_MS = 60_000L

    fun enabled(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", false)
    fun setEnabled(c: Context, on: Boolean) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()
    fun available(c: Context) =
        BiometricManager.from(c).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    /** هل غاب التطبيق مدة تكفي لطلب القفل من جديد. */
    fun shouldLock(hiddenAt: Long, now: Long = System.currentTimeMillis()) = hiddenAt > 0 && now - hiddenAt > AFTER_MS

    fun prompt(activity: FragmentActivity, onOk: () -> Unit) {
        val p = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
        })
        p.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("افتح قيد")
                .setSubtitle("بالبصمة أو برمز قفل الجوال")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build(),
        )
    }
}
