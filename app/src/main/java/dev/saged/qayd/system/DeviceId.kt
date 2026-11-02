package dev.saged.qayd.system

import android.content.Context
import android.os.Build
import java.util.UUID

/** معرّف ثابت لهذا الجهاز داخل التطبيق، لتحديد جوال الإرسال المعتمد. */
object DeviceId {
    fun get(c: Context): String {
        val p = c.getSharedPreferences("qayd_device", Context.MODE_PRIVATE)
        return p.getString("id", null) ?: UUID.randomUUID().toString().also { p.edit().putString("id", it).apply() }
    }

    val name: String get() = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
}
