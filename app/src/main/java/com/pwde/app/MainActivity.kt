package com.pwde.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pwde.app.data.prefs.ColorSchemeOption
import com.pwde.app.ui.common.pwdeViewModel
import com.pwde.app.ui.navigation.PwdeNavHost
import com.pwde.app.ui.theme.PwdeTheme
import com.pwde.app.ui.theme.ThemeViewModel
import com.pwde.app.ui.theme.colorsFor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Adaptive UI is app-wide: settings drive the theme here, at the root, for every screen.
            val themeViewModel = pwdeViewModel { ThemeViewModel(it.settingsRepository) }
            val settings by themeViewModel.settings.collectAsStateWithLifecycle()
            val current = settings
            if (current == null) {
                // Settings load in a few ms; hold the brand background instead of flashing defaults.
                Box(Modifier.fillMaxSize().background(colorsFor(ColorSchemeOption.DEFAULT).background))
            } else {
                val isDark = colorsFor(current.colorScheme).isDark
                LaunchedEffect(isDark) {
                    val style = if (isDark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                PwdeTheme(colorScheme = current.colorScheme, textSize = current.textSize, layoutMode = current.layoutMode) {
                    PwdeNavHost()
                }
            }
        }
    }
}
