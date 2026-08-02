package com.fengling.share.data

import org.json.JSONObject

/** 软件分类 */
data class Category(
    val id: Int,
    val name: String,
    val color: String,
    val icon: String, // 分类图标 URL (后端上传)
    val sortOrder: Int,
) {
    companion object {
        fun fromJson(json: JSONObject): Category = Category(
            id = json.optInt("id", 0),
            name = json.optString("name", ""),
            color = json.optString("color", "#4C6FFF"),
            icon = json.optString("icon", ""),
            sortOrder = json.optInt("sort_order", 0),
        )
    }
}

/** 网盘推广链接 */
data class PanLink(
    val id: Int,
    val panType: String,
    val label: String,
    val url: String,
    val password: String,
) {
    companion object {
        fun fromJson(json: JSONObject): PanLink = PanLink(
            id = json.optInt("id", 0),
            panType = json.optString("pan_type", "uc"),
            label = json.optString("label", ""),
            url = json.optString("url", ""),
            password = json.optString("password", ""),
        )
    }
}

/** 软件条目 */
data class AppItem(
    val id: Int,
    val categoryId: Int,
    val name: String,
    val icon: String,
    val version: String,
    val description: String,
    val packageName: String,
    val downloadCount: Int,
    val rating: Double,
    val categoryName: String,
    val screenshots: List<String> = emptyList(), // 介绍图片 (应用市场风格)
    val panLinks: List<PanLink> = emptyList(),
    val packId: Int? = null,
    val packItems: List<PackItem> = emptyList(),
    val parentPack: PackItem? = null,
    val isTop: Boolean = false,
    val isFeatured: Boolean = false,
) {
    companion object {
        fun fromJson(json: JSONObject): AppItem = AppItem(
            id = json.optInt("id", 0),
            categoryId = json.optInt("category_id", 0),
            name = json.optString("name", ""),
            icon = json.optString("icon", ""),
            version = json.optString("version", ""),
            description = json.optString("description", ""),
            packageName = json.optString("package_name", ""),
            downloadCount = json.optInt("download_count", 0),
            rating = json.optDouble("rating", 0.0),
            categoryName = json.optString("category_name", ""),
            screenshots = json.optJSONArray("screenshots")
                ?.let { arr -> (0 until arr.length()).map { arr.optString(it) } }
                ?: emptyList(),
            panLinks = json.optJSONArray("pan_links")
                ?.let { arr ->
                    (0 until arr.length()).map { PanLink.fromJson(arr.getJSONObject(it)) }
                } ?: emptyList(),
            packId = if (json.isNull("pack_id")) null else json.optInt("pack_id", 0).takeIf { it > 0 },
            packItems = json.optJSONArray("pack_items")
                ?.let { arr ->
                    (0 until arr.length()).map { PackItem.fromJson(arr.getJSONObject(it)) }
                } ?: emptyList(),
            parentPack = if (!json.isNull("parent_pack")) {
                json.optJSONObject("parent_pack")?.let { PackItem.fromJson(it) }
            } else null,
            isTop = json.optInt("is_top", 0) == 1,
            isFeatured = json.optInt("is_featured", 0) == 1,
        )
    }
}

/** 整合包子项 (轻量) */
data class PackItem(
    val id: Int,
    val name: String,
    val icon: String,
    val version: String,
    val downloadCount: Int,
) {
    companion object {
        fun fromJson(json: JSONObject): PackItem = PackItem(
            id = json.optInt("id", 0),
            name = json.optString("name", ""),
            icon = json.optString("icon", ""),
            version = json.optString("version", ""),
            downloadCount = json.optInt("download_count", 0),
        )
    }
}

/** 轮播图 */
data class Banner(
    val id: Int,
    val image: String,
    val title: String,
    val appId: Int?,
    val appName: String,
    val url: String = "",
) {
    companion object {
        fun fromJson(json: JSONObject): Banner = Banner(
            id = json.optInt("id", 0),
            image = json.optString("image", ""),
            title = json.optString("title", ""),
            appId = if (json.isNull("app_id")) null else json.optInt("app_id", 0).takeIf { it > 0 },
            appName = json.optString("app_name", ""),
            url = json.optString("url", ""),
        )
    }
}
