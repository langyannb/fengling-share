package com.fengling.share.ui.components

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * 「拍摄」用: 把系统相机返回的缩略图压成 JPEG 字节 (契约第 6 条)。
 *
 * 走 `ActivityResultContracts.TakePicturePreview()` → Bitmap, 不落文件、不用 FileProvider,
 * 压缩完直接交给既有的 `ApiClient.uploadChatImage(bytes, name, mime)` 上传, 和相册那条链路完全一致。
 */
fun bitmapToJpeg(bitmap: Bitmap, quality: Int = 88): ByteArray {
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}
