package dev.saged.qayd.system

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** مجلد قيد على الجهاز: التنزيلات/Qayd، وفيه النسخ الاحتياطية والفواتير. */
object QaydFiles {
    const val ROOT = "Qayd"
    const val BACKUPS = "Backups"
    const val INVOICES = "Invoices"

    fun label(sub: String) = "التنزيلات/$ROOT/$sub"

    /** ينسخ الملف إلى المجلد، ويحذف الأقدم من [keep] للملفات التي تبدأ بـ [prefix]. */
    fun save(c: Context, src: File, sub: String, mime: String, name: String = src.name, prefix: String? = null, keep: Int = 0): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            val r = c.contentResolver
            val rel = "${Environment.DIRECTORY_DOWNLOADS}/$ROOT/$sub/"
            val uri = r.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, rel)
                },
            ) ?: return@runCatching false
            r.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
            if (prefix != null && keep > 0) {
                r.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Downloads._ID),
                    "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?", arrayOf(rel, "$prefix%"),
                    "${MediaStore.Downloads.DATE_ADDED} DESC",
                )?.use { cur ->
                    var i = 0
                    while (cur.moveToNext()) if (i++ >= keep) r.delete(ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cur.getLong(0)), null, null)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$ROOT/$sub").apply { mkdirs() }
            src.copyTo(File(dir, name), overwrite = true)
            if (prefix != null && keep > 0) {
                dir.listFiles { f -> f.name.startsWith(prefix) }?.sortedByDescending { it.lastModified() }?.drop(keep)?.forEach { it.delete() }
            }
        }
        true
    }.getOrDefault(false)
}
