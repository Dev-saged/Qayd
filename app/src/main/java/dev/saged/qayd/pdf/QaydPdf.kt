package dev.saged.qayd.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dev.saged.qayd.R
import dev.saged.qayd.config.BrandConfig
import dev.saged.qayd.data.Customer
import dev.saged.qayd.data.Shop
import dev.saged.qayd.data.Tx
import dev.saged.qayd.data.TxKind
import dev.saged.qayd.ui.util.money
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** كشف الحساب A4 والإيصال الحراري 80mm بخط قيد، مع رمز QR للتحقق، ومشاركة مباشرة. */
class QaydPdf(private val ctx: Context) {
    private val regular: Typeface = ResourcesCompat.getFont(ctx, R.font.readex_regular) ?: Typeface.DEFAULT
    private val bold: Typeface = ResourcesCompat.getFont(ctx, R.font.readex_semibold) ?: Typeface.DEFAULT_BOLD
    private val navy = 0xFF0E1322.toInt()
    private val navy2 = 0xFF1A2236.toInt()
    private val gold = 0xFFD9A846.toInt()
    private val ink = 0xFF1E2230.toInt()
    private val mute = 0xFF6A7080.toInt()
    private val line = 0xFFE6E2D8.toInt()
    private val zebra = 0xFFF8F6F1.toInt()
    private val red = 0xFFB3261E.toInt()
    private val green = 0xFF1B7A4A.toInt()
    private val date = SimpleDateFormat("d/M/yyyy", Locale.US)
    private val stamp = SimpleDateFormat("d/M/yyyy HH:mm", Locale.US)

