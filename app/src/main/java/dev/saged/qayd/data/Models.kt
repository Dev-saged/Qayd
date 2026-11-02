package dev.saged.qayd.data

import androidx.compose.runtime.Immutable

enum class Role(val label: String) {
    SuperAdmin("مالك المنصة"),
    Owner("صاحب المحل"),
    Staff("موظف"),
}

/** مميزات يحددها مالك المنصة مجانية أو مدفوعة (الوحدات + كشف PDF + التقارير). */
val FEATURES = listOf("ps" to "شاشات PlayStation", "electricity" to "الكهرباء", "sms" to "الرسائل", "pdf" to "كشف الحساب PDF", "reports" to "التقارير", "autoBackup" to "النسخ الاحتياطي التلقائي", "inventory" to "الأصناف والمخزون", "suppliers" to "الموردون")

enum class Module(val key: String, val label: String) {
    Debts("debts", "الديون"),
    Ps("ps", "شاشات PlayStation"),
    Electricity("electricity", "الكهرباء"),
    Sms("sms", "الرسائل"),
    Inventory("inventory", "الأصناف والمخزون"),
    Suppliers("suppliers", "الموردون"),
}

enum class SyncState(val label: String) {
    Synced("متزامن"),
    Pending("بانتظار المزامنة"),
    Offline("بدون اتصال"),
}

@Immutable
data class Account(
    val uid: String,
    val email: String,
    val displayName: String,
    val role: Role,
    val shopId: String?,
)

@Immutable
data class Shop(
    val id: String,
    val name: String,
    val suffix: String,
    val active: Boolean,
    val licenseUntil: Long?,
    val modules: Set<Module>,
    val psScreens: Int,
    val paid: Set<String> = emptySet(),
    /** حد رسائل الشهر، و-1 بلا حد. */
    val smsQuota: Int = -1,
    val type: String = BizType.General.key,
) {
    fun smsLeft(used: Int): Int? = smsQuota.takeIf { it >= 0 }?.let { (it - used).coerceAtLeast(0) }

    /** الوحدة مفعّلة للمحل، ومجانية أو مدفوعة له. */
    fun featureOn(m: Module, features: Map<String, String>) = m in modules && unlocked(m.key, features)
    fun unlocked(key: String, features: Map<String, String>) = features[key] != "paid" || key in paid
    fun daysLeft(now: Long = System.currentTimeMillis()): Int? = licenseUntil?.let { ((it - now) / 86_400_000L).toInt() }

    /** سبب إيقاف المحل، أو null إن كان فعّالاً. */
    fun blockReason(now: Long = System.currentTimeMillis()): String? = when {
        !active -> "اشتراك المحل موقوف"
        licenseUntil != null && licenseUntil < now -> "انتهى اشتراك المحل"
        else -> null
    }
}

@Immutable
data class NewShop(
    val name: String,
    val suffix: String,
    val ownerName: String,
    val ownerUsername: String,
    val ownerPassword: String,
    val modules: Set<Module>,
    val psScreens: Int,
    val type: String = BizType.General.key,
)

class QaydError(message: String) : Exception(message)

object Validate {
    private val suffixRe = Regex("^[a-z0-9-]{2,30}\\.[a-z]{2,10}$")
    private val userRe = Regex("^[a-z0-9][a-z0-9._-]{1,31}$")

    fun suffix(s: String): String? = if (suffixRe.matches(s)) null else "النطاق مثل anoar.ps (حروف إنجليزية صغيرة)"
    fun username(s: String): String? = if (userRe.matches(s)) null else "حروف إنجليزية صغيرة وأرقام (2-32)"
    fun password(s: String): String? = if (s.length >= 8) null else "8 أحرف على الأقل"
    fun login(s: String): String? = if (s.count { it == '@' } == 1 && s.substringAfter('@').contains('.')) null else "اكتب اسم المستخدم كاملاً مثل ahmad@anoar.ps"
}

@Immutable
data class Prices(val psRate: Double, val kwhPrice: Double) {
    companion object { val None = Prices(0.0, 0.0) }
}
