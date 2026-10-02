package com.fengling.share.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 待确认的「跳出本 App」目标 (uclink:// weixin:// intent:// mailto: tel: 等)。
 *
 * @param url         原始链接
 * @param appLabel    能处理该链接的应用名 (解析不到时为「外部应用」)
 * @param intent      已补好 FLAG_ACTIVITY_NEW_TASK 的启动 Intent
 * @param host        展示用的标识 (域名或 scheme)
 * @param packageName 目标应用包名 (用于加载应用图标, 可能为 null)
 * @param hasApp      系统里是否真的存在可处理该链接的应用
 */
data class ExternalJumpTarget(
    val url: String,
    val appLabel: String,
    val intent: Intent,
    val host: String,
    val packageName: String?,
    val hasApp: Boolean,
)

/**
 * 判断一个链接是否需要跳出 App 并交给外部应用:
 * - `http` / `https` → 返回 null (继续用内置浏览器加载, 不拦截)
 * - 其它 scheme → 返回 [ExternalJumpTarget], 交由 UI 弹确认框, 用户点了才真正跳转
 */
fun resolveExternalJump(context: Context, url: String): ExternalJumpTarget? {
    val scheme = runCatching { Uri.parse(url).scheme?.lowercase() ?: "" }.getOrDefault("")
    if (scheme.isEmpty() || scheme == "http" || scheme == "https") return null

    val intent = (if (url.startsWith("intent://")) {
        runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull()
    } else {
        runCatching { Intent(Intent.ACTION_VIEW, Uri.parse(url)) }.getOrNull()
    }) ?: return ExternalJumpTarget(url, "外部应用", Intent(), scheme, null, false)

    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    val pm = context.packageManager
    val resolved = runCatching {
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }.getOrNull()

    val pkg = intent.`package` ?: resolved?.activityInfo?.packageName
    val label = if (pkg != null) {
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
            .getOrDefault("外部应用")
    } else {
        "外部应用"
    }
    val host = runCatching { intent.data?.host }.getOrNull()
        ?: if (url.startsWith("intent://")) "intent" else scheme

    return ExternalJumpTarget(
        url = url,
        appLabel = label,
        intent = intent,
        host = host,
        packageName = pkg,
        hasApp = resolved != null || intent.`package` != null,
    )
}

/**
 * 外部应用跳转确认框 (内置浏览器 / 公告弹窗共用, 视觉与公告弹窗一致)
 */
@Composable
fun ExternalJumpDialog(
    target: ExternalJumpTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val primary = MiuixTheme.colorScheme.primary

    // 目标应用图标 (加载不到就用首字母占位)
    val appIcon: Bitmap? = remember(target.packageName) {
        target.packageName?.let { pkg ->
            runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(144, 144) }
                .getOrNull()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(MiuixTheme.colorScheme.surface),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(primary, primary.copy(alpha = 0.72f))
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (target.hasApp) "即将打开外部应用" else "无法打开此链接",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (target.hasApp) "将离开风铃分享，跳转到其他应用" else "未检测到可处理该链接的应用",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp)),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = target.appLabel.take(1),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = primary,
                            )
                        }
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = target.appLabel,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = target.host,
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    if (target.hasApp) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MiuixTheme.colorScheme.surfaceVariant)
                                .clickable(onClick = onDismiss)
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "取消",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(primary, primary.copy(alpha = 0.82f))
                                )
                            )
                            .clickable(onClick = if (target.hasApp) onConfirm else onDismiss)
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (target.hasApp) "打开" else "知道了",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}
