# 底部液态玻璃：完整 Backdrop 接入（2026-11-03）

当前实现固定接入 [Kyant0/AndroidLiquidGlass 1.0.6](https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.6) 的完整渲染源码及底部导航示例，替换旧的局部算法改编。上游 commit 为 `896a94a3ade1cc1a940b92365f942a34971fecda`。采用源码内置方式适配现有 Kotlin / Compose，无新二进制依赖；详见 [兼容改动与许可](../third-party/Backdrop.md)。

## 差距与修正

旧版导航使用 28 dp 模糊、较厚填色和约 3.2 dp 的简化镜片位移；图标放大、触摸鼓起、独立透镜的内阴影等效果不完整。本轮保留上游完整 8 dp blur + vibrancy + lens 链，以及独立的页面与标签采样层。移动透镜合成页面和放大的强调色标签，按压后缩放到原始高度的 78 / 56，叠加原版速度形变、色散、高光、阴影与回弹。没有把光学变化人为减到几乎不可见。

纯色背景没有可折射的纹理：同一算法在空白首页底部也会比官方壁纸演示平淡。生产页面保持普通内容，不为了玻璃凭空增加壁纸。使用滚动列表经过导航以及按下／拖动状态检查实际采样与变形。

## 范围和兼容

仅底部导航启用玻璃；顶栏无切换按钮，入口只有「设置 → 外观主题」。卡片、表单、弹窗、菜单与按钮保持普通 Material。保留现有业务回调、Tab 选中语义、应用自身浅深色、旧系统兼容和开关保存。

API 33+ 使用完整 AGSL；31–32 保留模糊和运动；26–30 使用高不透明度底色，不能宣称有完整折射。无持续空闲动画、CPU 截图轮询或递归采样。隐藏的标签副本不提供点击或无障碍节点；大字号下导航高度自适应。

## 本轮验证

`assembleDebug`、`lintDebug` 通过，Lint 0 errors / 17 warnings；`git diff --check` 通过。修复移植后首次测量前读取 `Size.Unspecified.minDimension` 的启动异常，首次有效绘制重新注册效果参数观察；手势绑定跟随重建后的动画实例。

只读 Android 14 / API 34 模拟器对照运行上游 catalog APK。检查了生产 DashboardScreen 的浅深色，以及共享生产导航的调试列表／表单场景：按住时镜片鼓起与图标文字放大、松手恢复、横向拖动切换、连续点击切换、列表滚动时背景采样更新、开关关闭／重新开启、普通添加弹窗、1.5 倍字号。界面树中四个标签各出现一次，拖到设置后只有设置选中。最终包已重新安装并录制，截图确认名单和设置镜片均能鼓起。

本轮曾遇 UIAutomator 自身读取界面树的空节点异常，重试后取得有效树；该异常属于测试工具进程，最终 App 进程崩溃日志为空。没有把工具异常算作 App 崩溃，也未以旧界面树作为最终验证依据。

证据位于忽略目录 `build/glass-qa/`：`full-verified-list-held.png`、`full-verified-scroll.png`、`full-verified-drag.xml`、`full-verified-settings-held.png` 与 `liquid-glass-final.mp4`（约 19.5 秒）。首页和大字号截图为 `full-final-home-*.png`、`full-final-largefont.png`，开关与深色弹窗为 `full-switch-*.png`、`full-dialog-dark.png`。调试列表使用模拟内容，不代表真实账号数据；未做账号业务回归、真机性能或 Android 8–12 设备验证。

---

# 以下为历史实现记录（不代表当前代码）

# 克制的底部液态玻璃导航（当前实现）

本轮按用户提供的「反推液态玻璃提示词」对话收敛范围：仅底部悬浮导航保留玻璃。正文卡片、统计块、顶栏、按钮、输入框、筛选项、开关、菜单与弹窗恢复普通 Material 材质；纯色背景移除装饰渐变。业务保持原有接线；液态玻璃只通过「设置 → 外观主题」开关切换，顶部切换按钮已于 2026-11-03 移除。

