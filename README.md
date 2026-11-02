# statimg

用于离线渲染 GitHub 和 Wakatime 等开发者状态图片，适合 QQBot 等不支持 SVG 的场景。

图片渲染使用 Tavolo 2.8.0。

[![Release](https://img.shields.io/github/v/release/4o4E/statimg?label=Release)](https://github.com/4o4E/statimg/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/4o4E/statimg/total?label=Download)](https://github.com/4o4E/statimg/releases)
## http服务

`http-server`模块供了一个简单的http server用于根据需求生成皮肤渲染图

### 使用

1. 安装17或更高版本的[java](https://adoptium.net/)
2. 从[release](https://github.com/4o4E/statimg/releases/latest)下载对应操作系统的jar文件
3. 在控制台中使用`java -jar http-server-${plateform}.jar`启动服务

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
