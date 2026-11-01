package dev.saged.qayd

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import dev.saged.qayd.system.ThemePref
import dev.saged.qayd.ui.AppRoot
import dev.saged.qayd.ui.theme.QaydTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ThemePref.load(this)
        setContent {
            val dark = when (ThemePref.mode.value) {
                ThemePref.Mode.System -> isSystemInDarkTheme()
                ThemePref.Mode.Light -> false
                ThemePref.Mode.Dark -> true
            }
            LaunchedEffect(dark) {
                val bar = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(bar, bar)
            }
            QaydTheme(dark) { AppRoot() }
        }
    }
}
