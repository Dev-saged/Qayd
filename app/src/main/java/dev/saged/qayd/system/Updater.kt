package dev.saged.qayd.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.app.PendingIntent
import android.content.pm.PackageInstaller
import dev.saged.qayd.BuildConfig
import dev.saged.qayd.config.BrandConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(val version: String, val build: Int, val code: Int, val notes: String)

/** التحديث الذاتي من Worker قيد: يقرأ آخر نسخة، ينزّلها، ويفتح شاشة التثبيت. */
object Updater {
    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("${BrandConfig.ADMIN_API}/v1/app/latest").openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            try {
                if (c.responseCode != 200) return@runCatching null
                val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                AppUpdate(j.getString("version"), j.optInt("build"), j.getInt("code"), j.optString("notes"))
                    .takeIf { it.code > BuildConfig.VERSION_CODE }
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }

    suspend fun download(ctx: Context, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val f = File(File(ctx.cacheDir, "apk").apply { mkdirs() }, "Qayd.apk")
        val c = URL("${BrandConfig.ADMIN_API}/v1/app/apk").openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        try {
            val total = c.contentLengthLong.coerceAtLeast(1)
            c.inputStream.use { input ->
                f.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        withContext(Dispatchers.Main) { progress((done.toFloat() / total).coerceIn(0f, 1f)) }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        f
    }

    fun install(ctx: Context, apk: File) {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        // التثبيت بجلسة PackageInstaller كما تفعل المتاجر، فلا يقيّد النظام صلاحيات التطبيق بعد التحديث
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            if (Build.VERSION.SDK_INT >= 33) setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            s.openWrite("qayd.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; s.fsync(out) }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            s.commit(PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallReceiver::class.java), flags).intentSender)
        }
    }
}