- `GlassNavigationBar.kt`：点击和横向拖动；Animatable 弹簧连续重定向，阻尼 0.84、刚度 650，速度驱动最多 9% 横向拉伸及 4% 纵向压缩。保留 Tab 选中语义、RTL 和字体缩放布局。
- `LiquidGlass.kt`：页面内容记录到独立 GraphicsLayer，底部导航在记录区域外采样。普通面板不再进行离屏记录、模糊与折射。
- `NavigationLens.kt`：把导航材质及图标文字记录一次，AGSL 只替换移动透镜范围内的像素；外围原样输出，避免双份文字叠加。中心无位移，边缘曲率驱动小幅折射、RGB 色散和方向性高光。参数集中于 NavigationGlassConfig。
- Shader 与图层复用；Android 图像滤镜会快照 uniform，运动参数变化时更新 RenderEffect，静止时缓存。没有位图截图、后台轮询或空闲循环动画。性能目标尚需真机测量，不承诺具体帧率。
- Android 13+ 使用 AGSL；Android 12 保留导航背景模糊和移动胶囊；Android 8–11 使用高不透明度材质和胶囊动画，低版本不声称支持真实折射。

实现核对 [Compose 图层记录](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers) 与 [Android AGSL](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl)。既有光学算法许可保留在 assets/licenses。下方内容为之前全应用玻璃探索的历史记录，其覆盖范围及验证结论不代表本轮版本。

### 本轮验证（2026-11-01）

`assembleDebug`、`lintDebug` 和 `git diff --check` 通过，Lint 0 errors / 17 项既有 warnings。只读 Android 14 模拟器验证生产首页、共用表单和添加弹窗，覆盖浅深色、横向拖动、连续切换、关闭／重新开启主题、滚动采样和 1.5 倍字号。最终包快速连续切换后「设置」选中语义正确，崩溃缓冲区为空。修复验证中发现的图层输出越界，并补正普通主按钮的前景对比度。

截图、界面树位于忽略目录 `build/glass-qa/restrained-*`。`restrained-home-light.png` 与 `restrained-home-dark.png` 是最终包首页截图；这些历史截图中的顶部两个按钮原为调试入口，2026-11-03 已移除，调试场景也只在设置页保留玻璃开关。未执行真实账号业务请求；未做真机帧率和 Android 8–12 设备验证，版本降级路径目前仅通过编译与静态检查。

---

# 液态玻璃实现

## 参考与取舍

