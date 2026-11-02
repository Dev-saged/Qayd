package dev.saged.qayd.data

import androidx.compose.runtime.Immutable

/** باقة اشتراك: سعر شهري ومجموعة مميزات مدفوعة. */
@Immutable
data class Plan(val id: String, val name: String, val price: Double, val features: Set<String>)

/** آخر نشاط للمحل، يكتبه تطبيق المحل عند فتحه. */
@Immutable
data class Presence(val lastSeen: Long, val version: String, val by: String, val customers: Int, val members: Int, val smsUsed: Int)

/** دفعة اشتراك من محل لمالك المنصة. */
@Immutable
data class Payment(val id: String, val shopId: String, val amount: Double, val months: Int, val plan: String, val note: String, val at: Long)

/** رسالة من مالك المنصة تظهر لكل المحلات. */
@Immutable
data class Broadcast(val text: String, val at: Long, val active: Boolean)
