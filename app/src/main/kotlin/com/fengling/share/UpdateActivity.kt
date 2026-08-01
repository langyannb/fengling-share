package com.fengling.share

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fengling.share.data.VersionInfo
import com.fengling.share.ui.update.UpdateScreen

/**
 * UpdateActivity - 独立更新页 (强制更新/从设置进入)
 * 直接下载 APK + 安装 (OShin 同款)
 */
class UpdateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val info = VersionInfo(
            version = intent.getStringExtra("version") ?: "",
            url = intent.getStringExtra("url") ?: "",
            updateLog = intent.getStringExtra("log") ?: "",
            updateMode = intent.getStringExtra("mode") ?: "internal",
            sizeMb = intent.getFloatExtra("size", 0f),
            releaseDate = intent.getStringExtra("date") ?: "",
        )
        enableEdgeToEdge()
        setContent {
            UpdateScreen(info = info, onBack = { finish() })
        }
    }
}
