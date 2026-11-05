# HITA Community Plugins

OH-MY-HITA 的社区插件仓库。这里的插件不随宿主 App 发版，通过插件市场索引安装。

## 插件

| 插件 | 说明 | 宿主要求 |
|---|---|---|
| 番剧 `com.stupidtree.hita.ui.anime` | Bangumi 今日放送与搜索，占用主界面第五个 tab | API 4+ |

## 构建

```bash
./gradlew publishAnime
```

产物：`dist/anime-0.1.0.apk`，构建脚本会打印 sha256。

依赖的 `:api` / `:model` / `:component` AAR 放在 `libs/`，来自 OH-MY-HITA 发布构建。

## 安装

在 OH-MY-HITA 的「插件市场 → 查询并下载」里选择索引源安装；
插件 APK 地址与 sha256 由市场索引配置，直接下载 APK 安装无效（界面插件由宿主容器加载）。

## 新增插件

1. 新建 Android application 模块，`compileOnly` 依赖 `libs/` 与 Compose 运行时
2. 实现 `ComposeUiPlugin`，在 `assets/plugin.json` 写 id/version/apiVersion/composeVersion/capabilities
3. 在 `settings.gradle` 注册模块，把 APK 复制到 `dist/`
4. 更新市场索引（主仓库 `market` 分支）

## License

MIT