    private fun Canvas.text(
        s: String, x: Float, y: Float, w: Float, size: Float, color: Int,
        heavy: Boolean = false, left: Boolean = false, center: Boolean = false, lines: Int = 1,
    ): Float {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = if (heavy) bold else regular }
        val l = StaticLayout.Builder.obtain(s, 0, s.length, tp, w.toInt().coerceAtLeast(1))
            .setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else if (left) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build()
        save(); translate(x, y); l.draw(this); restore()
        return l.height.toFloat()
    }

    private fun num(v: Double) = "⁦${money(v)} ${BrandConfig.CURRENCY}⁩"

    private fun qr(data: String, size: Int): Bitmap {
        val m = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 0))
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until size) for (y in 0 until size) setPixel(x, y, if (m[x, y]) navy else 0xFFFFFFFF.toInt())
        }
    }

    /** كشف حساب زبون: الأقدم أولاً مع الرصيد بعد كل حركة. */
    fun statement(shop: Shop, c: Customer, history: List<Tx>): File {
        val rows = history.sortedBy { it.at }
        var run = 0.0
        val running = rows.map { t -> run += if (t.kind == TxKind.Debt) t.amount else -t.amount; run }
        val debts = rows.filter { it.kind == TxKind.Debt }.sumOf { it.amount }
        val pays = rows.filter { it.kind == TxKind.Payment }.sumOf { it.amount }
        val balance = debts - pays
        val no = SimpleDateFormat("yyMMdd-HHmm", Locale.US).format(Date())
        val doc = PdfDocument()
        val w = 595f
        val h = 842f
        val m = 36f
        val first = 15
        val per = 26
        val pages = if (rows.size <= first) 1 else 1 + (rows.size - first + per - 1) / per
        var index = 0
        for (pg in 1..pages) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(w.toInt(), h.toInt(), pg).create())
            val cv = page.canvas
            val band = if (pg == 1) 120f else 70f
            cv.drawRect(0f, 0f, w, band, Paint().apply { shader = LinearGradient(0f, 0f, w, band, navy2, navy, Shader.TileMode.CLAMP) })
            cv.drawRect(0f, band - 3f, w, band, Paint().apply { color = gold })
            cv.text(shop.name, m, if (pg == 1) 26f else 16f, w - 2 * m - 160f, if (pg == 1) 24f else 17f, 0xFFFFFFFF.toInt(), heavy = true)
            cv.text(if (pg == 1) "كشف حساب" else "كشف حساب — تابع", m, if (pg == 1) 62f else 42f, w - 2 * m - 160f, 13f, gold)
            cv.text("رقم $no\n${stamp.format(Date())}", m, if (pg == 1) 30f else 18f, 160f, 10.5f, 0xFFDDDDDD.toInt(), left = true, lines = 2)
            var y = band + 18f
            if (pg == 1) {
                cv.text("الزبون", m, y, 300f, 10.5f, mute)
                cv.text(c.name, m, y + 14f, w - 2 * m - 160f, 18f, ink, heavy = true)
                if (c.phone.isNotBlank()) cv.text("⁦${c.phone}⁩", m, y + 18f, 160f, 12f, mute, left = true)
                y += 52f
                val bw = (w - 2 * m - 20f) / 3
                listOf(
                    Triple("مجموع الديون", debts, red), Triple("مجموع الدفعات", pays, green),
                    Triple(if (balance >= 0) "الرصيد المستحق" else "رصيد مقدم للزبون", kotlin.math.abs(balance), if (balance > 0) red else green),
                ).forEachIndexed { i, (label, v, col) ->
                    val x = w - m - (i + 1) * bw - i * 10f
                    val r = RectF(x, y, x + bw, y + 60f)
                    cv.drawRoundRect(r, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (i == 2) navy else zebra })
                    cv.text(label, x + 12f, y + 10f, bw - 24f, 10.5f, if (i == 2) gold else mute)
                    cv.text(num(v), x + 12f, y + 28f, bw - 24f, 16f, if (i == 2) 0xFFFFFFFF.toInt() else col, heavy = true)
                }
                y += 80f
            }
            val cols = floatArrayOf(90f, 0f, 85f, 85f, 95f)
            cols[1] = w - 2 * m - cols[0] - cols[2] - cols[3] - cols[4]
            fun colX(i: Int) = w - m - cols.take(i + 1).sum()
            cv.drawRoundRect(RectF(m, y, w - m, y + 24f), 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = navy })
            listOf("التاريخ", "البيان", "دين", "دفعة", "الرصيد").forEachIndexed { i, s -> cv.text(s, colX(i) + 6f, y + 5f, cols[i] - 12f, 10.5f, gold, heavy = true) }
            y += 24f
            val count = if (pg == 1) first else per
            val end = (index + count).coerceAtMost(rows.size)
            if (rows.isEmpty()) cv.text("لا حركات بعد.", m, y + 14f, w - 2 * m, 12f, mute, center = true)
            for (r in index until end) {
                val t = rows[r]
                if ((r - index) % 2 == 1) cv.drawRect(m, y, w - m, y + 22f, Paint().apply { color = zebra })
                val debt = t.kind == TxKind.Debt
                val label = when (t.source) {
                    "power" -> "فاتورة كهرباء"
                    "ps" -> t.ref ?: "جلسة شاشة"
                    else -> t.kind.label
                } + if (t.byName.isNotBlank()) " · ${t.byName}" else ""
                cv.text("⁦${date.format(Date(t.at))}⁩", colX(0) + 6f, y + 5f, cols[0] - 12f, 10f, mute)
                cv.text(label, colX(1) + 6f, y + 5f, cols[1] - 12f, 10.5f, ink)
                cv.text(if (debt) num(t.amount) else "", colX(2) + 6f, y + 5f, cols[2] - 12f, 10.5f, red, heavy = true)
                cv.text(if (!debt) num(t.amount) else "", colX(3) + 6f, y + 5f, cols[3] - 12f, 10.5f, green, heavy = true)
                cv.text(num(running[r]), colX(4) + 6f, y + 5f, cols[4] - 12f, 10.5f, ink)
                cv.drawLine(m, y + 22f, w - m, y + 22f, Paint().apply { color = line })
                y += 22f
            }
            index = end
            cv.drawLine(m, h - 58f, w - m, h - 58f, Paint().apply { color = line; strokeWidth = 1.2f })
            cv.text("${BrandConfig.NAME} — ${BrandConfig.TAGLINE}", m, h - 46f, 300f, 10f, mute)
            cv.text("صفحة $pg من $pages", m + 60f, h - 46f, 200f, 10f, mute, left = true)
            if (pg == pages) {
                val q = qr("QAYD|${shop.id}|${c.id}|${money(balance)}|$no", 120)
                cv.drawBitmap(q, null, RectF(m, h - 132f, m + 64f, h - 68f), null)
                cv.text("رمز التحقق من الكشف", m + 70f, h - 110f, 200f, 9.5f, mute, left = false)
            }
            doc.finishPage(page)
        }
        return save(doc, "kashf-${c.name.take(20)}-$no.pdf")
    }

    /** إيصال حراري 80mm لحركة واحدة. */
    fun receipt(shop: Shop, t: Tx, customer: Customer?, balanceAfter: Double?): File {
        val w = 226f
        val h = 330f
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(w.toInt(), h.toInt(), 1).create())
        val cv = page.canvas
        val m = 12f
        var y = 14f
        y += cv.text(shop.name, m, y, w - 2 * m, 16f, ink, heavy = true, center = true) + 4f
        y += cv.text("إيصال ${t.kind.label}", m, y, w - 2 * m, 11f, mute, center = true) + 10f
        cv.drawLine(m, y, w - m, y, Paint().apply { color = ink; pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 3f), 0f) })
        y += 10f
        fun row(k: String, v: String, big: Boolean = false) {
            cv.text(k, m, y, (w - 2 * m) / 2, 10.5f, mute)
            y += cv.text(v, m, y, w - 2 * m, if (big) 15f else 11f, ink, heavy = big, left = true) + 7f
        }
        if (customer != null) row("الزبون", customer.name)
        row("المبلغ", num(t.amount), big = true)
        if (balanceAfter != null) row(if (balanceAfter >= 0) "الرصيد المستحق" else "رصيد مقدم", num(kotlin.math.abs(balanceAfter)))
        row("التاريخ", "⁦${stamp.format(Date(t.at))}⁩")
        if (t.byName.isNotBlank()) row("بواسطة", t.byName)
        y += 4f
        cv.drawBitmap(qr("QAYD|${shop.id}|${t.id}|${money(t.amount)}", 120), null, RectF(w / 2 - 34f, y, w / 2 + 34f, y + 68f), null)
        y += 76f
        cv.text("شكراً لتعاملكم — ${BrandConfig.NAME}", m, y, w - 2 * m, 10f, mute, center = true)
        doc.finishPage(page)
        return save(doc, "receipt-${t.id.take(8)}.pdf")
    }

    private fun save(doc: PdfDocument, name: String): File {
        val dir = File(ctx.cacheDir, "pdf").apply { mkdirs() }
        val f = File(dir, name.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }

    fun share(f: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, f.nameWithoutExtension).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
