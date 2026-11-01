package dev.saged.qayd.system

import android.content.Context
import dev.saged.qayd.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** فقرة من ملاحظات إصدار: عنوان القسم وبنوده (المستوى 0 أو 1). */
data class NoteSection(val title: String, val items: List<Pair<Int, String>>)
data class Release(val version: String, val sections: List<NoteSection>)

/** حالة التحديث على مستوى التطبيق: الفحص، التنزيل في الخلفية، والتثبيت. */
object Updates {
    val latest = MutableStateFlow<AppUpdate?>(null)
    /** نسبة التنزيل، أو null إن لم يكن هناك تنزيل. */
    val progress = MutableStateFlow<Float?>(null)
    val failed = MutableStateFlow(false)
    /** عرض نافذة "تحديث جديد" مرة واحدة لكل تشغيل. */
    val prompted = MutableStateFlow(false)
    val pageOpen = MutableStateFlow(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun check() = scope.launch { latest.value = Updater.check() }

    fun start(ctx: Context) {
        if (progress.value != null) return
        val app = ctx.applicationContext
        progress.value = 0f
        failed.value = false
        scope.launch {
            runCatching {
                val apk = Updater.download(app) { progress.value = it }
                kotlinx.coroutines.withContext(Dispatchers.IO) { Updater.install(app, apk) }
            }.onFailure { failed.value = true }
            progress.value = null
        }
    }

    /** ملاحظات الإصدارات المضمّنة في التطبيق، الأحدث أولاً. */
    fun history(ctx: Context): List<Release> = runCatching {
        ctx.assets.list("releases").orEmpty().filter { it.endsWith(".md") }
            .map { f -> parse(f.removePrefix("v").removeSuffix(".md"), ctx.assets.open("releases/$f").bufferedReader().use { it.readText() }) }
            .sortedWith(compareByDescending<Release> { v -> v.version.split('.').map { it.toIntOrNull() ?: 0 }.let { it.getOrElse(0) { 0 } * 1_000_000 + it.getOrElse(1) { 0 } * 1000 + it.getOrElse(2) { 0 } } })
    }.getOrDefault(emptyList())

    fun parse(version: String, md: String): Release {
        val out = mutableListOf<NoteSection>()
        var title: String? = null
        var items = mutableListOf<Pair<Int, String>>()
        fun flush() { title?.let { t -> if (items.isNotEmpty()) out += NoteSection(t, items) }; items = mutableListOf() }
        md.lines().forEach { raw ->
            val l = raw.trimEnd()
            when {
                l.startsWith("## ") -> { flush(); title = l.removePrefix("## ").trim() }
                l.trimStart().startsWith("- ") -> {
                    val text = l.trimStart().removePrefix("- ").replace("**", "").trim()
                    if (text != "لا شيء.") items += (if (l.startsWith("  ")) 1 else 0) to text
                }
            }
        }
        flush()
        return Release(version, out)
    }

    val current: String get() = BuildConfig.VERSION_NAME.substringBefore('-')
}
