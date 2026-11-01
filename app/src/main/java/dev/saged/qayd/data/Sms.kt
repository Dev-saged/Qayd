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
    val VARS = listOf("الاسم", "المحل", "الرصيد", "المبلغ", "التاريخ", "الشهر", "القراءة_السابقة", "القراءة_الحالية", "الاستهلاك", "سعر_الكيلو")

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

    fun fill(body: String, vars: Map<String, String>): String =
        VARS.fold(body) { acc, k -> acc.replace("{$k}", vars[k] ?: "") }
}
