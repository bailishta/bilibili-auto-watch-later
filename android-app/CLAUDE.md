# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

B站「稍后观看助手」Android 客户端（Jetpack Compose + Room + OkHttp）。从同仓库根目录的 Chrome 扩展迁移而来，**只保留手动检查功能，无自动定时检查**。根目录的 Chrome 扩展支持定时排程，详见 `../CLAUDE.md`。

## 构建与运行

```bash
# Windows 下编译 APK
./gradlew assembleDebug

# 安装到已连接的设备/模拟器
./gradlew installDebug
```

APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

要求 JDK 17+，Android SDK 35，Gradle 通过 wrapper 自动下载。

## 架构

```
BiliApp (Application)       ← 手动 DI：实例化 DB / Api / Repository
└── MainActivity            ← 单 Activity，Compose 全屏，根据登录状态切换 Login / Home

data/
├── local/
│   ├── entity/             ← Room 数据表：CreatorEntity, TrackedVideoEntity
│   ├── dao/                ← Room DAO：CreatorDao, TrackedVideoDao（Flow + 挂起函数）
│   └── AppDatabase         ← Room 数据库单例，fallbackToDestructiveMigration()
├── remote/
│   ├── CookieProvider      ← SharedPreferences 存储 SESSDATA / bili_jct / DedeUserID
│   ├── WbiSigner           ← B站 WBI 签名单例（imgKey + subKey → mixinKey → MD5）
│   └── BiliApiService      ← OkHttp 请求封装：UP主信息 / 视频列表 / 添加稍后观看 / 观看历史 / 关注列表
└── repository/
    ├── CreatorRepository   ← 追踪名单 CRUD
    ├── SettingsRepository  ← SharedPreferences 读写：窗口时间、统计、导入标记
    └── CheckRepository     ← 核心业务：扫描新视频、批量添加、导入关注列表

ui/
├── login/                  ← WebView 内嵌 B站 登录页面，提取 Cookie 后保存
├── home/                   ← 主界面：统计卡片、立即检查按钮、新视频窗口下拉、追踪名单列表
└── components/
    └── AddCreatorDialog    ← 添加 UP主 弹窗（支持 UID 或主页链接）

util/
└── Md5                     ← java.security.MessageDigest MD5 实现
```

## 关键实现细节

### 手动 DI（BiliApp.kt）

Application 子类 `BiliApp` 作为依赖容器。通过 `BiliApp.instance` 获取全局实例。所有依赖在 `onCreate()` 中按顺序构建：

1. `AppDatabase` → DAO
2. `CookieProvider`（SharedPreferences）
3. `BiliApiService`（需要 CookieProvider）
4. `CheckRepository`（需要 Api + 两个 DAO + Settings）

### WBI 签名（WbiSigner）

1. 从 `/x/web-interface/nav` 获取 `wbi_img.img_url` + `sub_url`
2. 提取文件名部分作为 `imgKey` / `subKey`
3. 用 `MIXIN_KEY_ENC_TAB`（64 元素固定数组）重排 key，取前 32 位
4. 参数统一用 `encWbi()` 编码（过滤 `!'()*` → `URLEncoder` → 大写十六进制 → `+` 替换为 `%20`）
5. 排序后的 query string 拼接 mixinKey 做 MD5 得到 `w_rid`
6. 最终返回原始参数 + `wts` + `w_rid`

### 双层 UP主 信息查询

`BiliApiService.getUpInfo()` 先调用无需 WBI 签名的 `x/web-interface/card` API，失败则回退到 WBI 签名的 `x/space/wbi/acc/info`，都失败则返回占位名 `UP主_{mid}`。

### Cookie 认证

登录时通过 WebView 打开 B站首页，用户登录后自动提取 `SESSDATA`、`bili_jct`、`DedeUserID`。`bili_jct` 作为添加稍后观看的 CSRF token。所有 API 请求通过 `baseHeaders` 携带完整 Cookie 字符串 + Chrome User-Agent + Referer。

### 检查流程（CheckRepository）

1. 读取追踪名单，计算时间窗口（`newVideoWindowHours`，默认 24 小时）
2. 获取观看历史（分页拉取，最多 200 条）用于跳过已看视频
3. 以 `CONCURRENCY=6` 的并发度分批处理，批次内 `BATCH_STAGGER=30ms` 错开启动
4. 每批：并行拉取视频列表 → 合并待添加 aid → 批量调用 `addToWatchLaterBatch`（每次间隔 200ms）
5. 成功后写入 `tracked_videos` 表，超出 5000 条上限时清理最旧记录
6. `cancel()` 设置中断标志，checker 在批次间检查

### 消息数据类

B站 API 返回数据类均定义在 `BiliApiService.kt` 文件底部：`UpInfo`、`BiliVideo`、`UserVideosResult`、`BatchResult`、`FollowUser`、`FollowListResult`。Check 相关的 `CheckReport`、`NewVideo`、`ImportResult` 定义在 `CheckRepository.kt`。

### 状态管理

`HomeViewModel` 使用 `MutableStateFlow<HomeUiState>` 驱动 UI，追踪名单通过 `CreatorDao.getAll()` Flow 实时更新。UI 通过 `collectAsState()` 订阅。
