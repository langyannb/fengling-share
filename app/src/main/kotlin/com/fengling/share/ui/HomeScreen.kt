package com.fengling.share.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fengling.share.data.ApiClient
import com.fengling.share.data.AppItem
import com.fengling.share.data.Category
import kotlinx.coroutines.launch

/** 首页: 分类标签 + 软件列表 (常驻层, 状态保留) */
@Composable
fun HomeScreen(
    onAppClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var selectedCategory by remember { mutableIntStateOf(0) }
    var keyword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    fun loadApps(catId: Int, kw: String) {
        scope.launch {
            loading = true
            error = ""
            try {
                apps = ApiClient.getApps(catId, kw)
            } catch (e: Exception) {
                error = e.message ?: "加载失败"
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        try {
            categories = ApiClient.getCategories()
        } catch (_: Exception) { }
        loadApps(0, "")
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "风铃分享库",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${apps.size} 款软件",
                fontSize = 12.sp,
                color = Color(0xFF8A8FA8),
            )
        }

        // 分类标签 (横向滚动)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            item {
                CategoryChip(
                    name = "全部",
                    selected = selectedCategory == 0,
                    onClick = {
                        selectedCategory = 0
                        loadApps(0, keyword)
                    },
                )
            }
            items(categories) { cat ->
                CategoryChip(
                    name = cat.name,
                    selected = selectedCategory == cat.id,
                    onClick = {
                        selectedCategory = cat.id
                        loadApps(cat.id, keyword)
                    },
                )
            }
        }

        // 搜索框
        androidx.compose.foundation.text.BasicTextField(
            value = keyword,
            onValueChange = {
                keyword = it
                loadApps(selectedCategory, it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF0F1F6))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Color(0xFF1A1A2E)),
            decorationBox = { inner ->
                if (keyword.isEmpty()) {
                    Text("搜索软件...", fontSize = 14.sp, color = Color(0xFF8A8FA8))
                }
                inner()
            },
        )

        // 列表区
        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF4C6FFF), strokeWidth = 2.dp)
                }
            }
            error.isNotEmpty() && apps.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error, color = Color(0xFFF5455C), fontSize = 14.sp)
                }
            }
            apps.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无软件", color = Color(0xFF8A8FA8), fontSize = 14.sp)
                }
            }
            else -> {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(apps, key = { it.id }) { app ->
                        AppCard(app = app, onClick = { onAppClick(app.id) })
                    }
                }
            }
        }
    }
}

/** 分类 chip */
@Composable
private fun CategoryChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color(0xFF4C6FFF) else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(
            text = name,
            fontSize = 14.sp,
            color = if (selected) Color.White else Color(0xFF8A8FA8),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** 软件卡片 */
@Composable
private fun AppCard(app: AppItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 图标
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE8ECFF)),
            contentAlignment = Alignment.Center,
        ) {
            if (app.icon.isNotEmpty()) {
                AsyncImage(
                    model = app.icon,
                    contentDescription = app.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = app.name.take(1),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4C6FFF),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        // 信息
        Column(Modifier.weight(1f)) {
            Text(
                text = app.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1A1A2E),
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (app.categoryName.isNotEmpty()) {
                    Text(
                        text = app.categoryName,
                        fontSize = 11.sp,
                        color = Color(0xFF4C6FFF),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x144C6FFF))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                if (app.version.isNotEmpty()) {
                    Text(
                        text = "v${app.version}",
                        fontSize = 12.sp,
                        color = Color(0xFF8A8FA8),
                    )
                }
            }
        }
        // 下载量
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatCount(app.downloadCount),
                fontSize = 12.sp,
                color = Color(0xFF8A8FA8),
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF4C6FFF))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text("下载", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** 下载量格式化 */
fun formatCount(count: Int): String {
    return when {
        count >= 10000 -> String.format("%.1fw", count / 10000.0)
        count >= 1000 -> String.format("%.1fk", count / 1000.0)
        else -> count.toString()
    }
}
