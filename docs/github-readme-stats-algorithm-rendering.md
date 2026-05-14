# github-readme-stats 评分与卡片渲染逻辑调研

本文档用于后续在本项目中实现 `anuraghazra/github-readme-stats` 的 GitHub 评分、统计卡片、仓库卡片和常用语言卡片能力。调研对象为上游仓库 `anuraghazra/github-readme-stats` 的 `main` 分支快照：

- 上游仓库：https://github.com/anuraghazra/github-readme-stats
- 调研提交：`5df91f9bfa89c356a55cbb3c2bbc164fdbf94a86`
- 本项目现状：已有 `GithubFetcher`、贡献热力图 2D/3D PNG 渲染和 GitHub GraphQL Token 配置，尚未实现原项目的 stats card、repo card、top languages card。

## 1. 本项目现有基础

相关文件：

- `core/src/main/kotlin/fetcher/GithubFetcher.kt`
- `core/src/main/kotlin/fetcher/GhTypes.kt`
- `core/src/main/kotlin/platform/GithubRender.kt`
- `http-server/src/main/kotlin/plugin/routing.kt`

当前已有能力：

- 通过 GitHub GraphQL 查询贡献日历，用于 `/github/contribution/{username}/{end}` 的 2D 热力图。
- 通过 GitHub GraphQL 查询贡献日历、贡献类型总数、按仓库提交语言、仓库 star/fork，用于 `/github/contribution3d/{username}` 的 3D 热力图和附加信息。
- 渲染输出是 Skia/Tavolo 生成的 PNG，不是上游原项目直接拼接的 SVG 字符串。

后续实现上游卡片逻辑时，可以继续保留 PNG 输出，也可以新增 SVG 输出层；但算法层建议先做成独立数据模型和纯函数，避免和具体渲染后端绑定。

## 2. 上游模块结构

上游核心代码分布：

- `src/fetchers/stats.js`：GitHub 用户统计数据获取。
- `src/calculateRank.js`：GitHub 用户等级评分算法。
- `src/cards/stats.js`：统计卡 SVG 渲染。
- `src/fetchers/top-languages.js`：常用语言数据获取和加权排序。
- `src/cards/top-languages.js`：常用语言卡 SVG 渲染，含 normal、compact、donut、donut-vertical、pie 布局。
- `src/fetchers/repo.js`：仓库卡数据获取。
- `src/cards/repo.js`：仓库卡 SVG 渲染。
- `src/common/Card.js`：通用 SVG 卡片外壳、标题、边框、背景、动画和可访问性标签。
- `src/common/render.js`：简易布局、进度条、图标文本组合、文本宽度估算、错误卡渲染。
- `src/common/color.js` 与 `themes/index.js`：主题和颜色覆盖逻辑。

## 3. GitHub 统计数据获取

上游 `fetchStats(username, ...)` 同时使用 GraphQL 和 REST：

GraphQL 主查询字段：

- `user.name`、`user.login`
- `contributionsCollection(from: $startTime).totalCommitContributions`
- `contributionsCollection.totalPullRequestReviewContributions`
- `repositoriesContributedTo(...).totalCount`
- `pullRequests.totalCount`
- 可选：`pullRequests(states: MERGED).totalCount`
- `issues(states: OPEN).totalCount + issues(states: CLOSED).totalCount`
- `followers.totalCount`
- 可选：`repositoryDiscussions.totalCount`
- 可选：`repositoryDiscussionComments(onlyAnswers: true).totalCount`
- `repositories(first: 100, ownerAffiliations: OWNER, orderBy: STARGAZERS)`，用于累计 owned repo stars。

提交数有三种语义：

- 默认：GraphQL `totalCommitContributions`，上游标题显示为 `Total Commits (Last Year)`。
- `include_all_commits=true`：调用 REST `GET /search/commits?q=author:{username}`，用 `total_count` 近似全量提交数。
- `commits_year=YYYY`：GraphQL `contributionsCollection(from: YYYY-01-01T00:00:00Z)`，显示指定年份标签。

Stars 统计：

- 只统计用户 owner 仓库。
- 默认一次取 100 个仓库，按 star 降序。
- 如果环境变量 `FETCH_MULTI_PAGE_STARS=true`，并且当前页仓库全部有 star，才继续翻页；这是为了避免公共部署消耗太多 rate limit。
- `exclude_repo` 和环境变量中的排除仓库会从 stars 求和中剔除。

