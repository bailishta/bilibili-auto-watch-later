# 应用图标

浏览器插件与 Android 共用蓝色层叠视频卡片、白色播放符号和粉色时钟的图案。
原始透明图由内置 imagegen 工具生成，提示词见 `design-prompt.txt`。

- `icon-master.png`：原始透明图，保留用于后续调整。
- `icon16.png`、`icon32.png`、`icon48.png`、`icon128.png`：插件图标。
- `../android-app/app/src/main/res/drawable-nodpi/ic_launcher_art.png`：Android 自适应图标前景，432 × 432，含安全边距。背景为深蓝色 `#14233D`。

在 Windows 上重新生成各尺寸资源：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/generate-icons.ps1
```

脚本仅缩放并居中原始图案，使用 Windows 内置的 System.Drawing，不需要安装额外依赖。
