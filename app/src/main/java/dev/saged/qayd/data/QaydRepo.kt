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
import dev.saged.qayd.system.AccountVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** الدخول، claims المخزنة للعمل بدون إنترنت، مستمعو Firestore، واستدعاء Worker الإدارة. */
class QaydRepo(context: Context) {
    companion object {
        @Volatile private var instance: QaydRepo? = null

        /** نسخة واحدة للتطبيق، تستخدمها الواجهة والمهام الخلفية. */
        fun get(c: Context): QaydRepo = instance ?: synchronized(this) { instance ?: QaydRepo(c.applicationContext).also { instance = it } }
    }

    private val ctx = context.applicationContext
    private var appName: String = AccountVault.DEFAULT
    private val auth get() = FirebaseAuth.getInstance(AccountVault.app(ctx, appName))
    private val db get() = FirebaseFirestore.getInstance(AccountVault.app(ctx, appName))
    private val prefs get() = ctx.getSharedPreferences(AccountVault.sessionPrefs(appName), Context.MODE_PRIVATE)

    /** Firestore الحساب النشط، تستخدمه الخدمات الخلفية. */
    val firestore: FirebaseFirestore get() = db
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _account = MutableStateFlow<Account?>(null)
    private val _ready = MutableStateFlow(false)
    val account: StateFlow<Account?> = _account
    val ready: StateFlow<Boolean> = _ready

    init {
        AccountVault.tune(FirebaseFirestore.getInstance())
        AccountVault.migrate(ctx)
        val u = AccountVault.active(ctx)?.let { appName = it; auth.currentUser }
        _account.value = u?.let(::loadCached)
        _ready.value = true
        if (u != null) scope.launch { refreshClaims(u) }
    }

    /* ---------- الدخول ---------- */

    suspend fun signIn(login: String, password: String) {
        val email = login.trim().lowercase()
        val name = AccountVault.appName(ctx, email)
        val a = FirebaseAuth.getInstance(AccountVault.app(ctx, name))
        val kept = a.currentUser?.takeIf { it.email.equals(email, true) }
        // حساب محفوظ على الجهاز: دخول فوري بدون إنترنت
        if (kept != null && AccountVault.check(ctx, email, password)) {
            appName = name
            loadCached(kept)?.let { acc ->
                AccountVault.setActive(ctx, name)
                _account.value = acc
                scope.launch { refreshClaims(kept) }
                return
            }
        }
        try {
            val u = a.signInWithEmailAndPassword(email, password).await().user ?: throw QaydError("تعذر الدخول")
            val claims = u.getIdToken(true).await().claims
            appName = name
            val acc = accountOf(u, claims) ?: run {
                a.signOut()
                throw QaydError("هذا الحساب غير مرتبط بأي محل")
            }
            AccountVault.remember(ctx, email, name, password)
            AccountVault.setActive(ctx, name)
            save(acc)
            _account.value = acc
            scope.launch { warm(acc) }
        } catch (e: QaydError) {
            throw e
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw QaydError("اسم المستخدم أو كلمة السر غير صحيحة")
        } catch (e: FirebaseAuthInvalidUserException) {
            throw QaydError("اسم المستخدم أو كلمة السر غير صحيحة")
        } catch (e: FirebaseNetworkException) {
            throw QaydError(if (kept != null) "كلمة السر غير صحيحة" else "أول دخول لهذا الحساب يحتاج اتصالاً بالإنترنت")
        } catch (e: FirebaseTooManyRequestsException) {
            throw QaydError("محاولات كثيرة، انتظر قليلاً ثم حاول")
        }
    }

    /** خروج إلى شاشة الدخول مع إبقاء الحساب وبيانات محله محفوظة على الجهاز. */
    fun signOut() {
        AccountVault.setActive(ctx, null)
        _account.value = null
    }

    /** حذف الحساب من الجهاز كلياً. */
    fun forget(login: String) {
        val email = login.trim().lowercase()
        if (AccountVault.appName(ctx, email) == appName && _account.value != null) _account.value = null
        AccountVault.forget(ctx, email)
    }

    /** تنزيل بيانات كل محلات الحساب للكاش حتى تعمل كاملة بدون إنترنت. */
    private suspend fun warm(a: Account) {
        val cols = listOf("customers", "tx", "recurring", "members", "settings", "ps", "readings", "products", "moves",
            "suppliers", "stx", "menu", "orders", "holds", "closes", "templates", "smsQueue", "audit")
        a.shopIds.forEach { id ->
            runCatching { db.document("shops/$id").get().await() }
            cols.forEach { c -> runCatching { db.collection("shops/$id/$c").get().await() } }
        }
    }

