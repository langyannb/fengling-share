package com.fengling.share.data

/**
 * 服务器地址隐藏配置
 *
 * 为什么加密: 防止有人从 APK 反编译/strings 直接扒出服务器 IP 和端口去搞服务器。
 * APK 里不存任何明文字符串, 只有 XOR 加密后的字节数组, 运行时才还原。
 *
 * ⚠️ 局限说明: 这只挡"静态分析" (反编译/搜字符串)。
 * 抓包 (HTTP 明文流量) / Hook 解密函数 依然能拿到地址,
 * 所以服务器本身也要做防护 (防火墙/限流/验证), 不能只靠客户端藏地址。
 *
 * 改地址流程: 用任意脚本对目标字符串做 每字节 XOR 0x5A + 反转, 替换下面两个数组。
 */
object ServerConfig {

    private const val KEY = 0x5A

    /** 解密: 反转 + 逐字节 XOR */
    private fun decode(data: IntArray): String {
        val bytes = ByteArray(data.size)
        for (i in data.indices) {
            bytes[i] = (data[data.size - 1 - i] xor KEY).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }

    /** 主 API 地址 (XOR 加密, 运行时还原) */
    val BASE_URL: String by lazy { decode(intArrayOf(
        42, 50, 42, 116, 51, 42, 59, 117, 111, 110, 98, 99, 96, 105, 104, 107, 116, 105, 104, 116, 110, 98, 107, 116, 107, 108, 117, 117, 96, 42, 46, 46, 50
    )) }

    /** 崩溃上报地址 (XOR 加密, 运行时还原) */
    val CRASH_URL: String by lazy { decode(intArrayOf(
        46, 40, 53, 42, 63, 40, 5, 50, 41, 59, 40, 57, 103, 52, 53, 51, 46, 57, 59, 101, 42, 50, 42, 116, 51, 42, 59, 117, 111, 110, 98, 99, 96, 105, 104, 107, 116, 105, 104, 116, 110, 98, 107, 116, 107, 108, 117, 117, 96, 42, 46, 46, 50
    )) }
}
