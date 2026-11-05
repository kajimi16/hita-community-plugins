# 课表壁纸与样式

直接调整 OH-MY-HITA 原有课表，支持壁纸选择/清除、课程卡片不透明度、背景模糊、暗色遮罩，以及清晰/柔光/磨砂/夜幕预设。

需要合入 TimetableAppearanceService 的 API 6 宿主；旧版宿主不能安装。该插件只提供设置界面，没有另一份课程数据库，也不新增课表 tab。修改自动保存，返回原有课表查看。

构建：`./gradlew :wallpaper-plugin:assembleRelease`，产物 wallpaper-plugin/build/outputs/apk/release/wallpaper-plugin-release-unsigned.apk。通过宿主 UI 插件加载器安装，不能作为独立 App 启动。

界面使用原生 View，不要求 Compose 版本。APK 不打包宿主 API、模型或运行时；wallpaper-contract 是 compileOnly 契约，源码与宿主 api/TimetableAppearanceService.kt 完全一致，待 API 6 AAR 发布后可换成新 AAR。

图片经系统选择器复制到宿主私有目录，不上传；取消选图或导入错误不会替换旧图。设置在关闭或卸载本设置插件后仍保留，可使用恢复默认清除。清除内置课表数据也会清除壁纸。

市场索引应等待兼容宿主发布，再填写真实 APK 下载 URL、SHA256 和 minHostApi=6。
