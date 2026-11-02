package dev.saged.qayd.system

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** حسابات "تذكّرني": كلمة السر مشفّرة بمفتاح في Android Keystore لا يخرج من الجهاز. */
object SavedLogins {
    private const val PREFS = "qayd_logins"
    private const val ALIAS = "qayd_logins_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build(),
            )
        }.generateKey()
    }

    private fun read(c: Context) = runCatching { JSONArray(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("list", "[]")) }.getOrDefault(JSONArray())
    private fun write(c: Context, a: JSONArray) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("list", a.toString()).apply()

    fun users(c: Context): List<String> = read(c).let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("u") } }

    fun save(c: Context, user: String, pass: String) = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val enc = cipher.doFinal(pass.toByteArray())
        val item = JSONObject().put("u", user).put("p", Base64.encodeToString(enc, Base64.NO_WRAP)).put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
        val old = read(c)
        val out = JSONArray().put(item)
        for (i in 0 until old.length()) if (old.getJSONObject(i).getString("u") != user) out.put(old.getJSONObject(i))
        write(c, out)
    }

    fun password(c: Context, user: String): String? = runCatching {
        val a = read(c)
        val o = (0 until a.length()).map { a.getJSONObject(it) }.first { it.getString("u") == user }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(o.getString("iv"), Base64.NO_WRAP)))
        }
        String(cipher.doFinal(Base64.decode(o.getString("p"), Base64.NO_WRAP)))
    }.getOrNull()

    fun remove(c: Context, user: String) {
        val old = read(c)
        val out = JSONArray()
        for (i in 0 until old.length()) if (old.getJSONObject(i).getString("u") != user) out.put(old.getJSONObject(i))
        write(c, out)
    }
}
