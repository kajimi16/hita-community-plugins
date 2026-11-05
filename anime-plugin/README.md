# 番剧插件

`com.stupidtree.hita.ui.anime`，OH-MY-HITA 的运行时界面插件。

- 源码仓库：[kajimi16/hita-anime-plugin](https://github.com/kajimi16/hita-anime-plugin)
- 市场索引：本仓库 `registry/index.json`
- 宿主要求：API 5+，Compose 1.6.8

## 能力

- **首页**：今日放送、本周新番、继续追番（读取 Bangumi 在看收藏）
- **放送表**：Bangumi `calendar` 当前在播番组，按星期分组，包含长篇连载
- **搜索**：关键词、类型、排序、年份/季度、标签组合筛选，分页加载
- **追番**：五种收藏状态、进度与评分、分页
- **条目详情**：封面、评分直方图、角色与 CV、制作人员、关联作品、章节、收藏/评分/短评、弹幕、评论、资源索引
- **弹幕**：只看本校、发送（位置/颜色/时间点）、刷新
- **我的**：Bangumi 访问令牌、hit-ani 会话、站点地址、缓存清理

## 与 Animeko Android 的对应

| Animeko | 本插件 |
|---|---|
| 首页 / 时间表 / 搜索 / 追番 | ✅ |
| 条目标题、评分、角色、制作、关联 | ✅ |
| 收藏五状态 / 评分 / 短评 / 进度 | ✅（Bangumi v0） |
| 弹幕浏览 / 发送 / 本校筛选 | ✅（hit-ani） |
| 多源资源选择 | ✅ 外站资源索引 |
| 播放器 / BT / 离线缓存 | ❌ 插件不声明 Activity，也不携带播放内核 |

## 缓存与离线回退

插件按顺序尝试「我的 → 站点地址」中配置的地址（逗号分隔）：

1. 数据：`{base}/api/bgm/calendar`、`/api/bgm/v0/subjects/{id}` 等，由 hit-ani 落库缓存
2. 封面：`{base}/_next/image?url=...`
3. 全部失败才直连 `https://api.bgm.tv` 与 `lain.bgm.tv`

收藏、进度、令牌校验始终直连 BGM；代理是只读白名单，`v0/me` 与收藏写接口不可达。

## 课表联动

通过宿主 `TimetableService` 读取今日课程，与「今日放送」并列展示；
课程点击交给 `TimetableUiService` 打开宿主课表。课表插件未启用时自动隐藏。

## 账号

- **Bangumi**：个人访问令牌，scope 至少 `write:collection`
- **hit-ani**：插件保存 `hitani_session` Cookie，用于弹幕、校内评论与资源索引
- 未登录也可浏览首页、时间表、搜索、条目详情与弹幕

## 元数据

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
# 产物：dist/anime-0.3.0.apk
```

单元测试：

```bash
./gradlew :anime-plugin:testReleaseUnitTest
```

## 已知限制

- 宿主底部导航会覆盖运行时 tab 的底部区域，插件内部导航放在顶部
- 插件不能声明 Activity，播放交给外站/外部播放器
- 返回键在插件根 View 拦截；宿主若改用标准 `OnBackPressedDispatcher` 仍兼容