建议迁移到本项目时新增数据模型：

```kotlin
data class GithubStats(
    val name: String,
    val totalPRs: Int,
    val totalPRsMerged: Int,
    val mergedPRsPercentage: Double,
    val totalReviews: Int,
    val totalCommits: Int,
    val totalIssues: Int,
    val totalStars: Int,
    val totalDiscussionsStarted: Int,
    val totalDiscussionsAnswered: Int,
    val contributedTo: Int,
    val followers: Int,
    val rank: GithubRank
)

data class GithubRank(
    val level: String,
    val percentile: Double
)
```

## 4. GitHub 评分算法

上游评分函数在 `src/calculateRank.js`，输入为 commits、PR、issues、reviews、stars、followers 等指标。`repos` 参数目前传入但未参与计算。

### 4.1 分布函数

```text
exponential_cdf(x) = 1 - 2 ^ (-x)
log_normal_cdf(x) = x / (1 + x)
```

上游并未使用真正的 log-normal CDF，`log_normal_cdf` 实际是一个简单饱和函数。

### 4.2 中位数和权重

| 指标 | median | weight | 分布函数 |
| --- | ---: | ---: | --- |
| commits | `include_all_commits ? 1000 : 250` | 2 | exponential |
| PRs | 50 | 3 | exponential |
| issues | 25 | 1 | exponential |
| reviews | 2 | 1 | exponential |
| stars | 50 | 4 | `x / (1 + x)` |
| followers | 10 | 1 | `x / (1 + x)` |

总权重为 `12`。

### 4.3 percentile 计算

```text
score =
  (
    2 * exponential_cdf(commits / commitMedian) +
    3 * exponential_cdf(prs / 50) +
    1 * exponential_cdf(issues / 25) +
    1 * exponential_cdf(reviews / 2) +
    4 * log_normal_cdf(stars / 50) +
    1 * log_normal_cdf(followers / 10)
  ) / 12

rankPercentile = (1 - score) * 100
```

percentile 越小，等级越高。上游渲染环形进度时使用：

```text
progress = 100 - rankPercentile
```

### 4.4 等级阈值

上游用 `rankPercentile <= threshold` 选择第一个等级：

| percentile 范围 | level |
| --- | --- |
| `<= 1` | S |
| `<= 12.5` | A+ |
| `<= 25` | A |
| `<= 37.5` | A- |
| `<= 50` | B+ |
| `<= 62.5` | B |
| `<= 75` | B- |
| `<= 87.5` | C+ |
| `<= 100` | C |

上游测试样例：

- 全 0：`C / 100`
- commits=250、PRs=50、issues=25、reviews=10、stars=50、followers=10：`B+ / 46.09375`
- commits=1300、PRs=1500、issues=4500、reviews=1000、stars=600000、followers=50000：`S / 0.4578556547153667`

建议 Kotlin 实现为纯函数，例如 `GithubRankCalculator.calculate(...)`，并直接移植上游测试用例，避免浮点细节偏移。

## 5. Stats Card 渲染逻辑

上游 `renderStatsCard(stats, options)` 使用通用 `Card` 外壳并拼接 SVG。渲染结构：

- 标题：默认 `{name}'s GitHub Stats`，也支持 `custom_title`。
- 指标列表：stars、commits、PRs、issues、contributedTo 是基础项。
- 可选指标：merged PR、merged PR percentage、reviews、discussions started、discussions answered。
- 右侧等级圆环：显示等级、GitHub 图标或 percentile。

主要参数：

- `hide`：隐藏某些统计项。
- `show_icons`：指标左侧显示 Octicon 图标。
- `hide_rank`：隐藏等级圆环。
- `hide_title`、`hide_border`
- `card_width`、`line_height`
- `theme` 和颜色覆盖：`title_color`、`ring_color`、`icon_color`、`text_color`、`bg_color`、`border_color`
- `number_format=short|long`、`number_precision=0..2`
- `rank_icon=default|github|percentile`
- `disable_animations`

尺寸逻辑：

- 无 rank 默认宽度 `287`。
- 有 stats + rank 默认宽度 `450`，最小宽度 `420`。
- rank-only 默认和最小宽度 `290`。
- 高度为 `max(45 + (statItems + 1) * lineHeight, rankVisible ? 150/180 : 0)`。

