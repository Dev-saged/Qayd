package dev.saged.qayd.system

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.saged.qayd.data.Customer
import dev.saged.qayd.data.Ledger
import dev.saged.qayd.data.Reading
import dev.saged.qayd.data.Template
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** نسخة احتياطية JSON كاملة لبيانات المحل، تُحفظ أو تُرسل من شاشة المشاركة. */
object Backup {
    fun write(ctx: Context, shopId: String, ledger: Ledger, customers: List<Customer>, readings: List<Reading>, templates: List<Template>): File {
        val root = JSONObject()
            .put("app", "qayd").put("version", 1).put("shopId", shopId).put("exportedAt", System.currentTimeMillis())
            .put("customers", JSONArray(customers.map {
                JSONObject().put("id", it.id).put("name", it.name).put("phone", it.phone).put("createdAt", it.createdAt)
                    .put("power", it.power).put("meter", it.meter).put("initialReading", it.initialReading)
            }))
            .put("tx", JSONArray(ledger.allTx.map {
                JSONObject().put("id", it.id).put("kind", it.kind.key).put("amount", it.amount).put("at", it.at)
                    .put("customerId", it.customerId).put("category", it.category?.key).put("recurringId", it.recurringId)
                    .put("period", it.period).put("by", it.by).put("byName", it.byName).put("source", it.source).put("ref", it.ref)
            }))
            .put("recurring", JSONArray(ledger.recurring.map {
                JSONObject().put("id", it.item.id).put("title", it.item.title).put("category", it.item.category.key).put("day", it.item.day)
            }))
            .put("readings", JSONArray(readings.map {
                JSONObject().put("id", it.id).put("customerId", it.customerId).put("prev", it.prev).put("curr", it.curr)
                    .put("kwh", it.kwh).put("price", it.price).put("amount", it.amount).put("period", it.period).put("at", it.at)
            }))
            .put("templates", JSONArray(templates.map { JSONObject().put("id", it.id).put("title", it.title).put("body", it.body).put("kind", it.kind.name) }))
        val dir = File(ctx.cacheDir, "backup").apply { mkdirs() }
        val f = File(dir, "qayd-backup-${SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())}.json")
        f.writeText(root.toString(2))
        return f
    }

    fun share(ctx: Context, f: File) {
        QaydFiles.save(ctx, f, QaydFiles.BACKUPS, "application/json")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        ctx.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "نسخة قيد الاحتياطية",
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
