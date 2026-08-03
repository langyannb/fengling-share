package com.fengling.share.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.fengling.share.R

/**
 * BrandIcons - 关于页彩色品牌图标
 *  - QQ: 本地 APK 图标 (res/drawable/qq_icon.png, 用户提供的 QQ 官方图标)
 *  - GitHub: 官方彩色 octocat
 *  - 其他: 品牌色 Material 图标
 */

/** QQ 彩色企鹅图标 (本地资源, 打包进 APK) */
@Composable
fun QqBrandIcon(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter = painterResource(R.drawable.qq_icon),
        contentDescription = "QQ",
        modifier = modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp)),
    )
}

/** GitHub 官方彩色图标 (octocat) */
@Composable
fun GitHubBrandIcon(modifier: Modifier = Modifier) {
    coil.compose.AsyncImage(
        model = "https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png",
        contentDescription = "GitHub",
        modifier = modifier.size(24.dp),
    )
}

/** 品牌色: QQ 蓝 / GitHub 黑 / 官网蓝 / 反馈绿 / 捐赠红 */
object BrandColors {
    val QQ = Color(0xFF12B7F5)
    val GitHub = Color(0xFF24292F)
    val Website = Color(0xFF4C6FFF)
    val Feedback = Color(0xFF22B07D)
    val Donate = Color(0xFFFF4D6D)
}