等级圆环：

- 圆半径 `40`，圆周近似 stroke-dasharray `250`。
- `calculateCircleProgress(value) = ((100 - clamp(value, 0, 100)) / 100) * 2πr`
- CSS 动画从空进度过渡到 `progress = 100 - percentile`。

迁移到 PNG/Skia 时的建议：

- 复用算法、数据项选择、尺寸规则。
- 将 SVG `flexLayout` 改成 Kotlin 的布局函数或 Tavolo `row/column`。
- 圆环用 Skia `drawCircle` + `drawArc` 实现，弧度从 -90 度开始。
- 数字格式化、隐藏项、可选项应先作为纯逻辑测试。

## 6. Top Languages 数据算法

上游 `fetchTopLanguages(username, exclude_repo, size_weight, count_weight)`：

GraphQL：

- `repositories(ownerAffiliations: OWNER, isFork: false, first: 100)`
- 每个仓库取 `languages(first: 10, orderBy: { field: SIZE, direction: DESC })`
- 每条语言边包含 `size` 和 `node.name/color`

数据处理流程：

1. 合并请求参数 `exclude_repo` 和环境变量排除仓库。
2. 过滤被排除仓库。
3. 忽略没有语言数据的仓库。
4. 把所有仓库的语言边扁平化。
5. 按语言名聚合：
   - `size` 累加该语言在所有仓库中的字节数。
   - `count` 表示该语言出现过的仓库数。
6. 用权重重算排序值：

```text
weightedSize = size ^ size_weight * count ^ count_weight
```

默认 `size_weight=1`、`count_weight=0`，即按代码字节数排序。若 `size_weight=0`、`count_weight=1`，则按出现仓库数排序。

注意：上游只取 owner 非 fork 的前 100 个仓库，且每个仓库只取前 10 种语言；这不是账号所有代码的完整语言统计。

## 7. Top Languages Card 渲染逻辑

上游支持 5 种布局：

- `normal`：每种语言一行，名称、百分比或字节数、进度条。
- `compact`：顶部堆叠进度条，下方两列语言图例。
- `donut`：左侧图例，右侧圆环图。
- `donut-vertical`：上方圆环，下方两列图例。
- `pie`：上方饼图，下方两列图例。

语言数量默认值：

| layout | 默认数量 |
| --- | ---: |
| normal | 5 |
| compact | 6 |
| donut | 5 |
| donut-vertical | 6 |
| pie | 6 |

最大 `langs_count` 为 `20`，最小为 `1`。渲染前会先按排序值降序，再按 `hide` 参数过滤语言，然后截断。

百分比计算：

```text
displayPercent = lang.size / sum(selectedLang.size) * 100
```

这里的分母是被选中展示的语言总量，不是用户所有语言总量。因此当只显示前 5 种语言时，百分比之和仍为 100%。

布局尺寸：

- 基础宽度默认 `300`，最小 `280`。
- `normal` 高度：`45 + (langs + 1) * 40`
- `compact` 高度：`90 + round(langs / 2) * 25`，若 `hide_progress=true` 再减 `25`
- `donut` 高度：`215 + max(langs - 5, 0) * 32`，宽度额外加 `50`
- `donut-vertical` 高度：`300 + round(langs / 2) * 25`
- `pie` 高度：`300 + round(langs / 2) * 25`

图形算法：

- compact 堆叠条：每个语言宽度为 `lang.size / total * (width - 50)`，太小的块视觉宽度加 10，但下一个块的 offset 仍按真实百分比推进。
- normal 进度条：每个语言单独进度条，最小显示宽度 clamp 到 2%。
- donut：把百分比转角度，使用极坐标转笛卡尔坐标生成圆弧路径；PNG 可用 `drawArc` 替代。
- pie：每片角度为 `lang.size / total * 360`，路径从圆心连到弧线两端；PNG 可直接画扇形。

## 8. Repo Card 数据与渲染逻辑

上游 `fetchRepo(username, reponame)` 同时查询 user 和 organization：

- `name`
- `nameWithOwner`
- `isPrivate`
- `isArchived`
- `isTemplate`
- `stargazers.totalCount`
- `description`
- `primaryLanguage.name/color`
- `forkCount`

渲染：

