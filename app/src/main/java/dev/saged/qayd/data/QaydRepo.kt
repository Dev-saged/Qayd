package dev.saged.qayd.data

import android.content.Context
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SnapshotMetadata
import dev.saged.qayd.config.BrandConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** الدخول، claims المخزنة للعمل بدون إنترنت، مستمعو Firestore، واستدعاء Worker الإدارة. */
class QaydRepo(context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val prefs = context.getSharedPreferences("qayd_session", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _account = MutableStateFlow<Account?>(null)
    private val _ready = MutableStateFlow(false)
    val account: StateFlow<Account?> = _account
    val ready: StateFlow<Boolean> = _ready

    init {
        val u = auth.currentUser
        _account.value = u?.let(::loadCached)
        _ready.value = true
        if (u != null) scope.launch { refreshClaims(u) }
    }

    /* ---------- الدخول ---------- */

    suspend fun signIn(login: String, password: String) {
        val email = login.trim().lowercase()
        try {
            val u = auth.signInWithEmailAndPassword(email, password).await().user ?: throw QaydError("تعذر الدخول")
            val claims = u.getIdToken(true).await().claims
            val acc = accountOf(u, claims) ?: run {
                auth.signOut()
                throw QaydError("هذا الحساب غير مرتبط بأي محل")
            }
            save(acc)
            _account.value = acc
        } catch (e: QaydError) {
            throw e
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw QaydError("اسم المستخدم أو كلمة السر غير صحيحة")
        } catch (e: FirebaseAuthInvalidUserException) {
            throw QaydError("اسم المستخدم أو كلمة السر غير صحيحة")
        } catch (e: FirebaseNetworkException) {
            throw QaydError("أول دخول يحتاج اتصالاً بالإنترنت")
        } catch (e: FirebaseTooManyRequestsException) {
            throw QaydError("محاولات كثيرة، انتظر قليلاً ثم حاول")
        }
    }

    fun signOut() {
        auth.signOut()
        prefs.edit().clear().apply()
        _account.value = null
    }

    private suspend fun refreshClaims(u: FirebaseUser) {
        runCatching { u.getIdToken(true).await() }
            .onSuccess { r ->
                val acc = accountOf(u, r.claims)
                if (acc == null) signOut() else { save(acc); _account.value = acc }
            }
            .onFailure { if (it is FirebaseAuthInvalidUserException) signOut() }
    }

    private fun accountOf(u: FirebaseUser, claims: Map<String, Any>): Account? {
        val role = when {
            claims["super_admin"] == true -> Role.SuperAdmin
            claims["role"] == "owner" -> Role.Owner
            claims["role"] == "staff" -> Role.Staff
            else -> return null
        }
        val shopId = claims["shopId"] as? String
        if (role != Role.SuperAdmin && shopId.isNullOrBlank()) return null
        return Account(u.uid, u.email.orEmpty(), u.displayName ?: u.email.orEmpty().substringBefore('@'), role, shopId)
    }

    private fun save(a: Account) = prefs.edit()
        .putString("uid", a.uid).putString("role", a.role.name)
        .putString("shopId", a.shopId).putString("name", a.displayName).apply()

    private fun loadCached(u: FirebaseUser): Account? {
        if (prefs.getString("uid", null) != u.uid) return null
        val role = prefs.getString("role", null)?.let { r -> Role.entries.firstOrNull { it.name == r } } ?: return null
        return Account(u.uid, u.email.orEmpty(), prefs.getString("name", null) ?: u.email.orEmpty(), role, prefs.getString("shopId", null))
    }

    /* ---------- المحلات ---------- */

    fun shop(id: String): Flow<Pair<Shop?, SyncState>> = callbackFlow {
        val reg = db.document("shops/$id").addSnapshotListener(MetadataChanges.INCLUDE) { s, e ->
            if (e != null || s == null) {
                trySend(null to SyncState.Offline)
                return@addSnapshotListener
            }
            trySend((if (s.exists()) s.toShop() else null) to s.metadata.sync())
        }
        awaitClose { reg.remove() }
    }

    fun shops(): Flow<Pair<List<Shop>, SyncState>> = callbackFlow {
        val reg = db.collection("shops").orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { s, e ->
                if (e != null || s == null) {
                    trySend(emptyList<Shop>() to SyncState.Offline)
                    return@addSnapshotListener
                }
                trySend(s.documents.map { it.toShop() } to s.metadata.sync())
            }
        awaitClose { reg.remove() }
    }

    fun renameShop(id: String, name: String) {
        db.document("shops/$id").update(mapOf("name" to name.trim(), "updatedAt" to FieldValue.serverTimestamp()))
    }

    fun setShopActive(id: String, active: Boolean) {
        db.document("shops/$id").update(mapOf("active" to active, "updatedAt" to FieldValue.serverTimestamp()))
    }

    private fun SnapshotMetadata.sync() = when {
        hasPendingWrites() -> SyncState.Pending
        isFromCache -> SyncState.Offline
        else -> SyncState.Synced
    }

    private fun DocumentSnapshot.toShop(): Shop {
        val mods = (get("modules") as? Map<*, *>).orEmpty()
        return Shop(
            id = id,
            name = getString("name").orEmpty(),
            suffix = getString("suffix").orEmpty(),
            active = getBoolean("active") ?: false,
            licenseUntil = (get("licenseUntil") as? Timestamp)?.toDate()?.time,
            modules = Module.entries.filter { mods[it.key] == true }.toSet(),
            psScreens = (getLong("psScreens") ?: 3L).toInt(),
        )
    }

    /* ---------- Worker الإدارة ---------- */

    suspend fun createShop(s: NewShop): String {
        val body = JSONObject()
            .put("name", s.name.trim()).put("suffix", s.suffix.trim().lowercase())
            .put("ownerName", s.ownerName.trim()).put("ownerUsername", s.ownerUsername.trim().lowercase())
            .put("ownerPassword", s.ownerPassword).put("psScreens", s.psScreens)
            .put("modules", JSONObject().apply { Module.entries.forEach { put(it.key, it in s.modules) } })
        return admin("/v1/shops", body).getString("ownerEmail")
    }

    suspend fun createStaff(username: String, displayName: String, password: String): String =
        admin(
            "/v1/staff",
            JSONObject().put("username", username.trim().lowercase()).put("displayName", displayName.trim()).put("password", password),
        ).getString("email")

    private suspend fun admin(path: String, body: JSONObject): JSONObject {
        val token = try {
            auth.currentUser?.getIdToken(false)?.await()?.token
        } catch (e: FirebaseNetworkException) {
            throw QaydError("هذه العملية تحتاج اتصالاً بالإنترنت")
        } ?: throw QaydError("سجّل الدخول من جديد")
        return withContext(Dispatchers.IO) {
            val c = URL(BrandConfig.ADMIN_API + path).openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 15_000
                c.readTimeout = 25_000
                c.setRequestProperty("Authorization", "Bearer $token")
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = c.responseCode
                val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
                if (code !in 200..299) throw QaydError(json.optString("message", "تعذر إتمام العملية"))
                json
            } catch (e: IOException) {
                throw QaydError("هذه العملية تحتاج اتصالاً بالإنترنت")
            } finally {
                c.disconnect()
            }
        }
    }
}