    private suspend fun refreshClaims(u: FirebaseUser) {
        runCatching { u.getIdToken(true).await() }
            .onSuccess { r ->
                val acc = accountOf(u, r.claims)
                if (acc == null) forget(u.email.orEmpty()) else { save(acc); _account.value = acc; warm(acc) }
            }
            .onFailure { if (it is FirebaseAuthInvalidUserException) forget(u.email.orEmpty()) }
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
        val all = (listOfNotNull(shopId) + (claims["shops"] as? List<*>).orEmpty().map { it.toString() }).distinct()
        val current = prefs.getString("current", null)?.takeIf { it in all } ?: shopId
        return Account(u.uid, u.email.orEmpty(), u.displayName ?: u.email.orEmpty().substringBefore('@'), role, current, shopIds = all)
    }

    private fun save(a: Account) = prefs.edit()
        .putString("uid", a.uid).putString("role", a.role.name)
        .putString("shopId", a.shopId).putString("name", a.displayName).putString("shops", a.shopIds.joinToString(",")).apply()

    /** التنقل بين محلات نفس الحساب بدون خروج. */
    fun switchShop(id: String) {
        val a = _account.value ?: return
        if (id !in a.shopIds) return
        prefs.edit().putString("current", id).apply()
        val n = a.copy(shopId = id)
        save(n)
        _account.value = n
    }

    fun shopNames(ids: List<String>): Flow<Map<String, String>> = callbackFlow {
        val names = java.util.concurrent.ConcurrentHashMap<String, String>()
        val regs = ids.map { id ->
            db.document("shops/$id").addSnapshotListener { s, _ -> s?.getString("name")?.let { names[id] = it; trySend(names.toMap()) } }
        }
        awaitClose { regs.forEach { it.remove() } }
    }

    suspend fun linkOwner(shopId: String, login: String, unlink: Boolean) {
        admin("/v1/shops/link", JSONObject().put("shopId", shopId).put("login", login.trim().lowercase()).put("unlink", unlink))
    }