- 固定宽度 `400`。
- 标题默认仓库名，`show_owner=true` 时用 `owner/repo`。
- 标题超过 35 字符截断为 `...`。
- 描述最多 3 行，默认没有描述时显示 `No description provided`。
- 语言、star、fork 位于底部一行。
- archived/template 显示 badge。
- 高度约为 `110/120 + descriptionLines * 10`。

迁移时要注意：

- private 或不存在仓库要返回错误。
- organization repo 也要支持。
- 描述中的 emoji 和 HTML 转义，上游有 `parseEmojis` 和 `encodeHTML`；PNG 渲染不需要 HTML 转义，但需要考虑 emoji 字体缺失。

## 9. 主题与颜色规则

上游颜色处理规则：

1. 先选择 `theme`，若未传则使用默认主题。
2. 每个颜色字段允许请求参数覆盖主题字段。
3. hex 支持 3、4、6、8 位。
4. `bg_color` 支持渐变格式：`angle,color1,color2,...`，例如 `45,fff,000`。
5. `ring_color` 默认跟随 `titleColor`。
6. `border_color` 如果主题没有提供，则回退到默认主题。

本项目当前 2D/3D 热力图主题与上游卡片主题不是一套模型。建议新增 `GithubCardTheme`，不要强行复用 `Heatmap2dRender.Theme`：

```kotlin
data class GithubCardTheme(
    val titleColor: Int,
    val textColor: Int,
    val iconColor: Int,
    val ringColor: Int,
    val background: CardBackground,
    val borderColor: Int,
    val borderRadius: Float = 4.5f
)
```

`CardBackground` 可先只支持纯色，渐变后续再加。

## 10. HTTP API 建议

已实现接口的使用说明见 [`github-http-api.md`](github-http-api.md)。本节保留为迁移阶段的接口设计记录。

为了贴近上游接口，同时符合本项目已有 `/github/...` 风格，可以新增：

- `GET /github/stats/{username}`
- `GET /github/top-langs/{username}`
- `GET /github/repo/{owner}/{repo}`

建议先支持的 query 参数：

Stats：

- `theme`
- `hide`
- `show_icons`
- `hide_rank`
- `include_all_commits`
- `commits_year`
- `exclude_repo`
- `show`
- `rank_icon`
- `number_format`
- `number_precision`

Top languages：

- `theme`
- `layout`
- `langs_count`
- `hide`
- `hide_progress`
- `exclude_repo`
- `size_weight`
- `count_weight`
- `stats_format`

Repo：

- `theme`
- `show_owner`
- `description_lines_count`

## 11. 推荐实现顺序

1. 新增纯算法层：
   - `GithubRankCalculator`
   - `GithubNumberFormatter`
   - `GithubLanguageAggregator`
2. 扩展 `GithubFetcher`：
   - `fetchStats`
   - `fetchTopLanguages`
   - `fetchRepo`
   - REST Search Commits 支持 `include_all_commits`
3. 添加单元测试：
   - 直接移植上游 `calculateRank.test.js` 关键用例。
   - 语言聚合测试覆盖 `size_weight/count_weight`、`exclude_repo`、`hide` 和 `langs_count`。
4. 实现通用卡片渲染外壳：
   - 背景、边框、标题、padding、主题。
   - 先输出 PNG，后续如需要再加 SVG backend。
5. 实现 stats card。
6. 实现 repo card。
7. 实现 top languages card 的 `normal` 和 `compact`。
8. 再实现 `donut`、`donut-vertical`、`pie`。

## 12. 风险点

- 上游 stats 的全量提交数依赖 REST Search API，rate limit 和结果准确性都不同于 GraphQL `contributionsCollection`。
- GitHub GraphQL 的 `totalCommitContributions` 是贡献日历语义，默认近一年，不等于账号历史总提交。
- top languages 的统计只覆盖 owner 非 fork 前 100 仓库和每仓库前 10 语言。
- 上游 SVG 使用 CSS 动画，本项目 PNG 静态输出时需要舍弃或转为固定最终状态。
- 字体测量差异会导致布局宽度和换行与 SVG 版本不同；需要以视觉稳定为目标，而不是逐像素一致。
- README、接口文档和错误处理需要统一字符编码，当前控制台读取 README 时出现乱码，后续编辑文档应确认 UTF-8。
