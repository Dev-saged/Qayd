package dev.saged.qayd.system

import android.content.Context
import android.util.Base64
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * خزنة الحسابات: كل حساب دخل على الجهاز له نسخة Firebase مستقلة بجلسة محفوظة وكاش كامل لبيانات محله،
 * فيعود للحساب بدون إنترنت. كلمة السر تُتحقق محلياً ببصمة PBKDF2 ولا تُخزن.
 */
object AccountVault {
    const val DEFAULT = FirebaseApp.DEFAULT_APP_NAME
    private const val PREFS = "qayd_vault"
    private const val ROUNDS = 120_000

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** اسم نسخة Firebase لهذا البريد: المسجّلة سابقاً أو اسم جديد ثابت. */
    fun appName(c: Context, email: String): String =
        p(c).getString("app:$email", null) ?: ("acc_" + sha(email).take(16))

    fun app(c: Context, name: String): FirebaseApp {
        FirebaseApp.getApps(c).firstOrNull { it.name == name }?.let { return it }
        val base = FirebaseApp.getInstance()
        val app = if (name == DEFAULT) base else FirebaseApp.initializeApp(c, base.options, name)
        tune(FirebaseFirestore.getInstance(app))
        return app
    }

    /** كاش دائم بلا حد حتى لا تُحذف بيانات المحل القديمة من الجهاز. */
    fun tune(db: FirebaseFirestore) = runCatching {
        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED).build())
            .build()
    }

    fun active(c: Context): String? = p(c).getString("active", null)
    fun setActive(c: Context, name: String?) = p(c).edit().putString("active", name).apply()

    /** الجلسة القديمة (قبل الخزنة) تبقى على النسخة الافتراضية. */
    fun migrate(c: Context) {
        if (p(c).getBoolean("migrated", false)) return
        val u = FirebaseAuth.getInstance().currentUser
        val e = p(c).edit().putBoolean("migrated", true)
        if (u != null) e.putString("app:${u.email.orEmpty().lowercase()}", DEFAULT).putString("active", DEFAULT)
        e.apply()
    }

    fun remember(c: Context, email: String, name: String, pass: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        p(c).edit().putString("app:$email", name)
            .putString("salt:$email", b64(salt)).putString("hash:$email", b64(derive(pass, salt))).apply()
    }

    fun check(c: Context, email: String, pass: String): Boolean {
        val salt = p(c).getString("salt:$email", null) ?: return false
        val hash = p(c).getString("hash:$email", null) ?: return false
        return MessageDigest.isEqual(derive(pass, Base64.decode(salt, Base64.NO_WRAP)), Base64.decode(hash, Base64.NO_WRAP))
    }

    /** إزالة الحساب من الجهاز كلياً: الجلسة والكاش والبصمة. */
    fun forget(c: Context, email: String) {
        val name = p(c).getString("app:$email", null) ?: return
        val app = runCatching { app(c, name) }.getOrNull()
        if (app != null) {
            FirebaseAuth.getInstance(app).signOut()
            if (name != DEFAULT) {
                val db = FirebaseFirestore.getInstance(app)
                db.terminate().addOnCompleteListener { db.clearPersistence().addOnCompleteListener { runCatching { app.delete() } } }
            }
        }
        c.getSharedPreferences(sessionPrefs(name), Context.MODE_PRIVATE).edit().clear().apply()
        val e = p(c).edit().remove("app:$email").remove("salt:$email").remove("hash:$email")
        if (active(c) == name) e.remove("active")
        e.apply()
    }

    fun sessionPrefs(name: String) = if (name == DEFAULT) "qayd_session" else "qayd_session_$name"

    private fun derive(pass: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pass.toCharArray(), salt, ROUNDS, 256)).encoded

    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
}
