# HITA Community Plugins

OH-MY-HITA 的社区插件仓库。这里的插件不随宿主 App 发版，通过插件市场索引安装。

## 插件

| 插件 | 说明 | 宿主要求 |
|---|---|---|
| 番剧 `com.stupidtree.hita.ui.anime` | 参照 Animeko Android 端的番剧客户端：首页 / 时间表 / 搜索 / 追番 / 条目详情 / 弹幕 / 评论 / 资源 | API 5+，Compose 1.6.8 |

## 番剧插件 0.3.0

### 能力

- **首页**：今日放送、本周新番、继续追番（读取 Bangumi 在看收藏）
- **放送表**：Bangumi `calendar` 的当前在播番组，按星期分组；含航海王、柯南这类长篇连载（不再按首播日期筛选）
- **搜索**：关键词 + 类型 + 排序 + 年份/季度 + 标签组合筛选，分页加载
- **追番**：五种收藏状态分组、进度与评分、分页
- **条目详情**
  - 封面 / 评分 / 排名 / 首播 / 话数 / 标签 / 作品信息 / 评分直方图
  - 收藏状态、评分（1–10，`清除` = 0 分）、短评
  - 章节格子、单集标记、全部标记看过、弹幕入口
  - 角色（含 CV）、制作人员、关联作品
  - 弹幕列表（hit-ani 本地 + Animeko/dandanplay 外部源）
  - 校内评论 / 影评 / 收藏短评
  - 外部资源索引（按集分组，跳转外站）
- **弹幕**：只看本校、发送（位置/颜色/时间点）、刷新
- **我的**：Bangumi 个人访问令牌、hit-ani 会话登录、站点地址、缓存清理

### 与 Animeko Android 的对应关系

| Animeko | 本插件 |
|---|---|
| 首页 / 时间表 / 搜索 / 追番 | ✅ |
| 条目标题、评分直方图、角色、制作、关联 | ✅ |
| 收藏五状态 / 评分 / 短评 / 进度 | ✅（Bangumi v0） |
| 弹幕浏览 / 发送 / 本校筛选 | ✅（hit-ani） |
| 多源资源选择 | ✅ 资源索引（外站链接） |
| 播放器 / BT / 离线缓存 | ❌ 插件不声明 Activity，也不携带原生播放内核 |

### 缓存服务器与离线回退

插件按顺序尝试配置的地址（「我的 → 站点地址」，逗号分隔）：

1. 数据：`{base}/api/bgm/calendar`、`/api/bgm/v0/subjects/{id}`、`/api/bgm/v0/episodes` 等
   —— 由 hit-ani 落库缓存（周历 6h、条目/章节 24h、上游失败回退 3 天内旧数据）
2. 封面：`{base}/_next/image?url=...&w=384/640/1080&q=85`
3. 全部失败才直连 `https://api.bgm.tv` 与 `lain.bgm.tv`

收藏、进度、令牌校验始终直连 BGM（需要用户自己的身份）。代理是只读白名单，
`v0/me` 与收藏写接口不可达。

### 课表联动

插件通过宿主 `TimetableService` 读取今日课程，在首页与「今日放送」并列展示；
课程点击交给 `TimetableUiService` 打开宿主课表页。课表插件未启用时该区块自动隐藏。

### 账号与数据

- **Bangumi**：填入个人访问令牌（scope 至少 `write:collection`）。
  写入接口是 `POST /v0/users/-/collections/{id}` 与
  `PUT /v0/users/-/collections/-/episodes/{id}`。
- **hit-ani**：插件自己保存 `hitani_session` Cookie，不依赖 WebView；
  用于弹幕、校内评论与资源索引。地址可在「我的」里改。
- 未登录也可以浏览：首页、时间表、搜索、条目详情、弹幕读取。

### 版本

| 字段 | 值 |
|---|---|
| version | `0.3.0` |
| apiVersion | `5` |
| composeVersion | `1.6.8` |
| capabilities | `hita.ui.mainTab` |
| mainTabs | `anime`（标题「番剧」，order 100，`assets/tab_anime.png`） |

## 构建

```bash
./gradlew publishAnime
```

