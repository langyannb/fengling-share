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
 * @param hasApp      是否检测到可处理该链接的应用 (仅供参考, 不作为「能否打开」的判据)
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
private val SCHEME_APP_NAMES = mapOf(
    "uclink" to "UC浏览器", "uc" to "UC浏览器", "ucbrowser" to "UC浏览器",
    "weixin" to "微信", "wechat" to "微信",
    "mqqwpa" to "QQ", "mqq" to "QQ", "mqqapi" to "QQ", "mqqopensdkapi" to "QQ",
    "alipays" to "支付宝", "alipay" to "支付宝",
    "taobao" to "淘宝", "tmall" to "天猫",
    "bilibili" to "哔哩哔哩", "bilikiko" to "哔哩哔哩",
    "baiduboxapp" to "百度", "baidumap" to "百度地图",
    "qqmusic" to "QQ音乐", "kugou" to "酷狗音乐", "kwplayer" to "酷我音乐",
    "orpheuswidget" to "网易云音乐", "neteasemusic" to "网易云音乐",
    "tencentvideo" to "腾讯视频", "iqiyi" to "爱奇艺", "youku" to "优酷",
    "snssdk1128" to "抖音", "snssdk143" to "抖音", "douyin" to "抖音",
    "xhsdiscover" to "小红书", "xhs" to "小红书",
    "thunder" to "迅雷", "magnet" to "磁力链接", "ed2k" to "电驴",
    "mailto" to "电子邮件", "tel" to "电话", "sms" to "短信", "geo" to "地图",
)

fun resolveExternalJump(context: Context, url: String): ExternalJumpTarget? {
    val link = url.trim()
    if (link.isEmpty()) return null
    val uri = runCatching { Uri.parse(link) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    // http(s) 继续用内置浏览器, 不拦截
    if (scheme == "http" || scheme == "https") return null

    val intent = (if (scheme == "intent") {
        runCatching { Intent.parseUri(link, Intent.URI_INTENT_SCHEME) }.getOrNull()
    } else {
        runCatching { Intent(Intent.ACTION_VIEW, uri) }.getOrNull()
    }) ?: return null
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    val pm = context.packageManager
    // Android 11+ 有包可见性限制: resolveActivity 对没在 <queries> 里声明的 scheme 会返回 null,
    // 但这并不代表真的没有应用能打开它 —— 所以这里只用它「尽量取一个应用名/图标」,
    // 绝不用它来判定「无法打开」(否则就会出现明明装了 UC 却提示打不开的情况)。
    val resolved = runCatching { pm.resolveActivity(intent, PackageManager.MATCH_ALL) }.getOrNull()
        ?: runCatching { pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).firstOrNull() }
            .getOrNull()
    val pkg = intent.`package` ?: resolved?.activityInfo?.packageName
    val label = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg!!, 0)).toString()
    }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: SCHEME_APP_NAMES[scheme]
        ?: "外部应用"
    val host = uri.host?.takeIf { it.isNotBlank() }
        ?: if (scheme == "intent") "intent" else scheme

    return ExternalJumpTarget(
        url = link,
        appLabel = label,
        intent = intent,
        host = host,
        packageName = pkg,
        hasApp = resolved != null || intent.`package` != null || SCHEME_APP_NAMES.containsKey(scheme),
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
                    text = "要打开外部应用吗？",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (target.hasApp) {
                        "将离开风铃分享，跳转到「${target.appLabel}」"
                    } else {
                        "系统未检测到已安装的对应应用，仍可尝试打开"
                    },
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
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(primary, primary.copy(alpha = 0.82f))
                                )
                            )
                            .clickable(onClick = onConfirm)
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "打开",
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
