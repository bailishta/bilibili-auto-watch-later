# 稍后观看助手 Android

Android 2.0.0 适配浏览器插件的管理功能，使用 Jetpack Compose 原生界面。要求 Android 8.0 及以上。

[下载正式 APK](https://github.com/bailishta/bilibili-auto-watch-later/releases/download/android-v2.0.0/bilibili-watch-later-android-v2.0.0.apk) · [Release 与更新记录](https://github.com/bailishta/bilibili-auto-watch-later/releases/tag/android-v2.0.0) · [完整更新日志](../CHANGELOG.md)

## 手机界面

- **首页**：追踪人数、本次新发现、立即检查、跳转 B站安卓 APP 的稍后再看、累计添加与上次检查。未安装可用的哔哩哔哩 APP 时显示提示。
- **名单**：头像、名称、UID、粉丝数、最后视频更新时间；支持搜索、增删、粉丝数／更新时间排序，以及 JSON 名单导入导出。
- **更新**：按自然周查看每天更新人数，切换本周和历史周。进入某天默认上午，可切换下午；无指针 12 小时表盘，下方直接显示具体分钟、头像及名字。
- **设置**：通过带下拉箭头的选择框调整手动检查时间范围、更新记录保留周数、跟随系统／浅色／深色、配置备份、重新导入关注、日志及退出登录。

APP 只在点击「立即检查」时扫描新视频，没有自动检查、检查器或后台排程。检查期间可切换页面、查看名单和更新记录；等待检查结束后再修改名单或导入配置。

打开「稍后再看」时，依次尝试独立页面、B站专用链接接收入口及新旧链接。只有系统报告启动失败才会继续尝试；请求被接收后，无法在本 APP 内确认 B站是否显示了目标页。入口全部失败时尝试打开 B站主界面，并提示手动进入。设置中的「运行日志」会记录 B站版本、尝试入口和启动异常，便于真机反馈。

## 数据与备份

- 手动检查同时采集粉丝数、最后视频发布时间及最近视频记录，保存到本机 Room 数据库；排序和图表不再请求网络，去掉独立的刷新数据按钮。
- 更新记录默认保留最近 3 个自然周，含本周，可设 1–52 周。按设备时区、周一到周日统计；周、日、半天、每分钟分别按 UID 去重，每位 UP 主最多 500 条。
- 记录来自接口返回的近期视频，非完整投稿历史。缩短保留周数会清理范围外记录，延长后逐步积累。
- 名单导入是合并操作，已有 UID 会跳过。配置导入先校验并预览，再替换名单、设置和更新记录，保留本机已处理的视频记录及统计，不包含登录 Cookie。
- Android 可读取插件格式版本 1 的配置备份，只恢复支持的名单及设置，不应用浏览器检查器。Android 导出的配置含空检查器列表，不要用它覆盖需要保留排程的浏览器配置；仅交换名单时使用名单导入／导出。
- Room 显式迁移 `1 → 2 → 3`，新增更新记录表，不清空旧用户的名单。旧更新频率排序迁移为最后视频更新时间排序。

## 编译

要求 JDK 17、Android SDK 35。配置本机 `local.properties` 后，在本目录运行：

```powershell
.\gradlew.bat --no-daemon assembleDebug
```

安装测试包输出为 `app/build/outputs/apk/debug/app-debug.apk`。已有完整 Gradle 缓存时可增加 `--offline`。

## 正式构建与签名

```powershell
.\gradlew.bat --no-daemon assembleRelease
```

Release 启用 R8 代码压缩，输出 `app/build/outputs/apk/release/app-release-unsigned.apk`，需要使用本机私钥签名后才能安装。使用 Android SDK 的 `apksigner`，通过环境变量指定签名文件和密码：

```powershell
& "$env:ANDROID_HOME/build-tools/35.0.0/apksigner.bat" sign `
  --ks "$env:BILI_SIGNING_STORE" --ks-key-alias "$env:BILI_SIGNING_ALIAS" `
  --ks-pass env:BILI_SIGNING_STORE_PASSWORD --key-pass env:BILI_SIGNING_KEY_PASSWORD `
  --out app/build/outputs/apk/release/bilibili-watch-later-android-v2.0.0.apk `
  app/build/outputs/apk/release/app-release-unsigned.apk
```

本次发布沿用已在此电脑使用的测试签名证书，便于同签名覆盖升级；构建类型为 Release，非 Debug。后续版本需要沿用同一私钥。妥善备份本机签名文件，不提交签名密钥、密码或 `local.properties` 到 GitHub。

发布使用标签 `android-v2.0.0`，附件只包含已签名 APK 和 SHA-256 文件；插件使用独立标签 `v2.0.0`。详细发布说明在 [Android 发布记录](../docs/releases/android-v2.0.0.md)。

## 安装后重点查看

使用现有安装项覆盖升级，查看旧名单及登录是否保留。主要流程包括：检查范围下拉选择与保存、跳转 B站 APP 的稍后再看（含未安装时的提示）、手动检查及取消、缓存排序、周／日及上午／下午切换、备份预览取消与恢复、名单导入导出、深色模式和系统字号变化。所有验证使用自己的设备完成。
