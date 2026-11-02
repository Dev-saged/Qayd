package dev.saged.qayd.voice

import dev.saged.qayd.data.CustomerBalance
import dev.saged.qayd.data.TxKind

/** فهم الجملة العربية المنطوقة: النوع، المبلغ، والزبون. يعمل بالكامل على الجهاز. */
object VoiceParse {
    data class Result(val kind: TxKind?, val amount: Double?, val customer: CustomerBalance?, val confidence: Float)

    fun norm(s: String): String = s
        .replace(Regex("[ً-ْـ]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ة', 'ه').replace('ى', 'ي').replace('ؤ', 'و').replace('ئ', 'ي')
        .replace(Regex("[٠-٩]")) { (it.value[0] - '٠').toString() }
        .lowercase().trim()

    private val ONES = mapOf(
        "صفر" to 0, "واحد" to 1, "وحده" to 1, "اثنين" to 2, "اثنان" to 2, "ثنتين" to 2, "تنين" to 2, "ثلاث" to 3, "ثلاثه" to 3, "تلاته" to 3, "تلات" to 3,
        "اربع" to 4, "اربعه" to 4, "خمس" to 5, "خمسه" to 5, "ست" to 6, "سته" to 6, "سبع" to 7, "سبعه" to 7, "ثمان" to 8, "ثمانيه" to 8, "تمن" to 8, "تمانيه" to 8, "ثماني" to 8,
        "تسع" to 9, "تسعه" to 9, "عشر" to 10, "عشره" to 10,
        "احدعشر" to 11, "حداشر" to 11, "اثنعشر" to 12, "اطنعش" to 12, "طنعش" to 12,
    )
    private val TENS = mapOf(
        "عشرين" to 20, "عشرون" to 20, "ثلاثين" to 30, "تلاتين" to 30, "اربعين" to 40, "خمسين" to 50, "ستين" to 60, "سبعين" to 70,
        "ثمانين" to 80, "تمانين" to 80, "تسعين" to 90,
    )
    private val HUNDREDS = mapOf(
        "ميه" to 100, "مئه" to 100, "مائه" to 100, "مية" to 100, "ميتين" to 200, "مئتين" to 200, "مئتان" to 200,
        "ثلاثميه" to 300, "تلتميه" to 300, "اربعميه" to 400, "اربعمية" to 400, "خمسميه" to 500, "ستميه" to 600, "سبعميه" to 700, "ثمانميه" to 800, "تمنميه" to 800, "تسعميه" to 900,
    )
    private val THOUSANDS = mapOf("الف" to 1000, "الفين" to 2000, "الفان" to 2000, "الاف" to 1000)

    /** يجمع الأعداد المكتوبة بالكلمات أو الأرقام، مثل "خمسه وعشرين" و"ميه وخمسين" و"الف ونص". */
    fun amount(text: String): Double? {
        Regex("\\d+(?:[.,]\\d+)?").find(text)?.let { return it.value.replace(',', '.').toDoubleOrNull() }
        val words = text.split(Regex("\\s+")).map { it.removePrefix("و").removePrefix("ب").removeSuffix("شيكل") }.filter { it.isNotBlank() }
        var total = 0.0
        var current = 0.0
        var found = false
        for (raw in words) {
            val w = raw
            when {
                ONES[w] != null -> { current += ONES.getValue(w); found = true }
                ONES[w + "ه"] != null -> { current += ONES.getValue(w + "ه"); found = true }
                TENS[w] != null -> { current += TENS.getValue(w); found = true }
                HUNDREDS[w] != null -> { current += HUNDREDS.getValue(w); found = true }
                THOUSANDS[w] != null -> { current = (if (current == 0.0) 1.0 else current) * THOUSANDS.getValue(w); total += current; current = 0.0; found = true }
                w == "نص" || w == "نصف" -> { current += if (current == 0.0 && total == 0.0) .5 else (if (total > 0 && current == 0.0) 500.0 else .5); found = true }
                w == "ربع" -> { current += .25; found = true }
            }
        }
        return if (found) total + current else null
    }

    fun kind(text: String): TxKind? = when {
        Regex("(دفع|سدد|سداد|استلمت|قبضت|دفعه|رجع)").containsMatchIn(text) -> TxKind.Payment
        Regex("(بعت|بيع|مبيعات|نقدي)").containsMatchIn(text) && !Regex("(علي|عليه|دين)").containsMatchIn(text) -> TxKind.Sale
        Regex("(دين|علي|عليه|سجل|اخذ|اخد|حساب)").containsMatchIn(text) -> TxKind.Debt
        else -> null
    }

    /** أقرب زبون: تطابق كلمات الاسم مع الجملة، والاسم الكامل أولى. */
    fun customer(text: String, customers: List<CustomerBalance>): Pair<CustomerBalance, Float>? {
        val t = norm(text)
        return customers.map { c ->
            val n = norm(c.customer.name)
            val parts = n.split(Regex("\\s+")).filter { it.length > 1 && it != "ابو" && it != "ام" }
            val hits = parts.count { p -> t.contains(p) }
            val score = when {
                t.contains(n) -> 1f
                parts.isEmpty() -> 0f
                else -> hits.toFloat() / parts.size * (if (n.startsWith("ابو") || n.startsWith("ام")) (if (t.contains(n.substringBefore(' ') + " " + parts.firstOrNull())) 1f else .8f) else 1f)
            }
            c to score
        }.filter { it.second >= .5f }.maxByOrNull { it.second }
    }

    fun parse(text: String, customers: List<CustomerBalance>): Result {
        val t = norm(text)
        val c = customer(t, customers)
        val k = kind(t) ?: if (c != null) TxKind.Debt else TxKind.Sale
        val a = amount(t)
        val conf = listOf(c != null || k == TxKind.Sale, a != null, kind(t) != null).count { it } / 3f * (c?.second ?: 1f)
        return Result(k, a, c?.first, conf)
    }
}
