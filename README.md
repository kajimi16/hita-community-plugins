# HITA Community Plugins

OH-MY-HITA 的社区插件仓库：插件索引、构建产物与发布流水线。

- 宿主：[kajimi16/OH-MY-HITA](https://github.com/kajimi16/OH-MY-HITA)
- 模板：[kajimi16/hita-plugin-template](https://github.com/kajimi16/hita-plugin-template)
- 中央索引：[`registry/index.json`](registry/index.json)
- 发布请求：[新建 Issue](https://github.com/kajimi16/hita-community-plugins/issues/new/choose)

插件不随宿主 App 发版，通过市场安装与更新。

## 插件

| 插件 | 说明 | 宿主要求 |
|---|---|---|
| [番剧](anime-plugin/README.md) `com.stupidtree.hita.ui.anime` | Bangumi 追番、放送表、搜索、弹幕、评论与资源索引 | API 5+，Compose 1.6.8 |

番剧源码已迁到独立仓库
[kajimi16/hita-anime-plugin](https://github.com/kajimi16/hita-anime-plugin)，
本仓库保留构建产物与索引记录。

## 中央索引

宿主默认读取：

```
https://raw.githubusercontent.com/kajimi16/hita-community-plugins/master/registry/index.json
```

- 条目包含 id、version、url、sha256、size、minHostApi、author、tags、repo
- `url` 可以是绝对地址，也可以相对索引（如 `../dist/...`）
- 宿主校验 sha256 与 `apiVersion`/`minHostApi`；插件损坏或版本不兼容会拒绝安装
- 旧版本宿主仍可把 `OH-MY-HITA/market/index.json` 作为可替换源
- 用户在「插件市场 → 插件源」里也可以添加自定义索引

## 发布流程

作者只需要维护自己的插件仓库：

1. 用 [hita-plugin-template](https://github.com/kajimi16/hita-plugin-template) 创建工程
2. 开发和自测，推到 GitHub
3. 在本仓库提交「插件发布请求」Issue，填写 repo、module、kind、id、version、entryClass 等
4. 维护者运行 **Publish Plugin** 工作流
5. CI 构建、校验、写入 `dist/` 与 `registry/index.json`
6. 市场刷新后即可安装

不需要给宿主仓库提 PR。更新插件时提升 `version` 后重复发布；版本不变不会触发更新。

## CI 做什么

`Publish Plugin` 分为两个 job：

| job | 权限 | 行为 |
|---|---|---|
| `build` | `contents: read` | checkout 插件仓库（不持久化凭据），注入本仓库最新 AAR，构建 APK/dex |
| `publish` | `contents: write` | 下载产物、校验元数据、写 `dist/` 与索引、提交 |

界面插件发布前会解压校验 `assets/plugin.json` 的
`id` / `version` / `entryClass` / `apiVersion` 与表单一致。

产物当前托管在仓库 `dist/`，通过 raw 下载；后续可以替换为 S3/对象存储，只需让索引里的 `url` 指向最终地址。

## 本地构建

```bash
./gradlew publishAnime
# dist/anime-0.3.0.apk，并打印 sha256

./gradlew :anime-plugin:testReleaseUnitTest
```

`libs/` 中放 OH-MY-HITA 的 `api` / `model` / `component` AAR。工作流在构建外部插件时会用本仓库的 AAR 覆盖，保证契约版本一致。

## 仓库结构

```
anime-plugin/            番剧插件源码（历史保留）
dist/                    构建产物，按 id-version 命名
registry/index.json      中央索引
scripts/update_registry.py
.github/workflows/publish-plugin.yml
libs/                    API 5 契约 AAR
```

## 安全与限制

- 发布审核仍是**人工**：维护者运行工作流前确认仓库可信
- 索引没有签名；完整性依赖 HTTPS + sha256
- 插件与宿主同进程，拥有宿主权限，没有沙箱
- 界面插件不能注册组件、不能携带资源表
- `dist/` 放在 git 仓库里，版本多了会增大仓库体积；需要时再迁对象存储

## License

MIT
