package com.fengling.share.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 头像 (v1.1.23)。
 *
 * 群头像 / 私聊头像原来都是「一块 surfaceContainerHigh 底 + 一个主题色首字」,
 * 一整列看下去全是同一个灰块, 谁是谁只能靠字认。改成:
 *  · 有图就用图;
 *  · 没图时按名字哈希从一组渐变里取色 —— 同一个群永远同一个颜色, 列表里一眼能认;
 *  · [ring] 为 true 时外面套一圈主题色描边, 用来表示「有未读」。
 *
 * @param seed 取色种子, 一般传群名 / 昵称
 * @param shape 群用圆角方形, 私聊用圆形
 */
@Composable
fun GradientAvatar(
    imageUrl: String,
    fallbackText: String,
    size: Dp,
    modifier: Modifier = Modifier,
    seed: String = fallbackText,
    shape: Shape = RoundedCornerShape(size / 3.6f),
    ring: Boolean = false,
) {
    val brush = remember(seed) { avatarBrush(seed) }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(brush),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl.isNotBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = fallbackText.take(1).ifBlank { "友" },
                    fontSize = (size.value * 0.42f).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
        if (ring) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.5.dp, MiuixTheme.colorScheme.primary.copy(alpha = 0.85f), shape),
            )
        }
    }
}

/** 圆形头像 (私聊 / 成员列表用) */
@Composable
fun RoundAvatar(
    imageUrl: String,
    fallbackText: String,
    size: Dp,
    modifier: Modifier = Modifier,
    seed: String = fallbackText,
    ring: Boolean = false,
) = GradientAvatar(
    imageUrl = imageUrl,
    fallbackText = fallbackText,
    size = size,
    modifier = modifier,
    seed = seed,
    shape = CircleShape,
    ring = ring,
)

/** 名字 -> 渐变。同一串永远同一个色, 不随机, 列表刷新也不会跳色。 */
private val AVATAR_GRADIENTS = listOf(
    listOf(Color(0xFF6A8DFF), Color(0xFF9A6BFF)),
    listOf(Color(0xFF34BFA3), Color(0xFF2E93D6)),
    listOf(Color(0xFFFF9A6B), Color(0xFFE5484D)),
    listOf(Color(0xFFF0B44C), Color(0xFFE07A2B)),
    listOf(Color(0xFF9B7BFF), Color(0xFF5B7CFA)),
    listOf(Color(0xFF35C6C0), Color(0xFF2E8FD8)),
    listOf(Color(0xFFFF7FA8), Color(0xFFC7508E)),
    listOf(Color(0xFF5BC26B), Color(0xFF2F9C8F)),
)

private fun avatarBrush(seed: String): Brush {
    val idx = ((seed.hashCode() % AVATAR_GRADIENTS.size) + AVATAR_GRADIENTS.size) % AVATAR_GRADIENTS.size
    val colors = AVATAR_GRADIENTS[idx]
    return Brush.linearGradient(colors)
}
