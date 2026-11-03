# 稍后观看助手 Android

Android 2.1.0 适配浏览器插件的管理功能，使用 Jetpack Compose 原生界面。要求 Android 8.0 及以上。

[下载正式 APK](https://github.com/bailishta/bilibili-auto-watch-later/releases/download/android-v2.1.0/bilibili-watch-later-android-v2.1.0.apk) · [Release 与更新记录](https://github.com/bailishta/bilibili-auto-watch-later/releases/tag/android-v2.1.0) · [完整更新日志](../CHANGELOG.md)

## 手机界面

- **首页**：追踪人数、本次新发现、立即检查、跳转 B站安卓 APP 的稍后再看、累计添加与上次检查。未安装可用的哔哩哔哩 APP 时显示提示。
- **名单**：头像、名称、UID、粉丝数、最后视频更新时间；支持搜索、增删、粉丝数／更新时间排序，以及 JSON 名单导入导出。
- **更新**：按自然周查看每天更新人数，切换本周和历史周。进入某天默认上午，可切换下午；无指针 12 小时表盘，下方直接显示具体分钟、头像及名字。
- **设置**：通过带下拉箭头的选择框调整手动检查时间范围、更新记录保留周数、外观主题、配置备份、重新导入关注、日志及退出登录。

### 液态玻璃主题

仅在「设置 → 外观主题」打开「液态玻璃」，即可即时切换；再次关闭恢复原有主题。外观选择自动保存，重启后保留，也包含在配置备份中；没有此字段的旧备份恢复为原有主题。

液态玻璃支持跟随系统、浅色和深色，效果仅用于底部悬浮导航。卡片、顶栏、按钮、输入框、开关、菜单与弹窗使用清晰的普通材质，页面采用纯色背景。导航接入 Kyant0/AndroidLiquidGlass 1.0.6 的完整 Backdrop 渲染源码及 LiquidBottomTabs 实现：外层采用 8 dp 背景模糊和曲面折射；选中透镜按下鼓起、放大图标，可拖动并随速度形变，松手回弹，边缘包含真实像素色散和方向性高光。正文卡片保持普通材质。纯色背景下静止玻璃较淡，滚动内容经过导航或按压／拖动时光学变化更明显。

Android 13 及以上使用 AGSL；Android 12 使用背景模糊与移动胶囊，Android 8–11 使用高不透明度兼容材质。页面与导航内容使用受控 GPU 图层采样，不进行截图轮询。保留原有主题开关和配置保存。具体实现及验证范围见 [液态玻璃实现说明](design/liquid-glass.md)。上游 Apache-2.0 许可随 APK 附带，源码固定版本及兼容改动见 [第三方源码说明](third-party/Backdrop.md)。

APP 的检查由「立即检查」发起，没有定时检查或自动检查器。检查期间在通知中心显示已完成 UP 主数量和进度条，可切到其他应用；Android 13 及以上首次检查会请求通知权限，拒绝后仍可在 App 内查看进度和结果。前台服务只在检查运行时存在，完成、取消或暂停后立即停止并移除进度通知。检查完成另发一条「检查结果」通知，显示新增数量（包括没有新增视频）和失败项数量，点击打开 App，通知可划掉；暂停、取消不发送完成提醒。通知渠道设置和系统勿扰设置仍由用户控制。

运行期间持有临时 CPU 唤醒锁（不点亮屏幕），在所有退出路径释放；单次前台服务运行最多 30 分钟。检查断点首次落盘后，会登记一个只关联本次检查的 JobScheduler 系统任务：有网络且系统允许时择机续查，失败后指数退避，自动恢复窗口为一小时，每次恢复最多执行五分钟并保留进度。系统任务直接执行续查，不从后台强行启动前台服务。完成或取消后移除恢复任务；旧任务不能新建检查或重跑已完成任务。没有常驻服务、周期轮询、精确闹钟、忽略电池优化或自启动权限要求。系统任务不跨手机重启保存；重启、强行停止或恢复窗口结束后，回到 App 仍可使用数据库断点继续。

断网或请求连接中断时保存本次名单、原始检查时间范围、已完成 UP 主及已添加视频。视频列表读取在连接仍存在时遇到传输错误，会间隔 750ms 重试一次；仍失败后暂停。系统网络验证标志不作为即时检查请求的硬性条件；系统续查任务则遵守 Android 的联网条件。网络未连接与请求超时／连接失败分别提示，添加视频的写请求不会在读取重试逻辑中重放。暂停后交由系统择机恢复，不持有唤醒锁等待网络；重新进入 App 且网络可用也会续查，或点击「继续检查」。已完成的 UP 主会跳过，未完成的 UP 主继续处理尚未添加的视频。点击「取消」会清除断点和系统恢复任务；账号变更不会沿用旧账号的任务。暂停期间也可查看名单和更新记录，取消或完成后再修改名单、配置或退出登录。

打开「稍后再看」时，依次尝试独立页面、B站专用链接接收入口及新旧链接。只有系统报告启动失败才会继续尝试；请求被接收后，无法在本 APP 内确认 B站是否显示了目标页。入口全部失败时尝试打开 B站主界面，并提示手动进入。设置中的「运行日志」会记录 B站版本、尝试入口和启动异常，便于真机反馈。

## vivo / iQOO 后台限制

亮屏切到其他 App 后出现请求超时不等于进程已被杀。默认使用前台服务、持久化断点与系统续查，不要求开启「后台高耗电」。但 Android 的 Doze 会暂停网络和系统任务，厂商也可能施加额外限制，因此不能承诺零权限下永不被杀或立即完成。若真机仍持续中断，可选地在系统设置搜索「后台耗电管理」或「后台高耗电」，允许本应用后台运行；这不是开始检查的前置条件。

实现参考：[Android 后台任务调度](https://developer.android.com/develop/background-work/background-tasks/persistent)、[Doze 限制](https://developer.android.com/training/monitoring-device-state/doze-standby)、[JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler)。对照了 [NewPipe 下载服务](https://github.com/TeamNewPipe/NewPipe/blob/dev/app/src/main/java/us/shandian/giga/service/DownloadManagerService.java) 的运行通知、完成通知及任务结束释放资源的处理方式；未复制其源码。

日志会记录前后台切换、服务启动／销毁、连接失败的异常类型、屏幕与省电状态、系统联网验证，以及 Android 11 以上系统提供的最近进程退出原因，以便区分请求失败和进程终止。临时唤醒锁无法保证绕过厂商对后台联网或进程的限制。

## 数据与备份

- 手动检查同时采集粉丝数、最后视频发布时间及最近视频记录，保存到本机 Room 数据库；排序和图表不再请求网络，去掉独立的刷新数据按钮。
- 更新记录默认保留最近 3 个自然周，含本周，可设 1–52 周。按设备时区、周一到周日统计；周、日、半天、每分钟分别按 UID 去重，每位 UP 主最多 500 条。
- 记录来自接口返回的近期视频，非完整投稿历史。缩短保留周数会清理范围外记录，延长后逐步积累。
- 名单导入是合并操作，已有 UID 会跳过。配置导入先校验并预览，再替换名单、设置和更新记录，保留本机已处理的视频记录及统计，不包含登录 Cookie。
- Android 可读取插件格式版本 1 的配置备份，只恢复支持的名单及设置，不应用浏览器检查器。Android 导出的配置含空检查器列表，不要用它覆盖需要保留排程的浏览器配置；仅交换名单时使用名单导入／导出。
- 检查时最多同步最近 500 条观看历史，按至少 12 小时间隔、每天最多两次采集；未到间隔或达到限额时使用本地记录，次数跨重启保留。每一页立即把已观看 BVID 和观看时间按 B站账号保存到 Room。保留范围为手动检查范围 × 2 后向上取整为天，再加 1 天，包含今天；周六检查「1 天内」会保留周四零点起的周四、周五和周六记录。保留期内不因退出最近 500 条而删除，接口失败时继续使用本地记录。Android 没有自动检查器，也不提供提前背景采集；Chrome 支持按自动排程提前采集。记录不包含完整观看详情，也不包含在配置备份中；首次同步时已超出 500 条范围或两次检查间被淘汰的历史无法补回。
- Room 显式迁移 `1 → 2 → 3 → 4 → 5 → 6`，新增更新记录、已观看视频、观看时间列及检查断点表，保留旧数据。旧观看记录没有时间，在升级时开始一个保留周期。旧更新频率排序迁移为最后视频更新时间排序。

## 编译

要求 JDK 17、Android SDK 35。配置本机 `local.properties` 后，在本目录运行：

```powershell
.\gradlew.bat --no-daemon assembleDebug
```

安装测试包输出为 `app/build/outputs/apk/debug/app-debug.apk`。已有完整 Gradle 缓存时可增加 `--offline`。

编译后可在项目根目录运行 `python tests/android-watch-history-migration.py`，使用临时 SQLite 数据库验证已观看视频表迁移、旧数据保留及按账号持久化；此测试不替代真机升级验证。

断点恢复和通知生命周期的模拟器测试（使用模拟接口与独立测试数据库，不请求 B站）：

```powershell
.\gradlew.bat --no-daemon assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.bilibili.watchlater.test/com.bilibili.watchlater.CheckResumeInstrumentation
```

测试覆盖断网后重建数据库连接并续查、一个 UP 主的视频添加到一半中断、冻结检查名单与时间范围、旧恢复任务失效、取消清理、业务错误区分、后台系统续查、完成与零结果通知，以及进度通知和服务在暂停／完成／取消后释放。测试专用空白 Activity 仅包含在 Debug 包中。测试代码通过编译不代表真机行为已验证；厂商后台限制需在目标手机上测试。

## 正式构建与签名

```powershell
.\gradlew.bat --no-daemon assembleRelease
```

Release 启用 R8 代码压缩，输出 `app/build/outputs/apk/release/app-release-unsigned.apk`，需要使用本机私钥签名后才能安装。使用 Android SDK 的 `apksigner`，通过环境变量指定签名文件和密码：

```powershell
& "$env:ANDROID_HOME/build-tools/35.0.0/apksigner.bat" sign `
  --ks "$env:BILI_SIGNING_STORE" --ks-key-alias "$env:BILI_SIGNING_ALIAS" `
  --ks-pass env:BILI_SIGNING_STORE_PASSWORD --key-pass env:BILI_SIGNING_KEY_PASSWORD `
  --out app/build/outputs/apk/release/bilibili-watch-later-android-v2.1.0.apk `
  app/build/outputs/apk/release/app-release-unsigned.apk
```

本次发布沿用已在此电脑使用的测试签名证书，便于同签名覆盖升级；构建类型为 Release，非 Debug。后续版本需要沿用同一私钥。妥善备份本机签名文件，不提交签名密钥、密码或 `local.properties` 到 GitHub。

发布使用标签 `android-v2.1.0`，附件只包含已签名 APK 和 SHA-256 文件；插件使用独立标签 `v2.1.0`。详细发布说明在 [Android 发布记录](../docs/releases/android-v2.1.0.md)。

## 安装后重点查看

使用现有安装项覆盖升级，查看旧名单及登录是否保留。主要流程包括：检查范围下拉选择与保存、跳转 B站 APP 的稍后再看（含未安装时的提示）、手动检查及取消、缓存排序、周／日及上午／下午切换、备份预览取消与恢复、名单导入导出、深色模式和系统字号变化。液态玻璃主题重点检查设置页开关、切换后当前页面与滚动位置保留、重启保存、浅／深色效果、配置备份恢复，以及手势／三键导航下的底部间距。真机效果由用户在自己的设备上验证。
