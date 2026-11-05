package com.fengling.share.ui.main.my

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CallToAction
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fengling.share.data.Settings
import com.fengling.share.data.ThemeColor
import com.fengling.share.data.ThemeMode
import com.fengling.share.ui.components.BlurredBar
import com.fengling.share.ui.components.rememberBlurBackdrop
import com.fengling.share.ui.theme.LocalEnableBlur
import com.fengling.share.ui.theme.LocalFloatingBottomBar
import com.fengling.share.ui.theme.LocalGlassBottomBar
import com.fengling.share.ui.theme.colorScheme.PaletteStyle
import com.fengling.share.ui.theme.colorScheme.SpecVersionOption
import com.fengling.share.ui.theme.colorScheme.tonalScheme
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * ThemeSettingsScreen - 主题设置 (v1.1.18)
 *
 * 视觉方向: 一屏一张「迷你手机」预览卡, 下面三组玻璃卡片设置项;
 * 顶栏走真实 backdrop 模糊 (滚动时内容从毛玻璃下穿过), 与「我的」页其它入口拉开层次。
 *
 * 布局顺序 (照 KernelSU-Style-UIKit 的主题页):
 * 预览卡 → 明暗三选 → 主题色/色板风格/色彩规范 → 模糊/悬浮底栏/玻璃 → 预测返回。
 *
 * 所有改动即时生效并落盘 (Settings), 通过 onChanged 通知外层重建 AppTheme。
 */
