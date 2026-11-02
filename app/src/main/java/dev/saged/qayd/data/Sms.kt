package dev.saged.qayd.data

import androidx.compose.runtime.Immutable

enum class SmsStatus(val key: String, val label: String) {
    Queued("queued", "بالانتظار"),
    Sending("sending", "يُرسل"),
    Sent("sent", "أُرسلت"),
    Delivered("delivered", "وصلت"),
    Failed("failed", "فشلت");

    companion object {
        fun of(k: String?) = entries.firstOrNull { it.key == k } ?: Queued
    }
}

@Immutable
data class SmsMsg(
    val id: String,
    val to: String,
    val text: String,
    val name: String,
    val template: String,
    val status: SmsStatus,
    val error: String,
    val at: Long,
    val byName: String,
)

@Immutable
data class Gateway(val online: Boolean, val device: String, val lastSeen: Long, val uid: String) {
    /** متصل فعلياً: أعلن تشغيله وأرسل نبضة خلال آخر 3 دقائق. */
    fun live(now: Long) = online && now - lastSeen < 180_000

    companion object {
        val None = Gateway(false, "", 0, "")
    }
}

enum class TemplateKind { Debt, Payment, Power, General }

@Immutable
data class Template(val id: String, val title: String, val body: String, val kind: TemplateKind, val builtIn: Boolean = false)

object Templates {
    /** المتغيرات التي يمكن إدراجها في أي قالب. */
    val VARS = listOf("الاسم", "المحل", "الرصيد", "المبلغ", "التاريخ", "الشهر", "القراءة_السابقة", "القراءة_الحالية", "الاستهلاك", "سعر_الكيلو", "رابط_الكشف")

    val BUILT_IN = listOf(
        Template(
            "b_debt", "تذكير بالرصيد",
            "مرحباً {الاسم}،\nنودّ تذكيركم بلطف أن رصيدكم المستحق لدى {المحل} هو {الرصيد}.\nنقدّر تعاملكم الكريم والتزامكم الدائم.\n— {المحل}",
            TemplateKind.Debt, true,
        ),
        Template(
            "b_payment", "تأكيد استلام دفعة",
            "مرحباً {الاسم}،\nاستلمنا دفعتكم بقيمة {المبلغ} بتاريخ {التاريخ}، ورصيدكم الحالي {الرصيد}.\nشكراً لثقتكم.\n— {المحل}",
            TemplateKind.Payment, true,
        ),
        Template(
            "b_power", "فاتورة الكهرباء",
            "مرحباً {الاسم}،\nفاتورة الكهرباء لشهر {الشهر}:\nالقراءة السابقة: {القراءة_السابقة}\nالقراءة الحالية: {القراءة_الحالية}\nالاستهلاك: {الاستهلاك} كيلو × {سعر_الكيلو}\nالمبلغ المستحق: {المبلغ}\nإجمالي رصيدكم: {الرصيد}\n— {المحل}",
            TemplateKind.Power, true,
        ),
        Template(
            "b_thanks", "شكر على السداد",
            "مرحباً {الاسم}،\nتم سداد حسابكم بالكامل لدى {المحل}. شكراً لالتزامكم، ويسعدنا خدمتكم دائماً.\n— {المحل}",
            TemplateKind.General, true,
        ),
    )

    /** يملأ القالب، ويُلحق رابط الكشف آخر الرسالة إن كان مفعّلاً ولم يُذكر في القالب. */
    fun fill(body: String, vars: Map<String, String>): String {
        val link = vars["رابط_الكشف"].orEmpty()
        val withLink = if (link.isNotBlank() && "{رابط_الكشف}" !in body) "$body\nكشف حسابك: {رابط_الكشف}" else body
        return VARS.fold(withLink) { acc, k -> acc.replace("{$k}", vars[k] ?: "") }.replace("كشف حسابك: \n", "").trimEnd()
    }
}

/** إعدادات الرسائل للمحل: رابط الكشف، التذكير الشهري، وجوال الإرسال المعتمد. */
@Immutable
data class SmsSettings(
    val link: Boolean = false,
    val reminderOn: Boolean = false,
    val reminderDay: Int = 1,
    val reminderHour: Int = 10,
    val reminderTemplate: String = "b_debt",
    val reminderLateOnly: Boolean = false,
    val gatewayDevice: String = "",
    val gatewayName: String = "",
)

@Immutable
data class GatewayRequest(val byName: String, val at: Long, val status: String) {
    val pending get() = status == "pending" && System.currentTimeMillis() - at < 6 * 3_600_000L
}

/** روابط الكشف تُبنى من رمز الزبون. */
object StatementLinks {
    const val BASE = "https://qayd-admin.dev-mr07.workers.dev/s/"
    /** يزوّده المحل حين يكون الخيار مفعّلاً؛ وإلا لا رابط. */
    var of: (Customer) -> String? = { null }
}
