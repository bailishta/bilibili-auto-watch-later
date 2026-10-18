# 收藏播放图标

暖玫瑰渐变底色、暖白书签与播放镂空：收藏对应「稍后」，播放对应「观看」。不叠加小钟、文字或多层卡片，以便在桌面小尺寸下辨识。

- `app-icon.svg` 是可编辑设计稿，使用 108 × 108 坐标。
- Android 前景、背景分层使用矢量资源，主体置于中央安全区；圆形、圆角方形等蒙版由启动器处理。
- Android 13 以上提供同轮廓单色图标，跟随系统主题着色。
- 通知小图标使用 `ic_notification_bookmark.xml` 的同款单色书签播放图形，按 24dp 单独适配尺寸。OriginOS 通知左侧应用标识由系统根据应用图标显示；`setLargeIcon` 是右侧附图，不能替换左侧应用标识，因此不设置。检查进度与完成通知共用该配置。
- App 内品牌标识使用 `app_brand_mark.xml`，与桌面图标统一。
- 浏览器插件图标保持原样。

参考：[Google 图标设计教程](https://codelabs.developers.google.com/design-android-launcher)、[Android 自适应图标](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)。
