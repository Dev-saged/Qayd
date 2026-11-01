package dev.saged.qayd.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** أيقونات قيد: خطوط 24×24 مرسومة يدوياً بنفس هوية النموذج أ. */
object QIcons {
    private fun icon(name: String, vararg paths: Pair<String, Float>) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { (d, w) ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = w,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private const val W = 1.8f

    val Home = icon("home", "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z" to W)
    val Users = icon(
        "users",
        "M12.2 8a3.2 3.2 0 1 1-6.4 0a3.2 3.2 0 1 1 6.4 0" to W,
        "M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6M16 5.2a3 3 0 0 1 0 5.6M18 14.4c1.8.8 3 2.6 3 5.6" to W,
    )
    val Pad = icon(
        "pad",
        "M7 8h10a4 4 0 0 1 4 4v1.5a3 3 0 0 1-5.2 2L14.5 14h-5l-1.3 1.5A3 3 0 0 1 3 13.5V12a4 4 0 0 1 4-4zM8 10.5v3M6.5 12h3" to W,
        "M16 11.5h.01M18 13h.01" to 2.6f,
    )
    val Bolt = icon("bolt", "M13 3L5 13.5h6L10 21l8-10.5h-6z" to W)
    val Msg = icon("msg", "M4 5h16a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H9l-5 4V6a1 1 0 0 1 1-1zM8 10h8M8 13h5" to W)
    val More = icon("more", "M6 6h.01M12 6h.01M18 6h.01M6 12h.01M12 12h.01M18 12h.01M6 18h.01M12 18h.01M18 18h.01" to 3f)
    val Plus = icon("plus", "M12 5v14M5 12h14" to 2.2f)
    val Bell = icon("bell", "M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15zM10 21h4" to W)
    val Eye = icon(
        "eye",
        "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z" to W,
        "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0" to W,
    )
    val EyeOff = icon(
        "eyeOff",
        "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z" to W,
        "M4 4l16 16" to W,
    )
    val Signal = icon("signal", "M5 12.5a10 10 0 0 1 14 0M8 15.5a6 6 0 0 1 8 0" to W, "M12 19h.01" to 2.6f)
    val Up = icon("up", "M7 17L17 7M9 7h8v8" to W)
    val Down = icon("down", "M7 7l10 10M17 9v8H9" to W)
    val Clock = icon("clock", "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0" to W, "M12 7v5l3 2" to W)
    val Logout = icon("logout", "M15 4h4a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-4M10 17l-5-5 5-5M5 12h11" to W)
    val Shop = icon("shop", "M4 9l1.5-5h13L20 9M4 9v10a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1V9M4 9h16M9 20v-6h6v6" to W)
    val UserPlus = icon(
        "userPlus",
        "M12.2 8a3.2 3.2 0 1 1-6.4 0a3.2 3.2 0 1 1 6.4 0" to W,
        "M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6M18 8v6M15 11h6" to W,
    )
    val Edit = icon("edit", "M4 20h4L19 9l-4-4L4 16zM13.5 6.5l4 4" to W)
    val Backspace = icon("backspace", "M21 6H9l-6 6 6 6h12a1 1 0 0 0 1-1V7a1 1 0 0 0-1-1zM11.5 9.5l5 5M16.5 9.5l-5 5" to W)
    val Cart = icon("cart", "M3 4h2l2.2 10.2a1 1 0 0 0 1 .8h9.3a1 1 0 0 0 1-.8L20 8H6.2" to W, "M10 19.5h.01M17 19.5h.01" to 3f)
    val Wallet = icon("wallet", "M4 7a2 2 0 0 1 2-2h12v4M4 7v11a2 2 0 0 0 2 2h14V9H6a2 2 0 0 1-2-2zM16 14.5h.01" to W)
    val Receipt = icon("receipt", "M6 3h12v18l-3-2-3 2-3-2-3 2zM9 8h6M9 12h6M9 16h3" to W)
    val Back = icon("back", "M9 5l7 7-7 7" to W)
    val Phone = icon("phone", "M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a1 1 0 0 1-1 1A16 16 0 0 1 4 5a1 1 0 0 1 1-1z" to W)
    val Search = icon("search", "M17 11a6 6 0 1 1-12 0a6 6 0 1 1 12 0M20 20l-4.3-4.3" to W)
    val Trash = icon("trash", "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3" to W)
    val Calendar = icon("calendar", "M4 6a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1zM4 10h16M8 3v4M16 3v4" to W)
    val Lock = icon("lock", "M6 11h12a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1zM8 11V8a4 4 0 0 1 8 0v3" to W)
    val Chart = icon("chart", "M4 20V10M10 20V4M16 20v-7M22 20H2" to W)
    val Shield = icon("shield", "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6zM9 12l2 2 4-4" to W)
    val Sync = icon("sync", "M20 11a8 8 0 0 0-14.3-4.9L4 8M4 4v4h4M4 13a8 8 0 0 0 14.3 4.9L20 16M20 20v-4h-4" to W)
}
