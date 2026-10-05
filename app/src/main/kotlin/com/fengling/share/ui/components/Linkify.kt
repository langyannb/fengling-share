package com.fengling.share.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit

/**
 * v1.1.12 链接识别 (群聊气泡 / 私聊气泡 / 用户简介 / 群公告 共用)。
 *
 * 认得的形态:
 *  - 带协议: https://x.com/a?b=1、http://1.2.3.4:8080/x
 *  - 不带协议: baidu.com、www.x.com、x.com/path?q=1、1.2.3.4:8080
 *
 * 不认:
 *  - 123.456 这种纯数字 (顶级域必须是 2~6 位字母)
 *  - a.png / 截图.jpg 这类文件名 (常见文件后缀不当链接)
 *
 * 链接的字符集: 遇到空白和中文标点就结束 (中文句子里 "见 x.com, 挺好" 只吃 x.com)。
 */
private const val LINK_TAIL = "[^\\s@，。；、！？：（）()【】\\[\\]\"'<>|]+"

val LINK_REGEX = Regex(
    // 1) 带协议
    "(?:https?://)$LINK_TAIL" +
        // 2) 裸 IP (可带端口与路径): 1.2.3.4:8080
        "|(?<![\\w.\\-])(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?(?:/$LINK_TAIL)?" +
        // 3) 裸域名: www.x.com / x.com / x.com/path?q=1 (顶级域 2~6 位字母)
        "|(?<![@\\w.\\-])(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\\.)+[A-Za-z]{2,6}(?::\\d{1,5})?(?:/$LINK_TAIL)?",
)

/** 常见文件后缀: 命中就不当链接 (避免把「截图.png」点开成网址) */
private val FILE_EXT = setOf(
    "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico", "heic", "avif",
    "mp3", "mp4", "mov", "avi", "mkv", "webm", "wav", "flac", "m4a", "aac",
    "zip", "rar", "7z", "tar", "gz", "apk", "aab", "jar", "so", "dex", "exe",
    "pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv",
    "json", "xml", "kt", "java", "py", "js", "ts", "css", "scss", "html", "htm",
    "md", "log", "ini", "conf", "bak", "tmp",
)

/** 链接字符集之外还要再剪掉句末标点: "看 x.com." 里那个句号不算链接的一部分 */
private const val TRIM_TAIL = ".,;:!?…、。，；：！？"

/** 一个识别出来的链接: range = 原文位置, text = 原文里的样子 (带不带协议都原样保留) */
data class LinkSpan(val range: IntRange, val text: String)

/**
 * 找出文本里所有链接 (已过掉文件名), 按出现顺序返回。
 * 纯函数, 调用方自己 remember 缓存即可。
 */
fun findLinks(text: String): List<LinkSpan> {
    if (text.isEmpty()) return emptyList()
    val out = ArrayList<LinkSpan>(2)
    LINK_REGEX.findAll(text).forEach { m ->
        var raw = m.value
        while (raw.isNotEmpty() && TRIM_TAIL.indexOf(raw.last()) >= 0) raw = raw.dropLast(1)
        if (raw.isEmpty()) return@forEach
        // 主机名 = 去掉协议 / 路径 / 端口之后剩下的那一段
        val host = raw.substringAfter("://").substringBefore('/').substringBefore(':').lowercase()
        if (!host.contains('.')) return@forEach
        if (host.substringAfterLast('.', "") in FILE_EXT) return@forEach
        out.add(LinkSpan(m.range.first until (m.range.first + raw.length), raw))
    }
    return out
}

/** 无协议链接要补上 https:// 再交给内置浏览器 (WebView 直接吃原串, 不补会白屏) */
fun normalizeUrl(url: String): String {
    val u = url.trim()
    return if (u.startsWith("http://", true) || u.startsWith("https://", true)) u else "https://$u"
}

/** 群聊/私聊里的 @昵称 (中文/字母/数字/下划线, 不含空白与 @) */
private val MENTION_REGEX = Regex("@[^\\s@]{1,20}")

/**
 * 把链接高亮 + 打上 "URL" 注解 (点击时可取出来打开); mentionColor 传 null 表示不高亮 @。
 * 每次调用都会新建 AnnotatedString —— 在 remember 里缓存。
 */
fun linkify(content: String, linkColor: Color, mentionColor: Color? = null): AnnotatedString =
    buildAnnotatedString {
        var last = 0
        findLinks(content).forEach { span ->
            if (span.range.first > last) {
                appendPlainWithMentions(content.substring(last, span.range.first), mentionColor)
            }
            pushStringAnnotation("URL", span.text)
            withStyle(
                SpanStyle(
                    color = linkColor,
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline,
                ),
            ) {
                append(span.text)
            }
            pop()
            last = span.range.last + 1
        }
        if (last < content.length) appendPlainWithMentions(content.substring(last), mentionColor)
    }

/** 纯文本片段: 只把 @昵称 高亮上去 (mentionColor 为 null 时原样追加) */
private fun AnnotatedString.Builder.appendPlainWithMentions(text: String, mentionColor: Color?) {
    if (mentionColor == null) {
        append(text)
        return
    }
    var last = 0
    MENTION_REGEX.findAll(text).forEach { m ->
        if (m.range.first > last) append(text.substring(last, m.range.first))
        withStyle(SpanStyle(color = mentionColor, fontWeight = FontWeight.Medium)) {
            append(m.value)
        }
        last = m.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}

/**
 * 可点击链接文本 (群公告 / 用户简介 / 任何只要「文字 + 链接可点」的地方共用)
 *
 * Coil 之外不能用 ClickableText (会和长按手势打架), 所以自己处理手势:
 * onTextLayout 拿到排版结果, 把点击坐标换成字符偏移, 再查 linkify 打好的 URL 注解。
 * onTap 收到 null 表示点在普通文字上, 调用方可以拿来做「展开/收起」。
 */
@Composable
fun LinkText(
    content: String,
    color: Color,
    linkColor: Color,
    fontSize: TextUnit,
    onTap: ((String?) -> Unit)? = null,
    maxLines: Int = Int.MAX_VALUE,
    mentionColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val shown = remember(content, mentionColor, linkColor) {
        linkify(content = content, linkColor = linkColor, mentionColor = mentionColor)
    }
    Text(
        text = shown,
        fontSize = fontSize,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { textLayout = it },
        modifier = modifier.pointerInput(shown.text, onTap) {
            if (onTap != null) {
                detectTapGestures(onTap = { pos ->
                    val lr = textLayout
                    val url = if (lr == null) {
                        null
                    } else {
                        val off = lr.getOffsetForPosition(pos).coerceIn(0, shown.length)
                        shown.getStringAnnotations("URL", off, off).firstOrNull()?.item
                    }
                    onTap(url)
                })
            }
        },
    )
}