@Composable
fun ThemeSettingsScreen(
    onBack: () -> Unit,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val scrollBehavior = MiuixScrollBehavior()
    var themeMode by remember { mutableStateOf(Settings.getThemeMode()) }
    var themeColor by remember { mutableStateOf(Settings.getThemeColor()) }
    var paletteStyle by remember { mutableStateOf(PaletteStyle.fromName(Settings.paletteStyle)) }
    var specVersion by remember { mutableStateOf(SpecVersionOption.fromName(Settings.colorSpec)) }
    var blurOn by remember { mutableStateOf(Settings.enableBlur) }
    var floatingBar by remember { mutableStateOf(Settings.floatingBottomBar) }
    var glassBar by remember { mutableStateOf(Settings.glassBottomBar) }
    var predictiveOn by remember { mutableStateOf(Settings.predictiveBack) }

    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val seed = if (themeColor == ThemeColor.CUSTOM) Settings.customColor else themeColor.seed

    // 顶栏在模糊生效时透明 (让毛玻璃透出来), 否则用不透明 surface 兜底
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        // 预测性返回交给 NavHost: navigation-compose 用 SeekableTransitionState 让本页
        // 跟随手势位移 (appScreen 的 popExit = slideOutHorizontally)。这里**不能**再叠
        // 一层 predictiveBackTransform —— 两套位移同时生效就是用户看到的「两层预测返回」。
        modifier = modifier,
        topBar = {
            BlurredBar(backdrop = backdrop, blurActive = blurActive) {
                TopAppBar(
                    title = "主题设置",
                    color = barColor,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        // 左右让给内容自己控制 (横屏/挖孔), 上下由 innerPadding 给
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        // 内容层挂 layerBackdrop: 顶栏的 textureBlur 才能采到这一层画了什么
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                // ============ 预览卡 ============
                item {
                    Spacer(Modifier.height(20.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ThemePreviewCard(
                            seed = seed,
                            isDark = isDark,
                            style = paletteStyle,
                            spec = specVersion,
                            floatingBar = floatingBar,
                            glassBar = glassBar,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }

                // ============ 明暗模式 ============
                item {
                    TabRow(
                        tabs = listOf(ThemeMode.SYSTEM.label, ThemeMode.LIGHT.label, ThemeMode.DARK.label),
                        selectedTabIndex = ThemeMode.entries.indexOf(themeMode).coerceIn(0, 2),
                        onTabSelected = { index ->
                            themeMode = ThemeMode.entries[index]
                            Settings.themeMode = themeMode.value
                            onChanged()
                        },
                    )
                }

                // ============ 配色 ============
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        OverlayDropdownPreference(
                            title = "主题色",
                            summary = themeColor.label,
                            items = ThemeColor.presets.map { it.label },
                            selectedIndex = ThemeColor.presets.indexOf(themeColor).coerceAtLeast(0),
                            startAction = {
                                Icon(
                                    imageVector = Icons.Filled.Colorize,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onBackgroundVariant,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            },
                            onSelectedIndexChange = { index ->
                                themeColor = ThemeColor.presets[index]
                                Settings.themeColor = themeColor.value
                                onChanged()
                            },
                        )
                        OverlayDropdownPreference(
                            title = "色板风格",
                            summary = paletteStyle.label,
                            items = PaletteStyle.entries.map { it.label },
                            selectedIndex = PaletteStyle.entries.indexOf(paletteStyle),
                            startAction = {
                                Icon(
                                    imageVector = Icons.Filled.Style,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onBackgroundVariant,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            },
                            onSelectedIndexChange = { index ->
                                paletteStyle = PaletteStyle.entries[index]
                                Settings.paletteStyle = paletteStyle.name
                                onChanged()
                            },
                        )
                        OverlayDropdownPreference(
                            title = "色彩规范",
                            summary = specVersion.label,
                            items = SpecVersionOption.entries.map { it.label },
                            selectedIndex = SpecVersionOption.entries.indexOf(specVersion),
                            startAction = {
                                Icon(
                                    imageVector = Icons.Filled.DesignServices,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onBackgroundVariant,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            },
                            onSelectedIndexChange = { index ->
                                specVersion = SpecVersionOption.entries[index]
                                Settings.colorSpec = specVersion.name
                                onChanged()
                            },
                        )
                    }
                }

                // ============ 界面效果 ============
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        SwitchPreference(
                            title = "界面模糊",
                            summary = "顶栏与悬浮底栏使用真实背景模糊（需 Android 12+）",
                            checked = blurOn,
                            startAction = {
                                Icon(
                                    imageVector = Icons.Filled.BlurOn,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onBackgroundVariant,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            },
                            onCheckedChange = { checked ->
                                blurOn = checked
                                Settings.enableBlur = checked
                                onChanged()
                            },
                        )
                        SwitchPreference(
                            title = "悬浮底栏",
                            summary = "底部导航栏改为悬浮胶囊样式",
                            checked = floatingBar,
                            startAction = {
                                Icon(
                                    imageVector = Icons.Filled.CallToAction,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onBackgroundVariant,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            },
                            onCheckedChange = { checked ->
                                floatingBar = checked
                                Settings.floatingBottomBar = checked
                                onChanged()
                            },
                        )
                        AnimatedVisibility(
                            visible = floatingBar,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            SwitchPreference(
                                title = "玻璃质感",
                                summary = "底栏带折射与高光，关掉更省电",
                                checked = glassBar,
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Filled.WaterDrop,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onBackgroundVariant,
                                        modifier = Modifier.padding(end = 6.dp),
                                    )
                                },
                                onCheckedChange = { checked ->
                                    glassBar = checked
                                    Settings.glassBottomBar = checked
                                    onChanged()
                                },
                            )
                        }
                    }
                }

                // ============ 交互 ============
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        SwitchPreference(
                            title = "预测性返回",
                            summary = "侧滑时页面跟随手指位移并缩小（需 Android 13+ 手势导航）",
                            checked = predictiveOn,
                            startAction = {
                                Icon(
                                    imageVector = Icons.Filled.Gesture,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onBackgroundVariant,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            },
                            onCheckedChange = { checked ->
                                predictiveOn = checked
                                Settings.predictiveBack = checked
                                onChanged()
                            },
                        )
                    }
                }

                // ============ 底部留白 (导航栏 + 呼吸) ============
                item {
                    Spacer(
                        Modifier.height(
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * 主题预览卡 - 一张按手机比例缩小的「迷你界面」, 实时反映当前配色。
 *
 * 用 tonal 色板逐个取色, 所以换主题色 / 风格 / 规范 / 明暗都会立刻反映在这里。
 */
@Composable
private fun ThemePreviewCard(
    seed: Long,
    isDark: Boolean,
    style: PaletteStyle,
    spec: SpecVersionOption,
    floatingBar: Boolean,
    glassBar: Boolean,
) {
    val scheme = remember(seed, isDark, style, spec) {
        tonalScheme(seed = seed, darkTheme = isDark, style = style, spec = spec.spec)
    }
    val bg = Color(scheme.background)
    val text = Color(scheme.onBackground)
    val card = Color(scheme.surfaceContainerHigh)
    val navBar = Color(scheme.surfaceContainer)
    val accent = Color(scheme.primary)
    val accentSoft = Color(scheme.primaryContainer)
    val secondarySoft = Color(scheme.secondaryContainer)
    val tertiarySoft = Color(scheme.tertiaryContainer)
    val outline = Color(scheme.outline)

    // 预览卡宽高按 9:19.5 的手机比例, 固定宽度让它在页面里居中
    val cardWidth = 132.dp
    val cardHeight = cardWidth / 9f * 19.5f

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, outline.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 顶栏
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "风铃分享库",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 内容区: 三个色块示意列表卡片
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                PreviewBlock(accentSoft, Modifier.fillMaxWidth().height(30.dp))
                PreviewBlock(secondarySoft, Modifier.fillMaxWidth().height(22.dp))
                PreviewBlock(card, Modifier.fillMaxWidth().height(22.dp))
                PreviewBlock(tertiarySoft, Modifier.fillMaxWidth().height(14.dp))
            }
            // 底栏
            if (floatingBar) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(navBar.copy(alpha = if (glassBar) 0.55f else 1f))
                            .border(
                                width = 0.5.dp,
                                color = text.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(11.dp),
                            ),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(4) { i ->
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (i == 0) accent else text.copy(alpha = 0.35f)),
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .background(navBar),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(4) { i ->
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (i == 0) accent else text.copy(alpha = 0.35f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewBlock(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(color))
}