产物：`dist/anime-0.3.0.apk`，构建脚本会打印 sha256。

依赖的 `:api` / `:model` / `:component` AAR 放在 `libs/`，来自 OH-MY-HITA 发布构建。
插件对 Compose、协程、`kotlin-stdlib` 以及 `material-icons-core` 使用 `compileOnly`，
运行时全部由宿主提供。

单元测试（数据解析 / 日期区间）：

```bash
./gradlew :anime-plugin:testReleaseUnitTest
```

## 安装

在 OH-MY-HITA 的「插件市场 → 查询并下载」里选择索引源安装；
插件 APK 地址与 sha256 由市场索引配置，直接下载 APK 安装无效（界面插件由宿主容器加载）。

模拟器/调试包手动安装（宿主必须是 debuggable）：

```bash
adb push dist/anime-0.2.0.apk /data/local/tmp/anime.apk
adb shell run-as io.github.kajimi16.ohmyhita mkdir -p files/ui-plugins/com.stupidtree.hita.ui.anime
adb shell run-as io.github.kajimi16.ohmyhita cp /data/local/tmp/anime.apk \
  files/ui-plugins/com.stupidtree.hita.ui.anime/plugin.apk
```

再在 `shared_prefs/ui_plugins.xml` 写入安装记录（`apkPath` / `sha256` / `capabilities`）。

## 新增插件

1. 新建 Android application 模块，`compileOnly` 依赖 `libs/` 与 Compose 运行时
2. 实现 `ComposeUiPlugin`，在 `assets/plugin.json` 写 id/version/apiVersion/composeVersion/capabilities
3. 在 `settings.gradle` 注册模块，把 APK 复制到 `dist/`
4. 更新市场索引（主仓库 `market` 分支）

## 0.3.0 变更

- **课表联动**：首页新增「今日课表」，读取宿主 `TimetableService.getTodayEventsSync()`，
  显示上课时间/地点/教师，当前正在上的课标「上课中」；点击打开课表详情。
- **校内/国内缓存回退**：数据优先走 hit-ani `/api/bgm/*`（服务端落库缓存），
  封面走 `/_next/image`；服务器不可达时自动直连 BGM。站点地址支持逗号分隔多个，
  按顺序尝试。
- **封面清晰度**：按实际显示尺寸取图（320/640/1080 分档），改用 `ARGB_8888` 解码，
  网格/首页使用 `large` 原图，不再把 400px 图拉到 540px；缓存按尺寸分档。
- 需要部署新版 hit-ani 才会有 `/api/bgm` 代理；未部署时插件自动直连，不回归。

## 0.2.3 变更

- 封面加载：BGM 旧版 `/calendar` 返回 `http://lain.bgm.tv/...`，宿主的明文策略
  不可控，导致首页/放送表封面空白。现在统一把图片地址升级为 https（加载器与
  `ImageSet` 双保险）。

## 0.2.2 变更

- 修正返回键：宿主 `onBackPressed()` 不调用 `super`，Compose `BackHandler` 收不到；
  插件改为在根 `FrameLayout` 上消费 `KEYCODE_BACK`，系统返回键现在按
  「弹幕页 → 条目详情 → 番剧首页」逐级退出，不再直接回桌面。
- 新增 `isShown` 守卫，切到宿主其它 tab 时不会偷吃返回键。

## 0.2.1 变更

- 修正放送表逻辑：此前按 `air_date` 区间搜索，只能看到「本周首播」的番，
  在播长篇全部消失。现改用 BGM `calendar`（当前在播），按星期铺满七天。
- 首页「本周新番」改为「本周放送」，与放送表共用同一次 `calendar` 请求。

## 已知限制

- 返回键已在插件根 View 拦截；若宿主未来改成标准 `OnBackPressedDispatcher`，
  这段兜底仍然生效（Compose 的 `BackHandler` 也可重新启用）。
- 宿主底部导航会盖住运行时 tab 的底部区域，因此插件内部导航放在顶部。
- 插件不能声明 Activity，播放交给外站/外部播放器；这不影响找番、追番、弹幕与评论。

## License

MIT
