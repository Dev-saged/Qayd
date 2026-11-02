package dev.saged.qayd.system

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import dev.saged.qayd.R
import dev.saged.qayd.config.BrandConfig
import dev.saged.qayd.data.Order
import dev.saged.qayd.ui.util.money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * طابعة حرارية بلوتوث (ESC/POS). النص العربي يُرسم صورة ثم يُرسل نقطياً،
 * فيطبع صحيحاً على أي طابعة حتى لو لم تدعم الحروف العربية.
 */
object BtPrinter {
    private const val PREFS = "qayd_printer"
    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    data class Device(val name: String, val address: String)

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun saved(c: Context): Device? = prefs(c).getString("address", null)?.let { Device(prefs(c).getString("name", "").orEmpty(), it) }
    fun save(c: Context, d: Device) = prefs(c).edit().putString("address", d.address).putString("name", d.name).apply()
    fun width(c: Context) = prefs(c).getInt("width", 384)
    fun setWidth(c: Context, px: Int) = prefs(c).edit().putInt("width", px).apply()

    @SuppressLint("MissingPermission")
    fun paired(c: Context): List<Device> = runCatching {
        (c.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bondedDevices.orEmpty()
            .map { Device(it.name ?: it.address, it.address) }.sortedBy { it.name }
    }.getOrDefault(emptyList())

    /** إيصال الزبون أو تذكرة المطبخ كصورة بعرض الورق. */
    fun ticket(c: Context, shop: String, o: Order, kitchen: Boolean, width: Int = width(c)): Bitmap {
        val regular = ResourcesCompat.getFont(c, R.font.readex_regular)
        val bold = ResourcesCompat.getFont(c, R.font.readex_bold)
        val s = width / 384f
        val blocks = mutableListOf<Triple<String, Float, Pair<Boolean, Layout.Alignment>>>()
        fun add(t: String, size: Float, heavy: Boolean = false, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) = blocks.add(Triple(t, size * s, heavy to align))
        val center = Layout.Alignment.ALIGN_CENTER
        if (!kitchen) add(shop, 34f, true, center)
        add(if (kitchen) "مطبخ · طلب #${o.no}" else "طلب #${o.no}", if (kitchen) 40f else 28f, true, center)
        add("${o.type.label}${if (o.table.isNotBlank()) " · طاولة ${o.table}" else ""} · ⁦${SimpleDateFormat("HH:mm  d/M", Locale.US).format(Date(o.at))}⁩", 22f, align = center)
        add("—".repeat(18), 20f, align = center)
        o.lines.forEach { l ->
            if (kitchen) add("${l.qty} × ${l.name}", 32f, true)
            else add("${l.qty} × ${l.name}   ⁦${money(l.total)}⁩", 25f)
        }
        if (o.note.isNotBlank()) add("ملاحظة: ${o.note}", 24f, true)
        add("—".repeat(18), 20f, align = center)
        if (!kitchen) {
            add("المجموع  ⁦${money(o.total)} ${BrandConfig.CURRENCY}⁩", 34f, true, center)
            if (o.address.isNotBlank()) add("التوصيل: ${o.address}${if (o.phone.isNotBlank()) " · ⁦${o.phone}⁩" else ""}", 22f)
            add("شكراً لكم · ${BrandConfig.TAGLINE}", 20f, align = center)
        }
        val pad = (12 * s).toInt()
        val layouts = blocks.map { (t, size, st) ->
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; color = Color.BLACK; typeface = if (st.first) bold else regular }
            StaticLayout.Builder.obtain(t, 0, t.length, tp, width - 2 * pad).setAlignment(st.second).setTextDirection(TextDirectionHeuristics.RTL).build()
        }
        val h = layouts.sumOf { it.height + (6 * s).toInt() } + pad * 2 + (60 * s).toInt()
        return Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888).apply {
            val cv = Canvas(this)
            cv.drawColor(Color.WHITE)
            var y = pad.toFloat()
            layouts.forEach { l -> cv.save(); cv.translate(pad.toFloat(), y); l.draw(cv); cv.restore(); y += l.height + 6 * s }
        }
    }

    /** يحوّل الصورة إلى أمر GS v 0 النقطي. */
    private fun raster(b: Bitmap): ByteArray {
        val w = (b.width + 7) / 8
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf(0x1D, 0x76, 0x30, 0, (w and 0xFF).toByte(), (w shr 8).toByte(), (b.height and 0xFF).toByte(), (b.height shr 8).toByte()))
        val px = IntArray(b.width)
        for (y in 0 until b.height) {
            b.getPixels(px, 0, b.width, 0, y, b.width, 1)
            for (x in 0 until w) {
                var byte = 0
                for (bit in 0 until 8) {
                    val i = x * 8 + bit
                    if (i < b.width) {
                        val c = px[i]
                        val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                        if (lum < 140) byte = byte or (0x80 shr bit)
                    }
                }
                out.write(byte)
            }
        }
        return out.toByteArray()
    }

    @SuppressLint("MissingPermission")
    suspend fun print(c: Context, d: Device, pages: List<Bitmap>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val adapter = (c.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: error("البلوتوث غير متوفر")
            adapter.cancelDiscovery()
            adapter.getRemoteDevice(d.address).createRfcommSocketToServiceRecord(SPP).use { sock ->
                sock.connect()
                val out = sock.outputStream
                pages.forEach { b ->
                    out.write(byteArrayOf(0x1B, 0x40))
                    out.write(raster(b))
                    out.write(byteArrayOf(0x0A, 0x0A, 0x0A, 0x1D, 0x56, 0x42, 0x00))
                }
                out.flush()
                Thread.sleep(400)
            }
        }
    }
}
