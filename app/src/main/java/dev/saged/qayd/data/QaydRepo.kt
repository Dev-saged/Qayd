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
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.SnapshotMetadata
import dev.saged.qayd.ui.home.PsSlot
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

    fun features(): Flow<Map<String, String>> = callbackFlow {
        val reg = db.document("config/features").addSnapshotListener { s, _ ->
            trySend(s?.data.orEmpty().mapValues { it.value.toString() })
        }
        awaitClose { reg.remove() }
    }

    fun setFeature(key: String, paid: Boolean) {
        db.document("config/features").set(mapOf(key to if (paid) "paid" else "free"), SetOptions.merge())
    }

    fun updateShop(id: String, modules: Set<Module>, psScreens: Int, paid: Set<String>, licenseUntil: Long?, name: String, smsQuota: Int, type: String) {
        db.document("shops/$id").update(
            mapOf(
                "name" to name.trim(),
                "modules" to Module.entries.associate { it.key to (it in modules) },
                "psScreens" to psScreens,
                "paid" to FEATURES.associate { it.first to (it.first in paid) },
                "licenseUntil" to licenseUntil?.let { Timestamp(java.util.Date(it)) },
                "smsQuota" to smsQuota,
                "type" to type,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
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
            paid = (get("paid") as? Map<*, *>).orEmpty().filterValues { it == true }.keys.map { it.toString() }.toSet(),
            smsQuota = (getLong("smsQuota") ?: -1L).toInt(),
            type = getString("type") ?: BizType.General.key,
        )
    }

    /* ---------- الدفتر: زبائن، حركات، مصروفات ثابتة ---------- */

    private fun <T> listen(path: String, map: (DocumentSnapshot) -> T?): Flow<List<T>> = callbackFlow {
        val reg = db.collection(path).addSnapshotListener(MetadataChanges.INCLUDE) { s, e ->
            if (e != null || s == null) return@addSnapshotListener
            trySend(s.documents.filter { it.getBoolean("deleted") != true }.mapNotNull(map))
        }
        awaitClose { reg.remove() }
    }

    fun customers(shopId: String): Flow<List<Customer>> = listen("shops/$shopId/customers") {
        Customer(
            it.id, it.getString("name").orEmpty(), it.getString("phone").orEmpty(), it.getString("note").orEmpty(), it.millis("createdAt"),
            it.getBoolean("power") == true, it.getString("meter").orEmpty(), it.getDouble("initialReading") ?: 0.0,
        )
    }

    fun txs(shopId: String): Flow<List<Tx>> = listen("shops/$shopId/tx") {
        val kind = TxKind.of(it.getString("kind")) ?: return@listen null
        Tx(
            id = it.id, kind = kind, amount = it.getDouble("amount") ?: 0.0, at = it.millis("at"),
            customerId = it.getString("customerId"),
            category = it.getString("category")?.let(ExpenseCategory::of),
            recurringId = it.getString("recurringId"), period = it.getString("period"),
            byName = it.getString("byName").orEmpty(), pending = it.metadata.hasPendingWrites(),
            by = it.getString("by").orEmpty(), source = it.getString("source"), ref = it.getString("ref"),
        )
    }

    fun members(shopId: String): Flow<List<Member>> = listen("shops/$shopId/members") { d ->
        val perms = (d.get("perms") as? Map<*, *>).orEmpty()
        Member(
            d.id, d.getString("username").orEmpty(), d.getString("displayName").orEmpty(),
            if (d.getString("role") == "owner") Role.Owner else Role.Staff,
            Perm.entries.filter { perms[it.key] == true }.toSet(), d.getBoolean("active") ?: true,
        )
    }

    fun audit(shopId: String): Flow<List<AuditEntry>> = listen("shops/$shopId/audit") {
        AuditEntry(it.id, it.getString("action").orEmpty(), it.getString("detail").orEmpty(), it.getDouble("amount"), it.getString("byName").orEmpty(), it.millis("at"))
    }

    fun updateMember(shopId: String, m: Member, perms: Set<Perm>, active: Boolean) {
        db.document("shops/$shopId/members/${m.uid}").update(
            mapOf("perms" to Perm.entries.associate { it.key to (it in perms) }, "active" to active, "updatedAt" to FieldValue.serverTimestamp()),
        )
        log(shopId, if (active != m.active) (if (active) "تفعيل موظف" else "إيقاف موظف") else "تعديل صلاحيات", m.displayName)
    }

    fun log(shopId: String, action: String, detail: String, amount: Double? = null) {
        val a = _account.value
        db.collection("shops/$shopId/audit").document().set(
            mapOf("action" to action, "detail" to detail, "amount" to amount, "by" to a?.uid, "byName" to a?.displayName, "at" to FieldValue.serverTimestamp()),
        )
    }

    fun recurring(shopId: String): Flow<List<Recurring>> = listen("shops/$shopId/recurring") {
        Recurring(it.id, it.getString("title").orEmpty(), ExpenseCategory.of(it.getString("category")), (it.getLong("day") ?: 1L).toInt())
    }

    private fun DocumentSnapshot.millis(field: String) =
        (get(field) as? Timestamp)?.toDate()?.time ?: (getLong(field) ?: System.currentTimeMillis())

    private fun stamp(): Map<String, Any?> {
        val a = _account.value
        return mapOf(
            "by" to a?.uid, "byName" to a?.displayName,
            "deleted" to false, "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp(),
        )
    }

    /** الكتابة تُحفظ محلياً فوراً وتُرسل عند الاتصال؛ لا ننتظر الخادم. */
    fun addTx(
        shopId: String, kind: TxKind, amount: Double, customerId: String? = null,
        category: ExpenseCategory? = null, recurringId: String? = null,
        source: String? = null, ref: String? = null,
    ) {
        val now = System.currentTimeMillis()
        db.collection("shops/$shopId/tx").document().set(
            stamp() + mapOf(
                "kind" to kind.key, "amount" to round2(amount), "at" to Timestamp(java.util.Date(now)),
                "customerId" to customerId, "category" to category?.key,
                "recurringId" to recurringId, "period" to if (recurringId != null) Ledger.period(now) else null,
                "source" to source, "ref" to ref,
            ),
        )
    }

    fun deleteTx(shopId: String, tx: Tx, who: String?) {
        val a = _account.value
        db.document("shops/$shopId/tx/${tx.id}").update(
            mapOf("deleted" to true, "deletedBy" to a?.uid, "deletedByName" to a?.displayName, "updatedAt" to FieldValue.serverTimestamp()),
        )
        log(shopId, "حذف ${tx.kind.label}", who ?: tx.byName, tx.amount)
    }

    fun addCustomer(shopId: String, name: String, phone: String): String {
        val ref = db.collection("shops/$shopId/customers").document()
        ref.set(stamp() + mapOf("name" to name.trim(), "phone" to phone.trim(), "note" to ""))
        return ref.id
    }

    fun addRecurring(shopId: String, title: String, category: ExpenseCategory, day: Int) {
        db.collection("shops/$shopId/recurring").document()
            .set(stamp() + mapOf("title" to title.trim(), "category" to category.key, "day" to day))
    }

    fun deleteRecurring(shopId: String, id: String) {
        db.document("shops/$shopId/recurring/$id").update(mapOf("deleted" to true, "updatedAt" to FieldValue.serverTimestamp()))
    }

    /* ---------- الكهرباء ---------- */

    fun readings(shopId: String): Flow<List<Reading>> = listen("shops/$shopId/readings") {
        Reading(
            it.id, it.getString("customerId").orEmpty(), it.getDouble("prev") ?: 0.0, it.getDouble("curr") ?: 0.0,
            it.getDouble("kwh") ?: 0.0, it.getDouble("price") ?: 0.0, it.getDouble("amount") ?: 0.0,
            it.getString("period").orEmpty(), it.millis("at"), it.getString("byName").orEmpty(),
        )
    }

    fun addSubscriber(shopId: String, name: String, phone: String, meter: String, initial: Double): String {
        val ref = db.collection("shops/$shopId/customers").document()
        ref.set(stamp() + mapOf("name" to name.trim(), "phone" to phone.trim(), "note" to "", "power" to true, "meter" to meter.trim(), "initialReading" to initial))
        return ref.id
    }

    /** قراءة جديدة: تُحفظ القراءة وتُسجل الفاتورة ديناً على المشترك بنفس المرجع. */
    fun addReading(shopId: String, customerId: String, prev: Double, curr: Double, price: Double) {
        val (kwh, amount) = PowerState.bill(prev, curr, price)
        val now = System.currentTimeMillis()
        val ref = db.collection("shops/$shopId/readings").document()
        ref.set(
            stamp() + mapOf(
                "customerId" to customerId, "prev" to prev, "curr" to curr, "kwh" to kwh, "price" to price,
                "amount" to amount, "period" to Ledger.period(now), "at" to ts(now),
            ),
        )
        if (amount > 0) addTx(shopId, TxKind.Debt, amount, customerId = customerId, source = "power", ref = ref.id)
    }

    /* ---------- الرسائل ---------- */

    fun smsQueue(shopId: String): Flow<List<SmsMsg>> = listen("shops/$shopId/smsQueue") {
        SmsMsg(
            it.id, it.getString("to").orEmpty(), it.getString("text").orEmpty(), it.getString("name").orEmpty(),
            it.getString("template").orEmpty(), SmsStatus.of(it.getString("status")), it.getString("error").orEmpty(),
            it.millis("at"), it.getString("byName").orEmpty(),
        )
    }

    fun gateway(shopId: String): Flow<Gateway> = callbackFlow {
        val reg = db.document("shops/$shopId/gateway/main").addSnapshotListener { s, _ ->
            trySend(
                if (s == null || !s.exists()) Gateway.None
                else Gateway(s.getBoolean("online") == true, s.getString("device").orEmpty(), s.millis("lastSeen"), s.getString("uid").orEmpty()),
            )
        }
        awaitClose { reg.remove() }
    }

    fun templates(shopId: String): Flow<List<Template>> = listen("shops/$shopId/templates") {
        Template(it.id, it.getString("title").orEmpty(), it.getString("body").orEmpty(),
            TemplateKind.entries.firstOrNull { k -> k.name == it.getString("kind") } ?: TemplateKind.General)
    }

    fun saveTemplate(shopId: String, id: String?, title: String, body: String, kind: TemplateKind) {
        val ref = if (id == null) db.collection("shops/$shopId/templates").document() else db.document("shops/$shopId/templates/$id")
        ref.set(stamp() + mapOf("title" to title.trim(), "body" to body.trim(), "kind" to kind.name))
    }

    fun deleteTemplate(shopId: String, id: String) {
        db.document("shops/$shopId/templates/$id").update(mapOf("deleted" to true, "updatedAt" to FieldValue.serverTimestamp()))
    }

    /** تُضاف الرسائل للطابور؛ جوال الإرسال يلتقطها ويرسلها ويحدّث حالتها. */
    fun enqueueSms(shopId: String, items: List<Triple<String, String, String>>, template: String) {
        val now = ts(System.currentTimeMillis())
        items.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (to, name, text) ->
                batch.set(
                    db.collection("shops/$shopId/smsQueue").document(),
                    stamp() + mapOf("to" to to, "name" to name, "text" to text, "template" to template, "status" to "queued", "attempts" to 0, "at" to now),
                )
            }
            batch.set(db.document("shops/$shopId/usage/${smsMonth()}"), mapOf("count" to FieldValue.increment(chunk.size.toLong())), SetOptions.merge())
            batch.commit()
        }
    }

    /** عدّاد رسائل الشهر بتوقيت UTC، بنفس معرّف القواعد. */
    private fun smsMonth(): String {
        val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        return "sms-${c.get(java.util.Calendar.YEAR)}-${c.get(java.util.Calendar.MONTH) + 1}"
    }

    fun smsUsed(shopId: String): Flow<Int> = callbackFlow {
        val reg = db.document("shops/$shopId/usage/${smsMonth()}").addSnapshotListener { s, _ -> trySend((s?.getLong("count") ?: 0L).toInt()) }
        awaitClose { reg.remove() }
    }

    fun retrySms(shopId: String, id: String) {
        db.document("shops/$shopId/smsQueue/$id").update(mapOf("status" to "queued", "error" to "", "updatedAt" to FieldValue.serverTimestamp()))
    }

    /* ---------- الأسعار وشاشات PlayStation ---------- */

    fun prices(shopId: String): Flow<Prices> = callbackFlow {
        val reg = db.document("shops/$shopId/settings/prices").addSnapshotListener { s, _ ->
            trySend(Prices(s?.getDouble("psRate") ?: 0.0, s?.getDouble("kwhPrice") ?: 0.0))
        }
        awaitClose { reg.remove() }
    }

    fun setPrice(shopId: String, key: String, value: Double) {
        db.document("shops/$shopId/settings/prices").set(mapOf(key to round2(value), "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
        log(shopId, "تغيير سعر", if (key == "psRate") "ساعة الشاشة" else "كيلو الكهرباء", value)
    }

    fun ps(shopId: String): Flow<List<PsSlot>> = callbackFlow {
        val reg = db.collection("shops/$shopId/ps").addSnapshotListener(MetadataChanges.INCLUDE) { s, _ ->
            trySend(s?.documents.orEmpty().mapNotNull { d ->
                val n = d.id.toIntOrNull() ?: return@mapNotNull null
                val start = (d.get("startedAt") as? Timestamp)?.toDate()?.time
                PsSlot(
                    n, start, (d.getLong("bookedMinutes") ?: 60L).toInt(),
                    (d.get("pausedAt") as? Timestamp)?.toDate()?.time, d.getLong("pausedMs") ?: 0L,
                    d.getDouble("rate") ?: 0.0, d.getString("byName").orEmpty(),
                )
            })
        }
        awaitClose { reg.remove() }
    }

    private fun psDoc(shopId: String, n: Int) = db.document("shops/$shopId/ps/$n")
    private fun ts(t: Long) = Timestamp(java.util.Date(t))

    fun startPs(shopId: String, n: Int, minutes: Int, rate: Double) {
        val a = _account.value
        psDoc(shopId, n).set(
            mapOf(
                "startedAt" to ts(System.currentTimeMillis()), "bookedMinutes" to minutes, "pausedAt" to null, "pausedMs" to 0L,
                "rate" to rate, "by" to a?.uid, "byName" to a?.displayName, "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
    }

    fun pausePs(shopId: String, s: PsSlot) {
        val now = System.currentTimeMillis()
        val change = if (s.pausedAt == null) mapOf("pausedAt" to ts(now))
        else mapOf("pausedAt" to null, "pausedMs" to s.pausedMs + (now - s.pausedAt))
        psDoc(shopId, s.number).update(change + ("updatedAt" to FieldValue.serverTimestamp()))
    }

    fun extendPs(shopId: String, s: PsSlot, minutes: Int) {
        psDoc(shopId, s.number).update(mapOf("bookedMinutes" to (if (s.open) 0 else s.bookedMinutes + minutes), "updatedAt" to FieldValue.serverTimestamp()))
    }

    /** إنهاء الجلسة: تُسجل بيعاً للمحل أو ديناً على زبون، وتعود الشاشة متاحة. */
    fun endPs(shopId: String, s: PsSlot, amount: Double, customerId: String?) {
        if (amount > 0) {
            addTx(shopId, if (customerId == null) TxKind.Sale else TxKind.Debt, amount, customerId = customerId, source = "ps", ref = "شاشة ${s.number}")
        }
        psDoc(shopId, s.number).set(mapOf("startedAt" to null, "updatedAt" to FieldValue.serverTimestamp()))
    }

    /* ---------- Worker الإدارة ---------- */

    suspend fun createShop(s: NewShop): String {
        val body = JSONObject()
            .put("name", s.name.trim()).put("suffix", s.suffix.trim().lowercase())
            .put("ownerName", s.ownerName.trim()).put("ownerUsername", s.ownerUsername.trim().lowercase())
            .put("ownerPassword", s.ownerPassword).put("psScreens", s.psScreens).put("type", s.type)
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

    /* ---------- الأصناف والمخزون ---------- */

    fun products(shopId: String): Flow<List<Product>> = listen("shops/$shopId/products") {
        Product(
            it.id, it.getString("name").orEmpty(), it.getString("barcode").orEmpty(), it.getString("unit") ?: "قطعة",
            it.getDouble("cost") ?: 0.0, it.getDouble("price") ?: 0.0, it.getDouble("minStock") ?: 0.0,
        )
    }

    fun moves(shopId: String): Flow<List<Move>> = listen("shops/$shopId/moves") {
        val kind = MoveKind.of(it.getString("kind")) ?: return@listen null
        Move(it.id, it.getString("productId").orEmpty(), kind, it.getDouble("qty") ?: 0.0, it.getDouble("cost") ?: 0.0, it.millis("at"), it.getString("byName").orEmpty())
    }

    private fun move(shopId: String, productId: String, kind: MoveKind, qty: Double, cost: Double, extra: Map<String, Any?> = emptyMap()) =
        db.collection("shops/$shopId/moves").document() to stamp() + mapOf(
            "productId" to productId, "kind" to kind.key, "qty" to round2(qty), "cost" to round2(cost),
            "at" to Timestamp(java.util.Date()),
        ) + extra

    fun addProduct(shopId: String, n: NewProduct) {
        val batch = db.batch()
        val ref = db.collection("shops/$shopId/products").document()
        batch.set(ref, stamp() + mapOf("name" to n.name.trim(), "barcode" to n.barcode.trim(), "unit" to n.unit, "cost" to round2(n.cost), "price" to round2(n.price), "minStock" to n.minStock))
        if (n.qty > 0) move(shopId, ref.id, MoveKind.In, n.qty, n.cost).let { (d, m) -> batch.set(d, m) }
        batch.commit()
    }

    fun editProduct(shopId: String, p: Product) {
        db.document("shops/$shopId/products/${p.id}").update(
            mapOf("name" to p.name.trim(), "barcode" to p.barcode.trim(), "unit" to p.unit, "cost" to round2(p.cost), "price" to round2(p.price), "minStock" to p.minStock, "updatedAt" to FieldValue.serverTimestamp()),
        )
    }

    fun deleteProduct(shopId: String, p: Product) {
        db.document("shops/$shopId/products/${p.id}").update(mapOf("deleted" to true, "updatedAt" to FieldValue.serverTimestamp()))
        log(shopId, "حذف صنف", p.name, null)
    }

    /** بيع صنف: حركة بيع في الدفتر وخروج من المخزون بعملية واحدة. */
    fun sellProduct(shopId: String, p: Product, qty: Double) {
        val batch = db.batch()
        val tx = db.collection("shops/$shopId/tx").document()
        batch.set(tx, stamp() + mapOf(
            "kind" to TxKind.Sale.key, "amount" to round2(qty * p.price), "at" to Timestamp(java.util.Date()),
            "source" to "stock", "ref" to "${p.name} × ${dev.saged.qayd.ui.util.money(qty)}",
        ))
        move(shopId, p.id, MoveKind.Sale, -qty, p.cost, mapOf("txId" to tx.id)).let { (d, m) -> batch.set(d, m) }
        batch.commit()
    }

    /** إدخال بضاعة، ومعها فاتورة شراء على المورد إن اختير. */
    fun receiveStock(shopId: String, p: Product, qty: Double, cost: Double, supplierId: String?) {
        val batch = db.batch()
        move(shopId, p.id, MoveKind.In, qty, cost, mapOf("supplierId" to supplierId)).let { (d, m) -> batch.set(d, m) }
        if (cost != p.cost && cost > 0) batch.update(db.document("shops/$shopId/products/${p.id}"), mapOf("cost" to round2(cost), "updatedAt" to FieldValue.serverTimestamp()))
        if (supplierId != null && qty * cost > 0) {
            batch.set(db.collection("shops/$shopId/stx").document(), stamp() + mapOf(
                "supplierId" to supplierId, "kind" to STxKind.Purchase.key, "amount" to round2(qty * cost),
                "note" to "${p.name} × ${dev.saged.qayd.ui.util.money(qty)}", "at" to Timestamp(java.util.Date()),
            ))
        }
        batch.commit()
    }

    fun adjustStock(shopId: String, p: Product, current: Double, actual: Double) {
        if (actual == current) return
        move(shopId, p.id, MoveKind.Adjust, actual - current, p.cost).let { (d, m) -> d.set(m) }
        log(shopId, "جرد صنف", "${p.name}: ${dev.saged.qayd.ui.util.money(current)} ← ${dev.saged.qayd.ui.util.money(actual)}", null)
    }

    /* ---------- الموردون ---------- */

    fun suppliers(shopId: String): Flow<List<Supplier>> = listen("shops/$shopId/suppliers") {
        Supplier(it.id, it.getString("name").orEmpty(), it.getString("phone").orEmpty())
    }

    fun stx(shopId: String): Flow<List<STx>> = listen("shops/$shopId/stx") {
        val kind = STxKind.of(it.getString("kind")) ?: return@listen null
        STx(it.id, it.getString("supplierId").orEmpty(), kind, it.getDouble("amount") ?: 0.0, it.millis("at"), it.getString("note").orEmpty(), it.getString("byName").orEmpty())
    }

    fun addSupplier(shopId: String, name: String, phone: String): String {
        val ref = db.collection("shops/$shopId/suppliers").document()
        ref.set(stamp() + mapOf("name" to name.trim(), "phone" to phone.trim()))
        return ref.id
    }

    fun addSupplierTx(shopId: String, supplierId: String, kind: STxKind, amount: Double, note: String) {
        db.collection("shops/$shopId/stx").document().set(stamp() + mapOf(
            "supplierId" to supplierId, "kind" to kind.key, "amount" to round2(amount), "note" to note.trim(), "at" to Timestamp(java.util.Date()),
        ))
    }

    /* ---------- قائمة الطعام والطلبات ---------- */

    fun menu(shopId: String): Flow<List<MenuItem>> = listen("shops/$shopId/menu") {
        MenuItem(it.id, it.getString("name").orEmpty(), it.getDouble("price") ?: 0.0, it.getString("category").orEmpty(), it.getBoolean("active") != false)
    }

    fun saveMenuItem(shopId: String, id: String?, name: String, price: Double, category: String, active: Boolean) {
        val data = mapOf("name" to name.trim(), "price" to round2(price), "category" to category.trim(), "active" to active)
        if (id == null) db.collection("shops/$shopId/menu").document().set(stamp() + data)
        else db.document("shops/$shopId/menu/$id").update(data + ("updatedAt" to FieldValue.serverTimestamp()))
    }

    fun deleteMenuItem(shopId: String, id: String) {
        db.document("shops/$shopId/menu/$id").update(mapOf("deleted" to true, "updatedAt" to FieldValue.serverTimestamp()))
    }

    /** الطلبات من بداية الأمس فقط، فالقائمة خفيفة دائماً. */
    fun orders(shopId: String): Flow<List<Order>> = callbackFlow {
        val since = Timestamp(java.util.Date(Ledger.dayStart(System.currentTimeMillis()) - 86_400_000L))
        val reg = db.collection("shops/$shopId/orders").whereGreaterThanOrEqualTo("at", since)
            .addSnapshotListener(MetadataChanges.INCLUDE) { s, e ->
                if (e != null || s == null) return@addSnapshotListener
                trySend(s.documents.map { d ->
                    Order(
                        d.id, (d.getLong("no") ?: 0L).toInt(),
                        (d.get("lines") as? List<*>).orEmpty().mapNotNull { l ->
                            (l as? Map<*, *>)?.let { OrderLine(it["name"]?.toString().orEmpty(), (it["price"] as? Number)?.toDouble() ?: 0.0, (it["qty"] as? Number)?.toInt() ?: 1) }
                        },
                        OrderType.of(d.getString("type")), OrderStatus.of(d.getString("status")),
                        d.getString("table").orEmpty(), d.getString("phone").orEmpty(), d.getString("address").orEmpty(), d.getString("note").orEmpty(),
                        d.millis("at"), d.getString("byName").orEmpty(), d.getString("txId").orEmpty(),
                    )
                })
            }
        awaitClose { reg.remove() }
    }

    /** الطلب يُسجَّل بيعاً في الدفتر بنفس العملية. */
    fun addOrder(shopId: String, no: Int, o: NewOrder) {
        val batch = db.batch()
        val now = Timestamp(java.util.Date())
        val ref = db.collection("shops/$shopId/orders").document()
        val tx = db.collection("shops/$shopId/tx").document()
        val total = round2(o.lines.sumOf { it.price * it.qty })
        batch.set(ref, stamp() + mapOf(
            "no" to no, "lines" to o.lines.map { mapOf("name" to it.name, "price" to it.price, "qty" to it.qty) },
            "type" to o.type.key, "status" to OrderStatus.New.key, "table" to o.table.trim(), "phone" to o.phone.trim(),
            "address" to o.address.trim(), "note" to o.note.trim(), "total" to total, "txId" to tx.id, "at" to now,
        ))
        batch.set(tx, stamp() + mapOf("kind" to TxKind.Sale.key, "amount" to total, "at" to now, "source" to "order", "ref" to "طلب #$no"))
        batch.commit()
    }

    fun setOrderStatus(shopId: String, id: String, status: OrderStatus) {
        db.document("shops/$shopId/orders/$id").update(mapOf("status" to status.key, "updatedAt" to FieldValue.serverTimestamp()))
    }
}
