package com.fengling.share

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fengling.share.data.Settings
import com.fengling.share.data.ThemeMode
import com.fengling.share.ui.components.ProvideNavigationEventDispatcher
import com.fengling.share.ui.main.MainScreen
import com.fengling.share.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            // 观察 themeMode + themeColor, 任一变化都触发重组换肤
            var themeMode by remember { mutableStateOf(Settings.getThemeMode()) }
            var themeColor by remember { mutableStateOf(Settings.getThemeColor()) }

            AppTheme(
                themeMode = themeMode,
                seedColor = themeColor.seed,
            ) {
                ProvideNavigationEventDispatcher {
                    MainScreen(
                        onThemeChanged = { newMode ->
                            themeMode = newMode
                            themeColor = Settings.getThemeColor()
                        },
                    )
                }
            }
        }
    }
}
