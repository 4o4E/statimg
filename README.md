# statimg

用于离线渲染 GitHub 和 Wakatime 等开发者状态图片，适合 QQBot 等不支持 SVG 的场景。

图片渲染使用 Tavolo 2.9.1。

[![Release](https://img.shields.io/github/v/release/4o4E/statimg?label=Release)](https://github.com/4o4E/statimg/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/4o4E/statimg/total?label=Download)](https://github.com/4o4E/statimg/releases)
## http服务

`http-server`模块供了一个简单的http server用于根据需求生成皮肤渲染图

### 使用

1. 在 Linux x64 安装17或更高版本的 [Java](https://adoptium.net/)。
2. 从 [Release](https://github.com/4o4E/statimg/releases/latest) 下载 `statimg-<版本>.jar`。
3. 使用 `java -jar statimg-<版本>.jar` 启动服务；CI 同时发布 `ghcr.io/4o4e/statimg:<版本>` 和 `ghcr.io/4o4e/statimg:latest` 镜像。

[示例配置文件](http-server-linux/config.template.yml)

### api接口

**api不会主动缓存渲染结果, 如有需要请自行缓存**

#### 获取可用主题

url: `/wakatime/themes`

**示例请求**

```http request
GET http://localhost:2345/wakatime/themes

### ["default_repocard","transparent","shadow_red","shadow_green","shadow_blue","dark","radical","merko","gruvbox","gruvbox_light","tokyonight","onedark","cobalt","synthwave","highcontrast","dracula","prussian","monokai","vue","vue-dark","shades-of-purple","nightowl","buefy","blue-green","algolia","great-gatsby","darcula","bear","solarized-dark","solarized-light","chartreuse-dark","nord","gotham","material-palenight","graywhite","vision-friendly-dark","ayu-mirage","midnight-purple","calm","flag-india","omni","react","jolly","maroongold","yeblu","blueberry","slateorange","kacho_ga","outrun","ocean_dark","city_lights","github_dark","github_dark_dimmed","discord_old_blurple","aura_dark","panda","noctis_minimus","cobalt2","swift","aura","apprentice","moltack","codeSTACKr","rose_pine","date_night","one_dark_pro","rose","holi"]
```

#### 获取 GitHub 综合统计图

url: `/github/dashboard/{username}`

返回用户头像、公开仓库概览、全年贡献日历、评级、语言、五类贡献蛛网图、月度趋势、公开 PR 状态及仓库 Stars 的单张 PNG。只需传入用户名和可选 `theme`，沿用服务端现有 GitHub 令牌、主题与字体配置。

```http request
GET http://localhost:2345/github/dashboard/4o4E?theme=tokyonight
```

仓库及语言读取全部分页；热力图、总贡献数与月度趋势共用匿名读取的 GitHub 用户主页贡献日历，采用页面实际显示的滚动年度范围及 tooltip 精确每日次数，并遵循目标用户的公开贡献设置（包括其选择公开展示的受限贡献）。其余五类贡献计数、公开 PR／Issue、仓库及语言等数据仍来自各自原有 API 查询；不会因此取得或展示未授权的私有仓库明细。独立统计、主页日历和头像并发读取，游标分页保留依赖顺序，所有数据就绪后统一绘制。页面结构变化或数据无效时请求失败，不回退到 API 日历。具体范围和图片布局见 [GitHub HTTP API](docs/github-http-api.md#github-综合统计图)。旧独立统计卡与贡献图接口保持不变。

贡献蛛网图的零次贡献位于圆心；五类贡献全零时显示单个数据色圆心点，不生成正数内圈面积。正数对应 `1／10／100／1k／10k` 五层网格，旧 `c3d` 使用相同转换和圆心标记。综合图按网格、刻度与标签的完整内容自然测量尺寸，不缩放半径或字体；月度趋势只显示水平参考网格，保留坐标轴和数字月份。

人工预览：在 `run/config.yml` 准备现有渲染配置后，运行 `./gradlew :core:manualTest --tests 'top.e404.statimg.manual.ManualTestGithubRender.testRenderDashboard'`，输出为 `run/github_dashboard.png`。该任务会请求真实 GitHub API。

#### 获取 WakaTime 综合统计图

url: `/wakatime/stats/{username}`

统计使用 WakaTime 匿名公开接口，不需要配置 API 密钥。图中只显示目标账户公开的活动日历、语言、编辑器、操作系统、活动分类和 AI 模型统计；各项公开权限独立，未公开的项目不会显示，未公开时长时不推算时间。

图片使用两列瀑布流展示公开分组，每组按占比排序并展示前五项，其余汇总为 `Others`。整体宽度由标题、时长、热力图和双列统计组件所需的最大自然宽度决定，不设置固定最小宽度；热力图卡片与瀑布流共享总宽度，下方两列卡片等宽。标题包含 WakaTime 时间范围；`Total` 和 `Daily` 仅在相应时长数据公开时显示。

全年活动热力图位于 `Total`／`Daily` 下方并横跨整行，复用 GitHub 日历网格；其下为双列环形图。热力图网格与 `Less`／`More` 图例在卡片内水平居中，卡片标题保持左侧排列；卡片扩宽时不拉伸格子。日历读取首页使用的 `/api/v1/users/{username}/insights/days/public`，独立标注接口返回的范围（当前为 `last year`），不将分类统计的 `all time` 当作日历范围。颜色沿用首页的六档活动区间；公开分档值不累加为精确时长，缺失日期保持空白。仅日历或仅分类公开时均可生成图片，两者都没有可展示数据时返回公开配置提示状态。

外框与统计卡片使用半径 12 像素、线宽 2 像素的连续圆角边框，外框四角保持透明；布局尺寸包含边框占位，瀑布流列宽向上取整到整数像素。

| 请求参数 | 含义 | 默认值 |
|---|---|---|
| `theme` | 主题，参考配置文件 | 默认主题 |

接口返回 `403 wakatime_public_data_unavailable` 表示没有可展示的公开统计，`404 wakatime_user_not_found` 表示找不到用户。

**示例请求**

```http request
GET http://localhost:2345/wakatime/stats/404E?theme=tokyonight
```

## WakaTime 综合统计人工预览

在 `run/config.yml` 准备项目渲染配置（包含 `tokyonight` 主题），从仓库根目录运行：

```shell
./gradlew :core:manualTest --tests 'top.e404.statimg.manual.ManualTestWakaRender.testRenderStats'
```

Windows 使用 `gradlew.bat`。输出为 `run/waka_dashboard.png`；人工任务需要访问 WakaTime，普通测试不会触发该请求。正式 renderer 由 `/wakatime/stats/{username}` HTTP 接口调用。
