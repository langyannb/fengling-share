package com.fengling.share.data

import org.json.JSONObject

/** 软件分类 */
data class Category(
    val id: Int,
    val name: String,
    val color: String,
    val sortOrder: Int,
) {
    companion object {
        fun fromJson(json: JSONObject): Category = Category(
            id = json.optInt("id", 0),
            name = json.optString("name", ""),
            color = json.optString("color", "#4C6FFF"),
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
    val panLinks: List<PanLink> = emptyList(),
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
            panLinks = json.optJSONArray("pan_links")
                ?.let { arr ->
                    (0 until arr.length()).map { PanLink.fromJson(arr.getJSONObject(it)) }
                } ?: emptyList(),
        )
    }
}
