package dev.saged.qayd.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dev.saged.qayd.R
import dev.saged.qayd.config.BrandConfig
import dev.saged.qayd.data.Customer
import dev.saged.qayd.data.Shop
import dev.saged.qayd.data.Tx
import dev.saged.qayd.data.TxKind
import dev.saged.qayd.system.QaydFiles
import dev.saged.qayd.ui.util.money
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** كشف الحساب A4 والإيصال الحراري 80mm بهوية قيد: عناوين بالكوفي، أيقونات مرسومة، ورمز تحقق. */
class QaydPdf(private val ctx: Context) {
    private val regular: Typeface = ResourcesCompat.getFont(ctx, R.font.readex_regular) ?: Typeface.DEFAULT
    private val semi: Typeface = ResourcesCompat.getFont(ctx, R.font.readex_semibold) ?: Typeface.DEFAULT_BOLD
    private val display: Typeface = runCatching {
        Typeface.Builder(ctx.assets, "fonts/ReemKufi.ttf").setFontVariationSettings("'wght' 700").build()
    }.getOrNull() ?: semi

    private val ink = 0xFF0E1322.toInt()
    private val ink2 = 0xFF1C2540.toInt()
    private val gold = 0xFFC08A2E.toInt()
    private val goldL = 0xFFE9C46A.toInt()
    private val paper = 0xFFFBF8F1.toInt()
    private val paper2 = 0xFFF3EEE2.toInt()
    private val line = 0xFFE6DFCF.toInt()
    private val body = 0xFF22263A.toInt()
    private val mute = 0xFF6B6F7B.toInt()
    private val red = 0xFFB3261E.toInt()
    private val green = 0xFF1B7A4A.toInt()
    private val white = 0xFFFFFFFF.toInt()

    private val date = SimpleDateFormat("d/M/yyyy", Locale.US)
    private val stamp = SimpleDateFormat("d/M/yyyy  HH:mm", Locale.US)

    private object Icon {
        const val USER = "M15.5 8a3.5 3.5 0 1 1-7 0a3.5 3.5 0 1 1 7 0M5 20c0-3.9 3.1-7 7-7s7 3.1 7 7"
        const val PHONE = "M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a1 1 0 0 1-1 1A16 16 0 0 1 4 5a1 1 0 0 1 1-1z"
        const val UP = "M7 17L17 7M9 7h8v8"
        const val DOWN = "M7 7l10 10M17 9v8H9"
        const val SCALE = "M12 4v16M8 20h8M5 7h14M5 7l-2.5 6a2.5 2.5 0 0 0 5 0zM19 7l-2.5 6a2.5 2.5 0 0 0 5 0z"
        const val CAL = "M4 6a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1zM4 10h16M8 3v4M16 3v4"
        const val DOC = "M6 3h9l4 4v14H6zM14 3v5h5M9 13h7M9 17h5"
    }

    /** للمعاينة في الاختبارات: يرسم الصفحات صوراً بدل PDF. */
    var preview: ((Int, Bitmap) -> Unit)? = null

