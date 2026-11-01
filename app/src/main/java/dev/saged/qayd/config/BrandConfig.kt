package dev.saged.qayd.config

/** نقطة التحكم الوحيدة بالهوية: الاسم، الألوان (OKLCH)، الوحدات، الإصدار. لا ألوان أو نصوص هوية خارجها. */
object BrandConfig {
    const val NAME = "قيد"
    const val NAME_LATIN = "Qayd"
    const val TAGLINE = "كل حق في قيده"
    const val UPDATE_REPO = "Dev-saged/Qayd"
    const val CURRENCY = "₪"

    /** واتساب مالك المنصة لطلبات التفعيل والمميزات المدفوعة. */
    const val OWNER_WHATSAPP = "972597857250"

    /** بطاقة المصمم في "المزيد". */
    object Dev {
        const val NAME = "ساجد العبادلة"
        const val TITLE = "المهندس ساجد العبادلة"
        const val PLACE = "صُنع بكل حب من فلسطين — غزة"
        const val YEAR = 2026
        val LINKS = listOf(
            "whatsapp" to "https://wa.me/972597857250",
            "telegram" to "https://t.me/+972597857250",
            "instagram" to "https://instagram.com/sajedd.0",
            "facebook" to "https://facebook.com/saged.t",
            "x" to "https://x.com/saged_al",
            "github" to "https://github.com/Dev-saged",
        )
    }

    /** Worker العمليات الإدارية (إنشاء المحلات والموظفين). */
    const val ADMIN_API = "https://qayd-admin.dev-mr07.workers.dev"

    /** دقائق قبل نهاية الجلسة يبدأ عندها تنبيه "قرب الانتهاء". */
    const val PS_WARN_MINUTES = 20

    /** أزرار الإضافة السريعة في النمباد. */
    val QUICK_ADDS = listOf(5, 10, 20, 50)

    /** أيام بلا دفعة يصبح بعدها الزبون المدين متأخراً. */
    const val OVERDUE_DAYS = 30

    /** كل لون زوج [فاتح، داكن] بصيغة OKLCH: إضاءة 0..1، تشبّع، زاوية. */
    data class Tone(val light: Oklch, val dark: Oklch)
    data class Oklch(val l: Float, val c: Float, val h: Float)

    private fun t(ll: Float, lc: Float, lh: Float, dl: Float, dc: Float, dh: Float) =
        Tone(Oklch(ll, lc, lh), Oklch(dl, dc, dh))

    object Palette {
        val bg = t(.97f, .012f, 85f, .16f, .014f, 262f)
        val surface = t(1f, 0f, 0f, .215f, .016f, 262f)
        val surface2 = t(.95f, .014f, 85f, .26f, .018f, 262f)
        val ink = t(.22f, .02f, 262f, .95f, .01f, 85f)
        val mute = t(.46f, .02f, 262f, .73f, .02f, 262f)
        val line = t(.89f, .014f, 85f, .31f, .016f, 262f)
        val pri = t(.54f, .13f, 78f, .81f, .14f, 85f)
        val onPri = t(.985f, .015f, 85f, .20f, .04f, 80f)
        val danger = t(.50f, .20f, 25f, .73f, .17f, 25f)
        val ok = t(.48f, .13f, 155f, .77f, .15f, 155f)
        val warn = t(.52f, .14f, 60f, .82f, .15f, 75f)
    }
}
