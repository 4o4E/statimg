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

#### 获取wakatime 语言使用渲染图

url: `/wakatime/lang/{user}`

| url参数 | 含义          | 示例                            |
|-------|-------------|-------------------------------|
| user  | wakatime用户名 | `404E`                        |

WakaTime 统计使用匿名公开接口，不需要配置 WakaTime 密钥，时间范围由目标账户的公开设置决定。未公开时长时显示百分比。

接口返回 `403 wakatime_public_data_unavailable` 表示所需统计项目未公开，`404 wakatime_user_not_found` 表示找不到用户。

**可用参数**

| 请求参数  | 含义         | 默认值  | 可用值            |
|-------|------------|------|----------------|
| theme | 主题         | null | 参考config       |
| all   | 是否显示所有lang | null | `true`/`false` |

**示例请求**

```http request
GET http://localhost:2345/wakatime/lang/404E?theme=dark
```

#### 获取wakatime 编辑器使用渲染图

url: `/wakatime/editor/{user}`

| url参数 | 含义          | 示例                            |
|-------|-------------|-------------------------------|
| user  | wakatime用户名 | `404E`                        |

**可用参数**

| 请求参数  | 含义         | 默认值  | 可用值            |
|-------|------------|------|----------------|
| theme | 主题         | null | 参考config       |

**示例请求**

```http request
GET http://localhost:2345/wakatime/editor/404E?theme=dark
```

## WakaTime 综合统计人工预览

使用匿名公开接口读取 `404E` 的统计，按账户公开时间范围生成一张图片。公开的语言、编辑器、操作系统、活动分类和 AI 模型使用圆角环形图，以双列瀑布流排列；每组保留占比最高的五项，其余汇总为 `Others`，显示原始百分比。标题包含时间范围，`Total` 和 `Daily` 分别表示总时长与日均时长。

在 `run/config.yml` 准备项目现有渲染配置（包含 `tokyonight` 主题），从仓库根目录运行：

```shell
./gradlew :core:manualTest --tests 'top.e404.statimg.manual.WakaDashboardPrototype.renderPublicDashboard'
```

Windows 使用 `gradlew.bat`。输出为 `run/waka_dashboard.png`，人工任务需要访问 WakaTime；普通测试不会触发该请求。该入口用于图片预览，未接入 HTTP 服务。
