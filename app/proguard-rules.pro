# 风铃分享库 R8 规则
# Compose 库自带 consumer rules, 无需额外配置

# Miuix (液态玻璃/组件库) - 保留全部, 防反射/渲染问题
-keep class top.yukonga.miuix.** { *; }

# 数据模型 (org.json 手动解析, 无反射, 但保留字段名保险)
-keep class com.fengling.share.data.** { *; }

# WebView/下载安装
-keep class com.fengling.share.ApkDownloader { *; }
