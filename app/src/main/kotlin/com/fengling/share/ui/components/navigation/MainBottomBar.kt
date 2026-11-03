// 结构照抄 KernelSU-Style-UI-Kit 的 ui/component/bottombar/BottomBarMiuix.kt (Apache 2.0)：
// 关掉悬浮底栏 → BlurredBar + NavigationBar；打开 → FloatingBottomBar（液体玻璃胶囊）。
// 差异只有：tab 数据来自本仓库的首页/分类/群组/关于，点击回调由 MainScreen 注入（保留未登录拦截）。

package com.fengling.share.ui.components.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengling.share.ui.components.BlurredBar
import com.fengling.share.ui.theme.LocalEnableBlur
import com.fengling.share.ui.theme.LocalFloatingBottomBar
import com.fengling.share.ui.theme.LocalGlassBottomBar
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 底部导航（两种形态，跟参考项目一致）：
 * - [LocalFloatingBottomBar] = false → 系统样式 `NavigationBar`，背景用 [BlurredBar] 做真实模糊
 * - [LocalFloatingBottomBar] = true  → `FloatingBottomBar` 液体玻璃悬浮胶囊（可拖动滑块切页）
 *
 * @param blurBackdrop 模糊采样源（顶栏/底栏共用）；为 null 表示设备不支持或用户关了模糊
 * @param backdrop 内容捕获源，悬浮胶囊用它做透镜折射
 * @param selectedIndex 当前页下标
 * @param onSelect 点/拖到某页
 */
@Composable
fun MainBottomBar(
    items: List<NavigationItem>,
    selectedIndex: () -> Int,
    onSelect: (Int) -> Unit,
    blurBackdrop: LayerBackdrop?,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val floating = LocalFloatingBottomBar.current
    val glass = LocalGlassBottomBar.current && LocalEnableBlur.current

    if (!floating) {
        BlurredBar(blurBackdrop) {
            NavigationBar(
                modifier = modifier,
                color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
            ) {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = item.icon,
                        label = item.label,
                        selected = selectedIndex() == index,
                        onClick = { onSelect(index) },
                    )
                }
            }
        }
    } else {
        FloatingBottomBar(
            modifier = modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(
                    bottom = 12.dp +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
            selectedIndex = selectedIndex,
            onSelected = onSelect,
            backdrop = backdrop,
            tabsCount = items.size,
            isBlurEnabled = glass,
        ) {
            items.forEachIndexed { index, item ->
                FloatingBottomBarItem(
                    onClick = { onSelect(index) },
                    modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            }
        }
    }
}
