# 插件开发与发布

插件运行时为原生 JavaScript 与 Manifest V3，无构建步骤。以下操作仅针对浏览器插件。

## 模块分工

| 路径 | 职责 |
| --- | --- |
| `service-worker.js` | 消息路由、检查器 alarms、持久化待检查队列、配置导入互斥 |
| `core/api.js`、`content.js` | B站请求、认证、WBI 签名与页面请求回退 |
| `core/checker.js`、`core/meta.js` | 检查视频、观看过滤、添加稍后观看、采集排序字段与发布时间 |
| `core/storage.js` | 本地存储、串行写入、记录清理与配置恢复 |
| `core/schedules.js` | 检查器校验、旧设置迁移与下次执行时间 |
| `core/activity.js` | 自然周、分钟统计、记录去重与本地排序 |
| `core/backup.js` | 配置备份版本、字段白名单及输入校验 |
| `popup/`、`manage/`、`ui/` | 基础弹窗、完整管理中心、共享主题 |

`settings.checkers` 保存独立检查器，旧 `schedule` 或 `checkDays/checkTime` 自动迁移。每个启用的检查器使用独立的一次性 alarm，并在开始长任务前安排下一次运行。待执行检查器 ID 存于 `_pendingCheckers`，由队列及重试 alarm 恢复执行。

`collect-watch-history` 使用共享的一次性 alarm：提前两倍检查范围进入采集区间，采集间隔至少 12 小时，每个本地自然日最多两次；`_historySyncBudget` 在请求前串行持久化尝试日期、次数和时间，启动、检查器、手动检查及失败重试共用限额。采集与视频检查、元数据刷新互斥，不触发视频添加；成功的每一页立即持久化。`watchedVideos:<uid>` 保存版本 2 的 BVID → 观看时间、同步时间及保留范围，保留天数为最大有效范围 × 2 后向上取整为天，再加 1 天，清理边界取最早保留日期的零点，包含检查当天。范围扩大时重新获取基线，补采仍遵守每日限额。旧版 BVID 数组迁移时赋予一次采集时间。

追踪名单中的 `fans`、`lastPubdate`、`metaUpdatedAt`、`publications` 在检查过程中更新。排序和图表仅访问本地记录。图表以浏览器本地时区计算自然周，每个时间范围独立按 UID 去重。

名单导入是合并操作；配置导入是替换操作，格式标识为 `bilibili-watch-later-configuration`、格式版本为 `1`，与插件版本独立。配置不包含登录信息、已处理视频和运行中的任务状态。

## 验证

在项目根目录运行核心回归测试，需要支持 VM Modules 的 Node.js：

```powershell
node --experimental-vm-modules --test tests/sorting-cache.test.cjs
node --experimental-vm-modules --test tests/watch-history.test.cjs
```

真实扩展测试使用 Playwright 和 Chromium 的独立临时 profile，不使用个人 B站登录。环境中需能解析 `playwright` 包，可通过 `NODE_PATH` 指向已有安装；可选 `CHROMIUM_EXECUTABLE` 指定支持加载扩展的 Chromium 可执行文件：

```powershell
node tests/extension-ui.cjs
```

管理页交互测试需要 `playwright-cli`。先在一个终端启动仅监听本机的静态服务，再在另一个终端运行测试：

```powershell
python -m http.server 8765 --bind 127.0.0.1
```

```powershell
playwright-cli -s=watch-later-ui open http://127.0.0.1:8765
playwright-cli -s=watch-later-ui --raw run-code --filename=tests/ui-smoke.js
playwright-cli -s=watch-later-ui close
```

测试使用示例数据，覆盖本地排序、多个检查器、记录去重、直接展示的分钟列表、设置持久化、明暗主题与窄屏布局。截图保存在已忽略的 `.playwright-cli/` 目录。

## 发布

1. 更新 `manifest.json`、README、CHANGELOG 和对应的发布说明。插件版本使用三段数字，例如 `2.1.0`，标签为 `v2.1.0`。
2. 运行上述验证和 `git diff --check`；仅暂存插件、文档及测试，避免带入 Android 的未提交改动。
3. 提交后用明确的路径清单打包，ZIP 根目录必须直接包含 `manifest.json`：

```powershell
git archive --format=zip --output=bilibili-auto-watch-later-v2.1.0.zip HEAD manifest.json service-worker.js content.js core popup manage onboarding icons ui README.md CHANGELOG.md
```

4. 检查 ZIP 版本与模块引用，确认没有 Android、测试、开发缓存或个人数据，生成 SHA-256 校验文件。
5. 用 `git push` 推送提交和匹配的标签，用 `gh release create --verify-tag --notes-file docs/releases/v2.1.0.md` 发布插件 ZIP 与校验文件。

插件 Release 不上传 APK。GitHub 自动生成的 Source code 压缩包是仓库源码；面向安装的附件应使用单独打包的插件 ZIP。