    private fun loadCached(u: FirebaseUser): Account? {
        if (prefs.getString("uid", null) != u.uid) return null
        val role = prefs.getString("role", null)?.let { r -> Role.entries.firstOrNull { it.name == r } } ?: return null
        val shops = prefs.getString("shops", null)?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        val sid = prefs.getString("shopId", null)
        return Account(u.uid, u.email.orEmpty(), prefs.getString("name", null) ?: u.email.orEmpty(), role, sid, shopIds = shops.ifEmpty { listOfNotNull(sid) })
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
            plan = getString("plan").orEmpty(),
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
            it.getDouble("limit") ?: 0.0, it.getString("waPrefix").orEmpty(), it.getString("link").orEmpty(),
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
            wallet = it.getString("wallet"), payer = it.getString("payer").orEmpty(), payerPhone = it.getString("payerPhone").orEmpty(),
            verify = it.getString("verify"),
        )
    }

    fun members(shopId: String): Flow<List<Member>> = listen("shops/$shopId/members") { d ->
        val perms = (d.get("perms") as? Map<*, *>).orEmpty()
        Member(
            d.id, d.getString("username").orEmpty(), d.getString("displayName").orEmpty(),
            if (d.getString("role") == "owner") Role.Owner else Role.Staff,
            Perm.entries.filter { perms[it.key] == true }.toSet(), d.getBoolean("active") ?: true,
            d.getLong("avatar")?.toInt(),
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
        source: String? = null, ref: String? = null, wallet: String? = null,
    ): String {
        val now = System.currentTimeMillis()
        val doc = db.collection("shops/$shopId/tx").document()
        doc.set(
            stamp() + mapOf(
                "kind" to kind.key, "amount" to round2(amount), "at" to Timestamp(java.util.Date(now)),
                "customerId" to customerId, "category" to category?.key,
                "recurringId" to recurringId, "period" to if (recurringId != null) Ledger.period(now) else null,
                "source" to source, "ref" to ref,
            ) + if (wallet != null) mapOf("wallet" to wallet, "verify" to "pending") else emptyMap(),
        )
        return doc.id
    }

    /** بيانات المحوِّل اختيارية وتُضاف بعد التسجيل. */
    fun setPayer(shopId: String, txId: String, payer: String, phone: String) {
        db.document("shops/$shopId/tx/$txId").update(mapOf("payer" to payer.trim(), "payerPhone" to phone.trim(), "updatedAt" to FieldValue.serverTimestamp()))
    }

    fun verifyTx(shopId: String, txId: String, status: String) {
        val a = _account.value
        db.document("shops/$shopId/tx/$txId").update(mapOf("verify" to status, "verifiedBy" to a?.displayName, "verifiedAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
    }

    fun wallets(shopId: String): Flow<List<String>> = callbackFlow {
        val reg = db.document("shops/$shopId/settings/payments").addSnapshotListener { s, _ ->
            trySend((s?.get("wallets") as? List<*>)?.map { it.toString() } ?: listOf("بال باي", "جوال باي", "بنك فلسطين"))
        }
        awaitClose { reg.remove() }
    }

    fun saveWallets(shopId: String, list: List<String>) {
        db.document("shops/$shopId/settings/payments").set(mapOf("wallets" to list))
    }

    fun closes(shopId: String): Flow<List<CashClose>> = listen("shops/$shopId/closes") {
        CashClose(it.id, it.getString("byName").orEmpty(), it.getString("by").orEmpty(), it.millis("at"),
            it.getDouble("expected") ?: 0.0, it.getDouble("actual") ?: 0.0, it.getString("note").orEmpty())
    }

    fun addClose(shopId: String, expected: Double, actual: Double, note: String) {
        db.collection("shops/$shopId/closes").document().set(stamp() + mapOf(
            "expected" to round2(expected), "actual" to round2(actual), "note" to note.trim(), "at" to Timestamp(java.util.Date()),
        ))
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
            JSONObject().put("username", username.trim().lowercase()).put("displayName", displayName.trim()).put("password", password)
                .put("shopId", _account.value?.shopId.orEmpty()),
        ).getString("email")

    /** كلمة سر جديدة لحساب: بمعرّف العضو (uid) أو باسم الدخول الكامل. */
    suspend fun resetPassword(target: String, password: String, byUid: Boolean) {
        admin("/v1/accounts/password", JSONObject().put(if (byUid) "uid" else "login", target.trim()).put("password", password))
    }

    private suspend fun admin(path: String, body: JSONObject): JSONObject =
        runCatching { JSONObject(adminRaw(path, body)) }.getOrElse { if (it is QaydError) throw it else JSONObject() }

    private suspend fun adminRaw(path: String, body: JSONObject): String {
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
                if (code !in 200..299) throw QaydError(runCatching { JSONObject(text) }.getOrDefault(JSONObject()).optString("message", "تعذر إتمام العملية"))
                text
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
            (it.get("sizes") as? List<*>).orEmpty().map(Any?::toString), (it.get("colors") as? List<*>).orEmpty().map(Any?::toString),
        )
    }

    fun moves(shopId: String): Flow<List<Move>> = listen("shops/$shopId/moves") {
        val kind = MoveKind.of(it.getString("kind")) ?: return@listen null
        Move(it.id, it.getString("productId").orEmpty(), kind, it.getDouble("qty") ?: 0.0, it.getDouble("cost") ?: 0.0, it.millis("at"), it.getString("byName").orEmpty(), it.getString("variant").orEmpty())
    }

    private fun move(shopId: String, productId: String, kind: MoveKind, qty: Double, cost: Double, extra: Map<String, Any?> = emptyMap(), variant: String = "") =
        db.collection("shops/$shopId/moves").document() to stamp() + mapOf(
            "productId" to productId, "kind" to kind.key, "qty" to round2(qty), "cost" to round2(cost), "variant" to variant,
            "at" to Timestamp(java.util.Date()),
        ) + extra

    fun addProduct(shopId: String, n: NewProduct) {
        val batch = db.batch()
        val ref = db.collection("shops/$shopId/products").document()
        batch.set(ref, stamp() + mapOf("name" to n.name.trim(), "barcode" to n.barcode.trim(), "unit" to n.unit, "cost" to round2(n.cost), "price" to round2(n.price), "minStock" to n.minStock, "sizes" to n.sizes, "colors" to n.colors))
        if (n.qty > 0) move(shopId, ref.id, MoveKind.In, n.qty, n.cost).let { (d, m) -> batch.set(d, m) }
        batch.commit()
    }

    fun editProduct(shopId: String, p: Product) {
        db.document("shops/$shopId/products/${p.id}").update(
            mapOf("name" to p.name.trim(), "barcode" to p.barcode.trim(), "unit" to p.unit, "cost" to round2(p.cost), "price" to round2(p.price), "minStock" to p.minStock,
                "sizes" to p.sizes, "colors" to p.colors, "updatedAt" to FieldValue.serverTimestamp()),
        )
    }

    fun deleteProduct(shopId: String, p: Product) {
        db.document("shops/$shopId/products/${p.id}").update(mapOf("deleted" to true, "updatedAt" to FieldValue.serverTimestamp()))
        log(shopId, "حذف صنف", p.name, null)
    }

    /** بيع صنف: حركة بيع في الدفتر وخروج من المخزون بعملية واحدة. */
    fun sellProduct(shopId: String, p: Product, qty: Double, variant: String = "") {
        val batch = db.batch()
        val tx = db.collection("shops/$shopId/tx").document()
        batch.set(tx, stamp() + mapOf(
            "kind" to TxKind.Sale.key, "amount" to round2(qty * p.price), "at" to Timestamp(java.util.Date()),
            "source" to "stock", "ref" to "${p.name}${if (variant.isNotBlank()) " ($variant)" else ""} × ${dev.saged.qayd.ui.util.money(qty)}",
        ))
        move(shopId, p.id, MoveKind.Sale, -qty, p.cost, mapOf("txId" to tx.id), variant).let { (d, m) -> batch.set(d, m) }
        batch.commit()
    }

    /** إدخال بضاعة، ومعها فاتورة شراء على المورد إن اختير. */
    fun receiveStock(shopId: String, p: Product, qty: Double, cost: Double, supplierId: String?, variant: String = "") {
        val batch = db.batch()
        move(shopId, p.id, MoveKind.In, qty, cost, mapOf("supplierId" to supplierId), variant).let { (d, m) -> batch.set(d, m) }
        if (cost != p.cost && cost > 0) batch.update(db.document("shops/$shopId/products/${p.id}"), mapOf("cost" to round2(cost), "updatedAt" to FieldValue.serverTimestamp()))
        if (supplierId != null && qty * cost > 0) {
            batch.set(db.collection("shops/$shopId/stx").document(), stamp() + mapOf(
                "supplierId" to supplierId, "kind" to STxKind.Purchase.key, "amount" to round2(qty * cost),
                "note" to "${p.name} × ${dev.saged.qayd.ui.util.money(qty)}", "at" to Timestamp(java.util.Date()),
            ))
        }
        batch.commit()
    }

    fun adjustStock(shopId: String, p: Product, current: Double, actual: Double, variant: String = "") {
        if (actual == current) return
        move(shopId, p.id, MoveKind.Adjust, actual - current, p.cost, variant = variant).let { (d, m) -> d.set(m) }
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

    /** مرتجع: يعود الصنف للمخزون ويُخصم ثمنه من مبيعات اليوم. */
    fun returnProduct(shopId: String, p: Product, qty: Double, variant: String) {
        val batch = db.batch()
        val tx = db.collection("shops/$shopId/tx").document()
        batch.set(tx, stamp() + mapOf(
            "kind" to TxKind.Sale.key, "amount" to -round2(qty * p.price), "at" to Timestamp(java.util.Date()),
            "source" to "return", "ref" to "مرتجع ${p.name}${if (variant.isNotBlank()) " ($variant)" else ""} × ${dev.saged.qayd.ui.util.money(qty)}",
        ))
        move(shopId, p.id, MoveKind.Return, qty, p.cost, mapOf("txId" to tx.id), variant).let { (d, m) -> batch.set(d, m) }
        batch.commit()
    }

    /* ---------- بطاريات السيارات ---------- */

    fun batteries(shopId: String): Flow<BatteryState> = listen<Any>("shops/$shopId/batteries") {
        if (it.getString("kind") == "scrap") ScrapOut(it.id, it.getDouble("kg") ?: 0.0, it.getDouble("amount") ?: 0.0, it.millis("at"))
        else BattSale(
            it.id, it.getString("serial").orEmpty(), it.getString("model").orEmpty(), it.getString("name").orEmpty(), it.getString("phone").orEmpty(),
            it.getString("car").orEmpty(), it.getDouble("price") ?: 0.0, it.getDouble("credit") ?: 0.0, it.getDouble("oldKg") ?: 0.0,
            (it.getLong("months") ?: 12L).toInt(), it.millis("at"), it.getString("productId"), it.getString("txId"),
        )
    }.map { l -> BatteryState(l.filterIsInstance<BattSale>(), l.filterIsInstance<ScrapOut>()) }

    /** بيع بطارية: سجل الكفالة وإيراد الصافي بعد قيمة القديمة، وخروج الصنف من المخزون إن اختير. */
    fun sellBattery(shopId: String, n: NewBattery) {
        val batch = db.batch()
        val ref = db.collection("shops/$shopId/batteries").document()
        val net = round2((n.price - n.credit).coerceAtLeast(0.0))
        var txId: String? = null
        if (net > 0) {
            val tx = db.collection("shops/$shopId/tx").document().also { txId = it.id }
            batch.set(tx, stamp() + mapOf(
                "kind" to TxKind.Sale.key, "amount" to net, "at" to Timestamp(java.util.Date()),
                "source" to "battery", "ref" to "بطارية ${n.model} ${n.serial}",
            ))
        }
        batch.set(ref, stamp() + mapOf(
            "kind" to "sale", "serial" to n.serial.trim(), "model" to n.model.trim(), "name" to n.name.trim(), "phone" to n.phone.trim(), "car" to n.car.trim(),
            "price" to round2(n.price), "credit" to round2(n.credit), "oldKg" to round2(n.oldKg), "months" to n.months,
            "at" to Timestamp(java.util.Date()), "productId" to n.productId, "txId" to txId,
        ))
        n.productId?.let { pid -> move(shopId, pid, MoveKind.Sale, -1.0, n.cost, mapOf("batteryId" to ref.id)).let { (d, m) -> batch.set(d, m) } }
        batch.commit()
    }

    fun sellScrap(shopId: String, kg: Double, amount: Double) {
        val batch = db.batch()
        batch.set(db.collection("shops/$shopId/batteries").document(), stamp() + mapOf(
            "kind" to "scrap", "kg" to round2(kg), "amount" to round2(amount), "at" to Timestamp(java.util.Date()),
        ))
        if (amount > 0) batch.set(db.collection("shops/$shopId/tx").document(), stamp() + mapOf(
            "kind" to TxKind.Sale.key, "amount" to round2(amount), "at" to Timestamp(java.util.Date()), "source" to "scrap", "ref" to "بيع خردة بطاريات ${round2(kg)} كغ",
        ))
        batch.commit()
    }

    /** حذف بيع بطارية: يلغى إيراده ويعود الصنف للمخزون. */
    fun deleteBattery(shopId: String, s: BattSale, cost: Double) {
        val batch = db.batch()
        batch.update(db.document("shops/$shopId/batteries/${s.id}"), mapOf("deleted" to true, "updatedAt" to FieldValue.serverTimestamp()))
        s.txId?.let { batch.update(db.document("shops/$shopId/tx/$it"), mapOf("deleted" to true, "updatedAt" to FieldValue.serverTimestamp())) }
        s.productId?.let { pid -> move(shopId, pid, MoveKind.Return, 1.0, cost, mapOf("batteryId" to s.id)).let { (d, m) -> batch.set(d, m) } }
        batch.commit()
        log(shopId, "حذف بيع بطارية", "${s.model} · ${s.serial}", s.net)
    }

    /* ---------- العربون والحجز ---------- */

    fun holds(shopId: String): Flow<List<Hold>> = listen("shops/$shopId/holds") {
        Hold(
            it.id, it.getString("name").orEmpty(), it.getString("phone").orEmpty(), it.getString("title").orEmpty(),
            it.getString("productId"), it.getString("variant").orEmpty(), it.getDouble("total") ?: 0.0,
            (it.get("pays") as? List<*>).orEmpty().mapNotNull { x -> (x as? Map<*, *>)?.let { m -> ((m["amount"] as? Number)?.toDouble() ?: 0.0) to ((m["at"] as? Number)?.toLong() ?: 0L) } },
            HoldStatus.of(it.getString("status")), it.millis("createdAt"),
        )
    }

    private fun holdPay(batch: com.google.firebase.firestore.WriteBatch, shopId: String, title: String, amount: Double) =
        batch.set(db.collection("shops/$shopId/tx").document(), stamp() + mapOf(
            "kind" to TxKind.Sale.key, "amount" to round2(amount), "at" to Timestamp(java.util.Date()), "source" to "hold", "ref" to "عربون: $title",
        ))

    fun addHold(shopId: String, name: String, phone: String, title: String, productId: String?, variant: String, total: Double, deposit: Double) {
        val batch = db.batch()
        batch.set(db.collection("shops/$shopId/holds").document(), stamp() + mapOf(
            "name" to name.trim(), "phone" to phone.trim(), "title" to title.trim(), "productId" to productId, "variant" to variant,
            "total" to round2(total), "status" to HoldStatus.Open.key,
            "pays" to if (deposit > 0) listOf(mapOf("amount" to round2(deposit), "at" to System.currentTimeMillis())) else emptyList(),
        ))
        if (deposit > 0) holdPay(batch, shopId, title, deposit)
        batch.commit()
    }

    fun payHold(shopId: String, h: Hold, amount: Double) {
        val batch = db.batch()
        batch.update(db.document("shops/$shopId/holds/${h.id}"), mapOf(
            "pays" to FieldValue.arrayUnion(mapOf("amount" to round2(amount), "at" to System.currentTimeMillis())), "updatedAt" to FieldValue.serverTimestamp(),
        ))
        holdPay(batch, shopId, h.title, amount)
        batch.commit()
    }

    /** التسليم: يُدفع الباقي إن وُجد، ويخرج الصنف من المخزون. */
    fun deliverHold(shopId: String, h: Hold, cost: Double) {
        val batch = db.batch()
        if (h.left > 0) {
            batch.update(db.document("shops/$shopId/holds/${h.id}"), mapOf("pays" to FieldValue.arrayUnion(mapOf("amount" to h.left, "at" to System.currentTimeMillis()))))
            holdPay(batch, shopId, h.title, h.left)
        }
        batch.update(db.document("shops/$shopId/holds/${h.id}"), mapOf("status" to HoldStatus.Done.key, "updatedAt" to FieldValue.serverTimestamp()))
        h.productId?.let { pid -> move(shopId, pid, MoveKind.Sale, -1.0, cost, mapOf("holdId" to h.id), h.variant).let { (d, m) -> batch.set(d, m) } }
        batch.commit()
    }

    fun cancelHold(shopId: String, h: Hold) {
        db.document("shops/$shopId/holds/${h.id}").update(mapOf("status" to HoldStatus.Cancelled.key, "updatedAt" to FieldValue.serverTimestamp()))
        log(shopId, "إلغاء حجز", "${h.title} · ${h.name}", h.paid)
    }

    /* ---------- لوحة المالك: باقات، نشاط، دفعات، رسالة عامة ---------- */

    fun plans(): Flow<List<Plan>> = callbackFlow {
        val reg = db.document("config/plans").addSnapshotListener { s, _ ->
            trySend((s?.get("list") as? List<*>).orEmpty().mapNotNull { x ->
                (x as? Map<*, *>)?.let { m ->
                    Plan(m["id"]?.toString().orEmpty(), m["name"]?.toString().orEmpty(), (m["price"] as? Number)?.toDouble() ?: 0.0,
                        (m["features"] as? List<*>).orEmpty().map { it.toString() }.toSet())
                }
            })
        }
        awaitClose { reg.remove() }
    }

    fun savePlans(list: List<Plan>) {
        db.document("config/plans").set(mapOf("list" to list.map { mapOf("id" to it.id, "name" to it.name, "price" to it.price, "features" to it.features.toList()) }))
    }

    /** تطبيق باقة: مميزاتها مفعّلة وغيرها مقفلة، مع تمديد الاشتراك. */
    fun applyPlan(shopId: String, plan: Plan, until: Long?) {
        db.document("shops/$shopId").update(mapOf(
            "plan" to plan.id, "paid" to FEATURES.associate { it.first to (it.first in plan.features) },
            "licenseUntil" to until?.let { Timestamp(java.util.Date(it)) }, "updatedAt" to FieldValue.serverTimestamp(),
        ))
    }

    fun presence(): Flow<Map<String, Presence>> = callbackFlow {
        val reg = db.collection("presence").addSnapshotListener { s, _ ->
            trySend(s?.documents.orEmpty().associate { d ->
                d.id to Presence(d.millis("lastSeen"), d.getString("version").orEmpty(), d.getString("by").orEmpty(),
                    (d.getLong("customers") ?: 0L).toInt(), (d.getLong("members") ?: 0L).toInt(), (d.getLong("smsUsed") ?: 0L).toInt())
            })
        }
        awaitClose { reg.remove() }
    }

    fun touchPresence(shopId: String, version: String, by: String, customers: Int, members: Int, smsUsed: Int) {
        db.document("presence/$shopId").set(mapOf(
            "lastSeen" to FieldValue.serverTimestamp(), "version" to version, "by" to by,
            "customers" to customers, "members" to members, "smsUsed" to smsUsed,
        ))
    }

    fun payments(): Flow<List<Payment>> = callbackFlow {
        val reg = db.collection("payments").orderBy("at", Query.Direction.DESCENDING).limit(300).addSnapshotListener { s, _ ->
            trySend(s?.documents.orEmpty().map { d ->
                Payment(d.id, d.getString("shopId").orEmpty(), d.getDouble("amount") ?: 0.0, (d.getLong("months") ?: 0L).toInt(),
                    d.getString("plan").orEmpty(), d.getString("note").orEmpty(), d.millis("at"))
            })
        }
        awaitClose { reg.remove() }
    }

    fun addPayment(shopId: String, amount: Double, months: Int, plan: String, note: String) {
        db.collection("payments").document().set(mapOf(
            "shopId" to shopId, "amount" to round2(amount), "months" to months, "plan" to plan, "note" to note.trim(), "at" to Timestamp(java.util.Date()),
        ))
    }

    fun broadcast(): Flow<Broadcast?> = callbackFlow {
        val reg = db.document("config/broadcast").addSnapshotListener { s, _ ->
            trySend(s?.takeIf { it.exists() }?.let { Broadcast(it.getString("text").orEmpty(), it.millis("at"), it.getBoolean("active") == true) })
        }
        awaitClose { reg.remove() }
    }

    fun setBroadcast(text: String, active: Boolean) {
        db.document("config/broadcast").set(mapOf("text" to text.trim(), "active" to active, "at" to Timestamp(java.util.Date())))
    }

    fun setAvatar(shopId: String, uid: String, avatar: Int?) {
        db.document("shops/$shopId/members/$uid").update(mapOf("avatar" to avatar, "updatedAt" to FieldValue.serverTimestamp()))
    }

    /* ---------- لوحة المالك: نسخ الخادم، التفريغ، الحذف ---------- */

    data class ServerBackup(val key: String, val at: String, val size: Long, val reason: String)

    private val wipeable = listOf(
        "customers", "tx", "recurring", "readings", "templates", "ps", "audit", "products", "moves",
        "suppliers", "stx", "menu", "orders", "holds", "usage", "smsQueue", "gateway",
    )

    suspend fun serverBackups(shopId: String): List<ServerBackup> {
        val a = admin("/v1/backups/list", JSONObject().put("shopId", shopId)).optJSONArray("items") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }.map { ServerBackup(it.optString("key"), it.optString("at"), it.optLong("size"), it.optString("reason")) }
    }

    suspend fun runServerBackup(shopId: String) { admin("/v1/backups/run", JSONObject().put("shopId", shopId)) }

    /** تفريغ بيانات المحل: يبقى المحل وحساباته وإعداداته. */
    suspend fun wipeShop(shopId: String) {
        for (c in wipeable) {
            while (true) {
                val docs = db.collection("shops/$shopId/$c").limit(400).get().await().documents
                if (docs.isEmpty()) break
                db.batch().apply { docs.forEach { delete(it.reference) } }.commit().await()
                if (docs.size < 400) break
            }
        }
        db.document("presence/$shopId").delete()
    }

    suspend fun deleteShop(shopId: String) {
        wipeShop(shopId)
        db.collection("shops/$shopId/settings").get().await().documents.forEach { it.reference.delete().await() }
        admin("/v1/shops/remove", JSONObject().put("shopId", shopId))
    }

    /** استعادة نسخة خادم: تفريغ ثم كتابة الوثائق كما كانت. */
    suspend fun restoreServerBackup(shopId: String, key: String) {
        val root = JSONObject(adminRaw("/v1/backups/get", JSONObject().put("key", key)))
        wipeShop(shopId)
        val writes = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any?>>>()
        for (c in wipeable + listOf("settings", "members")) {
            val pages = root.optJSONArray(c) ?: continue
            for (i in 0 until pages.length()) {
                val docs = pages.optJSONObject(i)?.optJSONArray("documents") ?: continue
                for (j in 0 until docs.length()) {
                    val d = docs.getJSONObject(j)
                    val id = d.getString("name").substringAfterLast('/')
                    val fields = d.optJSONObject("fields") ?: JSONObject()
                    writes += db.document("shops/$shopId/$c/$id") to fields.keys().asSequence().associateWith { k -> rest(fields.getJSONObject(k)) }
                }
            }
        }
        writes.chunked(400).forEach { chunk -> db.batch().apply { chunk.forEach { (r, m) -> set(r, m) } }.commit().await() }
        log(shopId, "استعادة نسخة", key.substringAfterLast(':'), null)
    }

    /** قيمة Firestore REST إلى قيمة SDK. */
    private fun rest(v: JSONObject): Any? = when {
        v.has("stringValue") -> v.getString("stringValue")
        v.has("integerValue") -> v.getString("integerValue").toLong()
        v.has("doubleValue") -> v.getDouble("doubleValue")
        v.has("booleanValue") -> v.getBoolean("booleanValue")
        v.has("timestampValue") -> Timestamp(java.util.Date.from(java.time.Instant.parse(v.getString("timestampValue"))))
        v.has("mapValue") -> v.getJSONObject("mapValue").optJSONObject("fields")?.let { f -> f.keys().asSequence().associateWith { rest(f.getJSONObject(it)) } } ?: emptyMap<String, Any?>()
        v.has("arrayValue") -> v.getJSONObject("arrayValue").optJSONArray("values")?.let { a -> (0 until a.length()).map { rest(a.getJSONObject(it)) } } ?: emptyList<Any?>()
        else -> null
    }

    /* ---------- سقف الدين، واتساب، رابط الكشف، جوال الإرسال ---------- */

    fun setLimit(shopId: String, customerId: String, limit: Double) {
        db.document("shops/$shopId/customers/$customerId").update(mapOf("limit" to round2(limit), "updatedAt" to FieldValue.serverTimestamp()))
    }

    fun setWaPrefix(shopId: String, customerId: String, prefix: String) {
        db.document("shops/$shopId/customers/$customerId").update(mapOf("waPrefix" to prefix, "updatedAt" to FieldValue.serverTimestamp()))
    }

    /** رمز قصير للزبون يُنشأ مرة واحدة ويُحفظ معه. */
    fun ensureLink(shopId: String, c: Customer): String {
        if (c.link.isNotBlank()) return c.link
        val abc = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val token = (1..7).map { abc[java.security.SecureRandom().nextInt(abc.length)] }.joinToString("")
        db.batch().apply {
            set(db.document("links/$token"), mapOf("shopId" to shopId, "customerId" to c.id, "createdAt" to FieldValue.serverTimestamp()))
            update(db.document("shops/$shopId/customers/${c.id}"), mapOf("link" to token))
        }.commit()
        return token
    }

    fun smsSettings(shopId: String): Flow<SmsSettings> = callbackFlow {
        val reg = db.document("shops/$shopId/settings/sms").addSnapshotListener { s, _ ->
            trySend(
                if (s == null || !s.exists()) SmsSettings() else SmsSettings(
                    s.getBoolean("link") == true, s.getBoolean("reminderOn") == true,
                    (s.getLong("reminderDay") ?: 1L).toInt(), (s.getLong("reminderHour") ?: 10L).toInt(),
                    s.getString("reminderTemplate") ?: "b_debt", s.getBoolean("reminderLateOnly") == true,
                    s.getString("gatewayDevice").orEmpty(), s.getString("gatewayName").orEmpty(),
                ),
            )
        }
        awaitClose { reg.remove() }
    }

    fun saveSmsSettings(shopId: String, v: SmsSettings) {
        db.document("shops/$shopId/settings/sms").set(mapOf(
            "link" to v.link, "reminderOn" to v.reminderOn, "reminderDay" to v.reminderDay, "reminderHour" to v.reminderHour,
            "reminderTemplate" to v.reminderTemplate, "reminderLateOnly" to v.reminderLateOnly,
            "gatewayDevice" to v.gatewayDevice, "gatewayName" to v.gatewayName,
        ))
    }

    fun gatewayRequest(shopId: String): Flow<GatewayRequest?> = callbackFlow {
        val reg = db.document("shops/$shopId/gateway/request").addSnapshotListener { s, _ ->
            trySend(s?.takeIf { it.exists() }?.let { GatewayRequest(it.getString("byName").orEmpty(), it.millis("at"), it.getString("status").orEmpty()) })
        }
        awaitClose { reg.remove() }
    }

    fun requestGateway(shopId: String) {
        db.document("shops/$shopId/gateway/request").set(mapOf("byName" to _account.value?.displayName, "at" to FieldValue.serverTimestamp(), "status" to "pending"))
    }

    fun answerGatewayRequest(shopId: String, accepted: Boolean) {
        db.document("shops/$shopId/gateway/request").update(mapOf("status" to if (accepted) "accepted" else "declined"))
    }
}