- 当前视觉基准为用户指定的 [NeriPlayer Clean](https://github.com/cwuom/NeriPlayer/tree/51f0100c4ae2982a43f5a1ca3d64399dae02384c)。阅读 `AdvancedGlassTokens.kt`、`AdvancedGlassSurface.kt`、`AdvancedGlassController.kt`、`AdvancedGlassHost.kt`、`CleanTheme.kt` 及选中胶囊实现，参考材质层级与视觉参数，在本项目原有渲染器上独立实现；未导入 NeriPlayer 的代码模块或资源。
- [Apple WWDC25 · Meet Liquid Glass](https://developer.apple.com/videos/play/wwdc2025/219/)：材质的关键是 lensing、动态光学反馈与弹性；Apple 推荐主要用于浮于内容上的导航和控件。本项目按用户要求扩展为全应用主题，借鉴光学与交互效果，而非严格复制 Apple 的使用范围。
- [Kyant0/AndroidLiquidGlass 1.0.6](https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.6)：阅读其背景记录、目标坐标映射、RenderEffect 链和 Shaders.kt，适配圆角矩形距离场与圆弧折射算法。固定参考提交 `896a94a3ade1cc1a940b92365f942a34971fecda`。项目仍使用原有 Compose 版本，没有为引入整套库升级构建工具。
- [Android Graphics modifiers](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers) 与 [RuntimeShader](https://developer.android.com/reference/android/graphics/RuntimeShader)：记录／复用 GraphicsLayer，在 Android 13+ 使用 AGSL。

## 绘制与交互

采用无环采样链：独立背景 → 面板材质 → 内嵌控件 → 完整内容层 → 悬浮栏／弹窗。根背景不绘制页面子项；`AppSurface` 只把自身玻璃背景提供给子控件，不包含文字与子项；`captureGlassBackdrop` 记录完成后的页面，只有记录范围外的悬浮栏、菜单和弹窗采样此层。这样内容区域也能使用真正的着色器玻璃，同时避免自采样递归。弹窗／菜单通过实际窗口的屏幕坐标对齐采样。嵌套材质记录显式传入稳定的 `LocalDensity`，避免可变绘图上下文自引用；页面内容记录保留 `DrawScope.record` 的画布重定向，确保文字和控件进入正确记录层。页面使用列表内部 contentPadding 预留导航位置，因此滚动内容能够经过悬浮栏下方，最后一项也可以完整滚出遮挡区域。

外层玻璃面板按坐标从记录层重绘所需区域，在四周留出采样余量；先模糊，再沿圆角轮廓做轻微折射、少量 RGB 色散和方向性高光。`GlassMaterialStyle.kt` 统一控制卡片、嵌套块、控件、导航和弹窗的底色、透明度与模糊。内嵌控件复用父材质，不再次模糊或折射。可读性填色在光学处理后统一叠加，前景图标和文字不参与模糊。没有屏幕截图轮询、CPU 位图转换或持续的空闲动画。

底部导航使用弹簧动画移动选中胶囊，触摸时略微拉伸并增强折射。横向拖动在松手后切换到最近项目；点击、返回首页、浅／深色切换保留原有操作。选项保留 Tab 角色和选中语义。首页、名单、更新、设置均通过共用组件覆盖；导入、日志和登录页的原生外框同步接入。按钮保留点击与禁用语义，增加按压回弹；表单保留焦点、错误与禁用状态，弹窗支持返回、点击外部关闭及键盘输入。背景使用静态、低对比度的柔色渐变，移除了大弧线和装饰线条。

### 当前材质层级

| 层级 | 浅色／深色底色不透明度 | 模糊 | 用途 |
| --- | --- | --- | --- |
| 外层卡片 | 62% / 56% | 22 dp | 面板与列表卡片 |
| 内嵌块 | 92% / 88% | 无二次模糊 | 统计块、卡片内的子区域 |
| 控件 | 86% / 82% | 无二次模糊 | 按钮、输入、开关、筛选项 |
| 悬浮栏 | 56% / 48% | 28 dp | 顶栏、底部导航 |
| 弹窗和菜单 | 62% / 56% | 24 dp | 页面内容之上的浮层 |

NeriPlayer 的 `cleanLegibleTintFloor` 对内容卡片设置 62% / 56% 底色下限，并禁止嵌套玻璃再次采样；本轮以此修正上一版卡片近乎消失的问题。轮廓以材质填色为主：仅外层卡片和浮层辅以 0.5 dp、2.5%–7% 不透明度的中性色细线，内嵌块和控件不描边；无白色亮框和内侧暗圈。边缘折射带收至 14 dp，最大位移约 6 dp。首页和设置分组间距为 18 dp，页面侧边距为 20 dp。

Android 13+：完整运行时折射；Android 12：实时模糊与高光；Android 8–11：实时背景副本加高不透明度兼容材质。不能将低版本兼容外观描述为完整折射。

## 授权

折射算法改编源保留上游版权与修改标记。APK 的 `assets/licenses` 包含 `AndroidLiquidGlass-Apache-2.0.txt` 和 `AndroidLiquidGlass-NOTICE.txt`。

## 验证

构建：`gradlew.bat --offline --no-daemon assembleDebug lintDebug`。

Debug 专用 `GlassPreviewActivity` 提供不涉及账号或业务请求的光学验证场景：直接使用生产首页和更新页，以及共用玻璃表单、列表、菜单和添加弹窗。检查嵌套材质、滚动采样、边缘弯折、点击和拖动、浅／深色，以及关闭／重新开启主题。该 Activity 不包含于 Release。

真机仍由用户验证；模拟器的 GPU、像素密度和帧率不能替代手机实际表现。可在真机重点查看名单滚动时底部玻璃是否跟随内容、最后一项是否能完全滚出导航遮挡，以及较大系统字号下的导航可读性。

2026-11-01 已在只读 Android 14 模拟器完成光学场景检查：着色器正常运行，背景随滚动变化（导航区域滚动前后平均 RGB 差值 10.84）；点击和横向拖动后的 Tab 选中语义正确；浅色、深色、关闭／重新开启主题及 1.5 倍字号均完成界面检查。最终包拖动到「设置」验证通过，未发现应用崩溃。`assembleDebug`、`lintDebug` 与 `git diff --check` 通过；Lint 仍有项目既有的目标 SDK、备份声明与未使用资源提示。截图和界面树保存在忽略目录 `build/glass-qa/`，验证的是独立光学场景及共享导航组件，不代表完整业务流程或真机验收。

### 全应用覆盖回归（2026-11-01）

本轮使用 Android 14 只读模拟器，在独立调试场景直接显示生产首页、更新图表和表盘，并检查共用面板、嵌套统计卡、按钮、输入框、筛选项、开关、菜单和添加弹窗。验证了键盘输入与取消、浅色／深色、关闭／重新开启主题、1.5 倍字号，以及列表滚动时的导航实时采样（导航区域平均 RGB 差值 14.22）。中途发现并修复嵌套记录的 Density 自引用和页面画布重定向问题。此场景不执行账号请求或修改生产偏好设置；真实账号业务流程与真机 GPU 表现仍由用户验证。

最终包补验：开关材质限定于 52 × 32 dp 轨道并保留 48 dp 触控空间；移除未聚焦输入框的重复描边；导航按手指坐标选择，拖动至「设置」后的选中语义通过。最终 `assembleDebug`、`lintDebug`、`git diff --check` 通过，Lint 为 0 errors / 17 项既有 warnings；修复后应用崩溃缓冲区为空。

### 按用户指定的网页参考校准（2026-11-01）

用户指定参考：[Liquid Glass Studio](https://liquid-glass-studio.vercel.app/)、[Liquid Glass for the Web](https://liquid-glass-eta.vercel.app/)、[CSS/SVG Demo](https://winaviation.github.io/liquid-glass-demo/)、[React Demo](https://cocolayuan.github.io/liquid-glass/)、[UI Showcase](https://batch-ly.com/glass)。实际在浏览器打开对照；React Demo 的背景在当前浏览器未正常显示，不以其空白画面作为视觉依据。

主要依据 Studio 的 `fragment-main.glsl`，固定提交 `f7b28c36305a862f5cffed3ddd51511cf1204f56`，适配平方截面入射角和 Snell 折射。保留现有无环背景采样架构，全应用覆盖范围不变。

- 移除统一白色描边、导航选中胶囊描边和玻璃表盘装饰圆框。
- 移除着色器中的内侧暗线；反射根据入射角和光照方向产生，仅保留局部亮弧。
- 内容玻璃的模糊改为 0.55 dp，导航等轻浮层为 0.9 dp；基础中性染色分别为 4.5% 和 15%，仅在背景影响文字对比度时提高。弹窗和菜单使用 8 dp 模糊及 24% 染色，避免背后的文字与表单内容重叠。
- 内容卡片与内嵌控件移除贴边投影，浮层保留轻投影，避免阴影仍形成一圈框。减弱按钮着色及背景饱和感；浅色主题加深强调文字，适应更透明的底色。背景增加很淡的连续曲线，让边缘折射可见；高光只沿受光方向逐渐出现。
- Debug 光学检查页（`GlassPreviewActivity --ei page 4`）用棋盘格展示中心透视和边缘变形，便于识别实际折射。

新增 MIT 许可与来源说明随 APK 打包；原圆角 SDF 的 Apache-2.0 许可保留。

本轮在 Android 14 只读模拟器检查了首页、共用表单、菜单、弹窗与键盘输入／取消、更新图表及表盘、浅深色切换和关闭／恢复主题。修正了透明页面下未显式指定颜色的深色标题。最终安装包再次检查首页浅深色与棋盘格：中心网格位置保持，曲面边缘发生连续弯折，无整圈描边及内容卡片贴边投影。`assembleDebug`、`lintDebug` 通过，Lint 为 0 errors / 17 项既有 warnings；崩溃缓冲区为空。`build/glass-qa/final-home-light.png`、`final-home-dark.png`、`final-optics.png` 为最终包截图。未操作真机或执行账号业务请求。

### NeriPlayer Clean 材质分层校准（2026-11-01，当前版本）

用户反馈上一版卡片过于透明、难以辨认，本轮以上文列出的 NeriPlayer 材质角色及 Clean 主题为参考。除源码阅读外，还在只读 Android 14 模拟器安装官方 `NeriPlayer-51f0100c.10312030-x86_64.apk`，核对官方 SHA256SUMS，并打开 Clean、液态玻璃、高级模糊和进阶高级模糊，检查浅色／深色设置页面的实际分层。未导入 NeriPlayer 的源码模块或素材。

恢复外层卡片的磨砂底色和内部控件的独立填充；卡片、浮动导航和弹窗分别使用 22／28／24 dp 模糊，内部控件不重复折射。删除大弧线背景，减弱镜片边缘弯折与反光，内部控件不描边，外层只保留极淡中性色边缘。页面两侧留白 20 dp、卡片间距 18 dp，保留原业务布局与品牌色。

本轮检查了生产首页的浅色／深色、共用表单、菜单、添加弹窗和键盘输入／取消；最终包再次检查关闭／恢复玻璃、浅深色和导航拖动到「设置」的选中语义。`assembleDebug`、`lintDebug` 完成，Lint 为 0 errors / 17 项既有 warnings，崩溃缓冲区为空。当前最终包首页截图为 `build/glass-qa/neri-style-home-light.png` 和 `neri-style-home-dark.png`，前述网页版本截图仅用于历史对照。所有设备操作限定于本轮只读模拟器，真机仍由用户验收。