    private inner class Out(private val w: Float, private val h: Float) {
        private val pdf = if (preview == null) PdfDocument() else null
        private var page: PdfDocument.Page? = null
        private var bmp: Bitmap? = null
        private var n = 0

        fun begin(): Canvas {
            n++
            pdf?.let { d -> return d.startPage(PdfDocument.PageInfo.Builder(w.toInt(), h.toInt(), n).create()).also { page = it }.canvas }
            return Canvas(Bitmap.createBitmap((w * 2).toInt(), (h * 2).toInt(), Bitmap.Config.ARGB_8888).also { bmp = it }).apply { scale(2f, 2f) }
        }

        fun end() {
            page?.let { pdf?.finishPage(it) }
            bmp?.let { preview?.invoke(n, it) }
        }

        fun file(name: String): File {
            val dir = File(ctx.cacheDir, "pdf").apply { mkdirs() }
            val f = File(dir, name.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
            pdf?.let { d -> f.outputStream().use { d.writeTo(it) }; d.close() }
            return f
        }
    }

    /* ---------- أدوات الرسم ---------- */

    private fun Canvas.text(
        s: String, x: Float, y: Float, w: Float, size: Float, color: Int,
        face: Typeface = regular, end: Boolean = false, center: Boolean = false, lines: Int = 1,
    ): Float {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = face }
        val align = when {
            center -> Layout.Alignment.ALIGN_CENTER
            end -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        val l = StaticLayout.Builder.obtain(s, 0, s.length, tp, w.toInt().coerceAtLeast(1))
            .setAlignment(align).setTextDirection(TextDirectionHeuristics.RTL).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build()
        save(); translate(x, y); l.draw(this); restore()
        return l.height.toFloat()
    }

    private fun Canvas.icon(d: String, x: Float, y: Float, size: Float, color: Int, stroke: Float = 1.8f) {
        val p = PathParser.createPathFromPathData(d)
        p.transform(Matrix().apply { setScale(size / 24f, size / 24f); postTranslate(x, y) })
        drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke * size / 24f; this.color = color
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        })
    }

    private fun fill(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c }

    /** علامة قيد: ق كوفية على ورقة الدفتر. */
    private fun Canvas.mark(x: Float, y: Float, s: Float) {
        val r = RectF(x, y, x + s, y + s)
        drawRoundRect(r, s * .24f, s * .24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, y, 0f, y + s, 0xFFFBF7EE.toInt(), 0xFFEAE1CC.toInt(), Shader.TileMode.CLAMP) })
        val k = s / 108f
        fun path(d: String) = PathParser.createPathFromPathData(d).apply {
            transform(Matrix().apply { setScale(.92f, .92f, 54f, 57f); postScale(k, k); postTranslate(x, y) })
        }
        drawPath(path("M54 36H75V75H30V54H39V66H66V57H54ZM62 43V50H68V43Z").apply { fillType = Path.FillType.EVEN_ODD }, fill(ink))
        drawPath(path("M57 20.5L62.5 26L57 31.5L51.5 26Z"), fill(gold))
        drawPath(path("M70 20.5L75.5 26L70 31.5L64.5 26Z"), fill(ink))
        drawPath(path("M24 81H84V85H24Z"), fill(gold))
    }

    /** خطا القيد الذهبيان. */
    private fun Canvas.rule(x1: Float, x2: Float, y: Float) {
        drawRect(x1, y, x2, y + 1.6f, fill(gold))
        drawRect(x1, y + 3.4f, x2, y + 4f, fill(gold))
    }

    private fun num(v: Double) = "⁦${money(v)} ${BrandConfig.CURRENCY}⁩"

    private fun qr(data: String, size: Int): Bitmap {
        val m = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 0))
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until size) for (y in 0 until size) setPixel(x, y, if (m[x, y]) ink else white)
        }
    }

    private fun label(t: Tx) = when (t.source) {
        "power" -> "فاتورة كهرباء"
        "ps" -> t.ref ?: "جلسة شاشة"
        else -> t.kind.label
    }

    /* ---------- كشف الحساب ---------- */

    /** كشف حساب زبون: الأقدم أولاً مع الرصيد بعد كل حركة. */
    fun statement(shop: Shop, c: Customer, history: List<Tx>): File {
        val rows = history.sortedBy { it.at }
        var run = 0.0
        val running = rows.map { t -> run += if (t.kind == TxKind.Debt) t.amount else -t.amount; run }
        val debts = rows.filter { it.kind == TxKind.Debt }.sumOf { it.amount }
        val pays = rows.filter { it.kind == TxKind.Payment }.sumOf { it.amount }
        val balance = debts - pays
        val no = SimpleDateFormat("yyMMdd-HHmm", Locale.US).format(Date())

        val w = 595f
        val h = 842f
        val m = 34f
        val rowH = 24f
        val footTop = h - 74f
        val firstTop = 326f
        val nextTop = 112f
        val capFirst = ((footTop - 40f - firstTop) / rowH).toInt()
        val capNext = ((footTop - 40f - nextTop) / rowH).toInt()
        val pages = if (rows.size <= capFirst) 1 else 1 + (rows.size - capFirst + capNext - 1) / capNext

        val out = Out(w, h)
        var index = 0
        for (pg in 1..pages) {
            val cv = out.begin()
            cv.drawRect(0f, 0f, w, h, fill(white))

            // الترويسة
            val band = if (pg == 1) 132f else 76f
            cv.drawRect(0f, 0f, w, band, Paint().apply { shader = LinearGradient(w, 0f, 0f, band, ink2, ink, Shader.TileMode.CLAMP) })
            cv.rule(0f, w, band)
            val logo = if (pg == 1) 64f else 40f
            cv.mark(w - m - logo, (band - logo) / 2, logo)
            val titleW = w - 2 * m - logo - 14f - 170f
            if (pg == 1) {
                cv.text(shop.name, m + 170f, 30f, titleW, 25f, white, display)
                cv.text("كشف حساب", m + 170f, 72f, titleW, 13f, goldL, semi)
                val meta = RectF(m, 30f, m + 158f, 102f)
                cv.drawRoundRect(meta, 12f, 12f, fill(0x22FFFFFF))
                cv.text("رقم الكشف", m + 12f, 38f, 134f, 9f, 0xFFB9BECB.toInt())
                cv.text("⁦$no⁩", m + 12f, 50f, 134f, 11.5f, white, semi)
                cv.text("تاريخ الإصدار", m + 12f, 68f, 134f, 9f, 0xFFB9BECB.toInt())
                cv.text("⁦${stamp.format(Date())}⁩", m + 12f, 80f, 134f, 11.5f, white, semi)
            } else {
                cv.text(shop.name, m + 170f, 18f, titleW, 17f, white, display)
                cv.text("كشف حساب ${c.name} — تابع", m + 170f, 46f, titleW, 10.5f, goldL)
                cv.text("⁦$no⁩", m, 30f, 160f, 10f, 0xFFB9BECB.toInt())
            }

            var y = band + 18f
            if (pg == 1) {
                // بطاقة الزبون
                val card = RectF(m, y, w - m, y + 60f)
                cv.drawRoundRect(card, 14f, 14f, fill(paper))
                cv.drawRoundRect(RectF(w - m - 48f, y + 10f, w - m - 10f, y + 50f), 11f, 11f, fill(paper2))
                cv.icon(Icon.USER, w - m - 40f, y + 19f, 22f, ink)
                cv.text("الزبون", m + 160f, y + 11f, w - 2 * m - 220f, 9.5f, mute)
                cv.text(c.name, m + 160f, y + 24f, w - 2 * m - 220f, 17f, body, semi)
                if (c.phone.isNotBlank()) {
                    cv.icon(Icon.PHONE, m + 130f, y + 22f, 15f, gold)
                    cv.text("⁦${c.phone}⁩", m + 14f, y + 21f, 112f, 12f, body, semi, end = true)
                }
                y += 76f

                // الملخص
                val gap = 10f
                val bw = (w - 2 * m - 2 * gap) / 3
                listOf(
                    listOf("مجموع الديون", debts, red, Icon.UP),
                    listOf("مجموع الدفعات", pays, green, Icon.DOWN),
                    listOf(if (balance >= 0) "الرصيد المستحق" else "رصيد مقدّم للزبون", abs(balance), goldL, Icon.SCALE),
                ).forEachIndexed { i, (lbl, v, col, ic) ->
                    val x = w - m - (i + 1) * bw - i * gap
                    val r = RectF(x, y, x + bw, y + 70f)
                    val hero = i == 2
                    cv.drawRoundRect(r, 14f, 14f, if (hero) Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x, y, x + bw, y + 70f, ink2, ink, Shader.TileMode.CLAMP) } else fill(paper))
                    if (!hero) cv.drawRoundRect(r, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = .8f; color = line })
                    cv.drawCircle(x + bw - 24f, y + 22f, 12f, fill(if (hero) 0x26FFFFFF else (col as Int) and 0x00FFFFFF or 0x1F000000))
                    cv.icon(ic as String, x + bw - 32f, y + 14f, 16f, col as Int, 2f)
                    cv.text(lbl as String, x + 12f, y + 15f, bw - 52f, 10f, if (hero) 0xFFB9BECB.toInt() else mute)
                    cv.text(num(v as Double), x + 12f, y + 38f, bw - 24f, 17f, if (hero) white else col, semi)
                }
                y = firstTop - 26f
            } else {
                y = nextTop - 26f
            }

            // الجدول
            val cols = floatArrayOf(84f, 0f, 84f, 84f, 96f)
            cols[1] = w - 2 * m - cols[0] - cols[2] - cols[3] - cols[4]
            fun colX(i: Int) = w - m - cols.take(i + 1).sum()
            cv.drawRoundRect(RectF(m, y, w - m, y + 26f), 9f, 9f, fill(ink))
            listOf("التاريخ", "البيان", "دين", "دفعة", "الرصيد").forEachIndexed { i, s ->
                cv.text(s, colX(i) + 8f, y + 6.5f, cols[i] - 16f, 10f, goldL, semi, center = i >= 2)
            }
            y += 26f
            val cap = if (pg == 1) capFirst else capNext
            val end = (index + cap).coerceAtMost(rows.size)
            if (rows.isEmpty()) cv.text("لا حركات بعد.", m, y + 18f, w - 2 * m, 12f, mute, center = true)
            for (r in index until end) {
                val t = rows[r]
                if ((r - index) % 2 == 1) cv.drawRect(m, y, w - m, y + rowH, fill(paper))
                val debt = t.kind == TxKind.Debt
                cv.text("⁦${date.format(Date(t.at))}⁩", colX(0) + 8f, y + 6.5f, cols[0] - 16f, 9.5f, mute)
                val dx = colX(1) + cols[1] - 14f
                cv.drawPath(Path().apply { moveTo(dx, y + 8.5f); lineTo(dx + 3.5f, y + 12f); lineTo(dx, y + 15.5f); lineTo(dx - 3.5f, y + 12f); close() }, fill(if (debt) red else green))
                cv.text(label(t), colX(1) + 8f, y + 6f, cols[1] - 30f, 10.5f, body)
                cv.text(if (debt) num(t.amount) else "", colX(2) + 6f, y + 6f, cols[2] - 12f, 10.5f, red, semi, center = true)
                cv.text(if (!debt) num(t.amount) else "", colX(3) + 6f, y + 6f, cols[3] - 12f, 10.5f, green, semi, center = true)
                cv.text(num(running[r]), colX(4) + 6f, y + 6f, cols[4] - 12f, 10.5f, body, center = true)
                cv.drawRect(m, y + rowH - .6f, w - m, y + rowH, fill(line))
                y += rowH
            }
            index = end
            if (pg == pages && rows.isNotEmpty()) {
                y += 8f
                val tot = RectF(m, y, w - m, y + 30f)
                cv.drawRoundRect(tot, 9f, 9f, fill(paper2))
                cv.text(if (balance >= 0) "الرصيد النهائي المستحق" else "الرصيد النهائي المقدّم", colX(1) + 8f, y + 8f, cols[1] + cols[0], 11.5f, body, semi)
                cv.text(num(abs(balance)), colX(4) + 6f, y + 7f, cols[4] + cols[3] - 12f, 13f, if (balance > 0) red else green, semi, center = true)
            }

            // التذييل
            cv.rule(m, w - m, footTop)
            cv.mark(w - m - 26f, footTop + 16f, 26f)
            cv.text("${BrandConfig.NAME} · ${BrandConfig.TAGLINE}", m + 200f, footTop + 18f, w - 2 * m - 236f, 10.5f, body, semi)
            cv.text("صفحة ⁦$pg / $pages⁩", m + 200f, footTop + 34f, w - 2 * m - 236f, 9.5f, mute)
            if (pg == pages) {
                cv.drawBitmap(qr("QAYD|${shop.id}|${c.id}|${money(balance)}|$no", 140), null, RectF(m, footTop + 12f, m + 52f, footTop + 64f), null)
                cv.text("امسح للتحقق من الكشف", m + 58f, footTop + 26f, 130f, 9.5f, mute)
            }
            out.end()
        }
        return out.file("كشف-${c.name.take(24)}-$no.pdf")
    }

    /* ---------- الإيصال ---------- */

    /** إيصال حراري 80mm لحركة واحدة. */
    fun receipt(shop: Shop, t: Tx, customer: Customer?, balanceAfter: Double?): File {
        val w = 226f
        val h = 360f
        val m = 14f
        val out = Out(w, h)
        val cv = out.begin()
        cv.drawRect(0f, 0f, w, h, fill(white))
        cv.drawRect(0f, 0f, w, 84f, Paint().apply { shader = LinearGradient(w, 0f, 0f, 84f, ink2, ink, Shader.TileMode.CLAMP) })
        cv.rule(0f, w, 84f)
        cv.mark(w / 2 - 17f, 10f, 34f)
        cv.text(shop.name, m, 48f, w - 2 * m, 14f, white, display, center = true)
        var y = 100f
        val pill = "إيصال ${label(t)}"
        val tp = TextPaint().apply { textSize = 10f; typeface = semi }
        val pw = tp.measureText(pill) + 24f
        cv.drawRoundRect(RectF(w / 2 - pw / 2, y, w / 2 + pw / 2, y + 20f), 10f, 10f, fill(paper2))
        cv.text(pill, m, y + 4f, w - 2 * m, 10f, body, semi, center = true)
        y += 32f
        val box = RectF(m, y, w - m, y + 58f)
        cv.drawRoundRect(box, 12f, 12f, fill(paper))
        cv.drawRoundRect(box, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = gold })
        cv.text("المبلغ", m, y + 9f, w - 2 * m, 9.5f, mute, center = true)
        cv.text(num(t.amount), m, y + 24f, w - 2 * m, 19f, if (t.kind == TxKind.Payment) green else ink, semi, center = true)
        y += 72f
        fun row(k: String, v: String) {
            val vw = (w - 2 * m) * .5f
            cv.text(k, m + vw + 6f, y, w - 2 * m - vw - 6f, 10f, mute)
            cv.text(v, m, y, vw, 10.5f, body, semi, end = true)
            y += 18f
            cv.drawRect(m, y - 5f, w - m, y - 4.5f, fill(line))
        }
        if (customer != null) row("الزبون", customer.name)
        if (balanceAfter != null) row(if (balanceAfter >= 0) "الرصيد المستحق" else "رصيد مقدّم", num(abs(balanceAfter)))
        row("التاريخ", "⁦${stamp.format(Date(t.at))}⁩")
        y += 6f
        cv.drawBitmap(qr("QAYD|${shop.id}|${t.id}|${money(t.amount)}", 140), null, RectF(w / 2 - 30f, y, w / 2 + 30f, y + 60f), null)
        y += 68f
        cv.rule(m, w - m, y)
        cv.text("شكراً لتعاملكم · ${BrandConfig.TAGLINE}", m, y + 10f, w - 2 * m, 9.5f, mute, center = true)
        out.end()
        return out.file("ايصال-${customer?.name?.take(20) ?: label(t)}-${SimpleDateFormat("yyMMdd-HHmm", Locale.US).format(Date(t.at))}.pdf")
    }

    /** يحفظ الملف في مجلد فواتير قيد ثم يفتح المشاركة. */
    fun share(f: File) {
        QaydFiles.save(ctx, f, QaydFiles.INVOICES, "application/pdf")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, f.nameWithoutExtension).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
