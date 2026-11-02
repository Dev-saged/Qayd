package dev.saged.qayd.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.runtime.mutableStateOf
import dev.saged.qayd.MainActivity
import dev.saged.qayd.R
import dev.saged.qayd.config.BrandConfig
import dev.saged.qayd.ui.util.money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** ويدجت الشاشة الرئيسية: أرقام اليوم واختصارات البيع والصوت. */
class QaydWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) = render(c, m, ids)

    companion object {
        private const val PREFS = "qayd_widget"
        /** طلب فتح من الويدجت: sale أو voice أو customers. */
        val launch = mutableStateOf<String?>(null)

        fun update(c: Context, shop: String, sales: Double, debts: Double) {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("shop", shop).putFloat("sales", sales.toFloat()).putFloat("debts", debts.toFloat()).putLong("at", System.currentTimeMillis()).apply()
            val m = AppWidgetManager.getInstance(c)
            render(c, m, m.getAppWidgetIds(ComponentName(c, QaydWidget::class.java)))
        }

        private fun open(c: Context, action: String, code: Int) = PendingIntent.getActivity(
            c, code, Intent(c, MainActivity::class.java).putExtra("launch", action).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun render(c: Context, m: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val v = RemoteViews(c.packageName, R.layout.widget_qayd).apply {
                setTextViewText(R.id.w_shop, p.getString("shop", null) ?: BrandConfig.NAME)
                setTextViewText(R.id.w_sales, "${money(p.getFloat("sales", 0f).toDouble())} ${BrandConfig.CURRENCY}")
                setTextViewText(R.id.w_debts, "${money(p.getFloat("debts", 0f).toDouble())} ${BrandConfig.CURRENCY}")
                setTextViewText(R.id.w_time, p.getLong("at", 0L).takeIf { it > 0 }?.let { SimpleDateFormat("HH:mm", Locale.US).format(Date(it)) } ?: "")
                setOnClickPendingIntent(R.id.w_sale, open(c, "sale", 1))
                setOnClickPendingIntent(R.id.w_voice, open(c, "voice", 2))
                setOnClickPendingIntent(R.id.w_open, open(c, "customers", 3))
                setOnClickPendingIntent(R.id.w_root, open(c, "home", 4))
            }
            m.updateAppWidget(ids, v)
        }
    }
}
