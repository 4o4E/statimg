# GitHub Card HTTP API

All endpoints return PNG images unless noted otherwise. The `theme` query parameter uses keys from `themes2d` in `config.yml`.

## Themes

```http
GET /github/themes
```

Returns the available 2D theme names as JSON.

## GitHub 综合统计图

```http
GET /github/dashboard/{username}
```

参数仅使用 `theme`，选择 `themes2d` 中的主题；默认采用内置主题。返回包含圆形头像、概览指标、全年贡献日历、评级统计、语言分布、贡献类型、月度趋势、PR 状态和仓库 Stars 的单张 PNG。原有 `/github/stats`、`/github/top-langs`、`/github/repo` 及贡献图接口保持独立。

统计范围：

- 仓库、Stars、Forks：用户拥有的全部公开仓库，包含 Fork；仓库连接读取全部分页。
- 语言：公开非 Fork 仓库中的语言字节量，语言连接读取全部分页。使用 KB／MB／GB 单位，前五项以外合并为 `Others`。
- 日历、年度 Commits 和五类贡献：同一请求时间向前 365 天的 GitHub `contributionsCollection`。日历遵循 GitHub 返回的贡献汇总；蛛网图固定展示 Commit、Issue、PR、Review、Repo，不添加受限贡献类别，零值类别仍保留坐标轴。
- PR 状态与累计 Issue：作者为该用户的公开条目。Merged、Open、Closed（未合并）互不重叠，PR 总数为三类之和。Search API 返回不完整结果时请求失败，不输出部分统计。
- `Contributed to`：GitHub 返回的近期参与公开仓库数量，并非历史累计仓库数量。
- 评级复用既有统计卡算法，使用年度提交／Review、累计公开 PR／Issue、Stars 和关注者数量；不是 GitHub 官方评级。

右上角日期为生成当天日期（Asia/Taipei），格式 `yyyy.MM.dd`，不表示全部卡片采用同一累计区间。图中不重复显示时间范围。头像使用 GitHub 公开头像地址，GitHub API 仍使用服务端现有 `github_token` 配置，调用方无需传入令牌。
Dashboard 数据获取完成后才会开始统一绘制。相互独立的 GitHub 查询会并发执行；仓库列表分页及各仓库语言分页因游标依赖，仍按顺序读取完整分页。`http-client` SDK 自建客户端仅为此接口设置300秒请求及读取超时，外部客户端完全沿用调用方设置。


布局以日历网格所需自然宽度为基准，下面使用双列瀑布流；环形图在左侧、数值图例在右侧。仓库 Stars 保留前五项，其余合并为 `Others`；条形使用卡片剩余宽度，不设置固定最大值，长仓库名称使用小字号。旧主题及字体配置继续生效。

默认图片样式：

- 圆形头像覆盖名字及概览指标区域；名字36字号，用户ID在右侧，顶部图标／标签与正文均为20字号。卡片标题沿用26字号及现有标题字体。
- 外框和卡片圆角半径12像素、边框2像素；图片两侧与瀑布流中间间隔均为16像素。Stats的Total标签、Contributed to与评级圆环保留，图例数值按列靠右。
- 日历只标注Mon／Wed／Fri，Less–More靠左。贡献蛛网保留c3d的五轴与对数刻度，按底部标签的实际位置确定高度；月度横坐标仅显示数字月份，零活动时使用0–1纵轴。
- 环形图半径65像素、内半径36像素，采用圆角扇区与分隔间隙。长语言名称在图例区域换行，长显示名在保留用户ID及日期位置后使用单行省略，不扩大日历。

```http
GET /github/dashboard/4o4E?theme=tokyonight
```

## Stats Card

```http
GET /github/stats/{username}
```

Query parameters:

| Parameter | Description |
| --- | --- |
| `theme` | 2D theme name. Defaults to the built-in default theme. |
| `hide` | Comma-separated stat ids to hide, for example `issues,contribs`. |
| `show` | Comma-separated optional stat ids. Supported values: `reviews`, `prs_merged`, `prs_merged_percentage`, `discussions_started`, `discussions_answered`. |
| `show_icons` | `true` to render stat icons. |
| `hide_rank` | `true` to hide the rank badge. |
| `include_all_commits` | `true` to include all commits through GitHub Search API. |
| `commits_year` | Year used by the all-commits search query. |
| `exclude_repo` | Comma-separated repositories excluded from owned-star totals and all-commit search. |
| `rank_icon` | `default` or `percentile`. |
| `number_format` | `short` or `long`. |
| `number_precision` | Decimal precision for formatted numbers. |
| `custom_title` | Replaces the default card title. |

Example:

```http
GET /github/stats/octocat?theme=tokyonight&show_icons=true&show=reviews,prs_merged&rank_icon=percentile
```

## Top Languages Card

```http
GET /github/top-langs/{username}
```

Query parameters:

| Parameter | Description |
| --- | --- |
| `theme` | 2D theme name. |
| `layout` | `normal`, `compact`, `donut`, `donut-vertical`, `donut_vertical`, or `pie`. |
| `langs_count` | Maximum language count, clamped by renderer limits. |
| `hide` | Comma-separated language names to hide. |
| `hide_progress` | `true` to hide progress bars. For `normal`, this renders the compact legend-only layout. |
| `exclude_repo` | Comma-separated repositories excluded from language aggregation. |
| `size_weight` | Repository language size weight. Defaults to `1.0`. |
| `count_weight` | Repository language occurrence count weight. Defaults to `0.0`. |
| `stats_format` | `percentages` or `bytes`. |
| `custom_title` | Replaces the default card title. |

Example:

```http
GET /github/top-langs/octocat?layout=donut-vertical&langs_count=8&hide=html,css
```

## Repository Card

```http
GET /github/repo/{owner}/{repo}
```

Query parameters:

| Parameter | Description |
| --- | --- |
| `theme` | 2D theme name. |
| `show_owner` | `true` to render `owner/repo` as the title. |
| `description_lines_count` | Description line count, clamped to `1..3`. |

Example:

```http
GET /github/repo/octocat/Hello-World?show_owner=true&description_lines_count=2
```

## Notes

- Boolean parameters are enabled only by the literal value `true`, case-insensitive.
- Comma-separated parameters trim whitespace and ignore empty entries.
- Invalid enum values fall back to the default option for that endpoint.
- Request failures are returned as `400 Bad Request` with a plain text message.
