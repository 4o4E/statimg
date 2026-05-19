# statimg

用于离线渲染 GitHub 和 Wakatime 等开发者状态图片，适合 QQBot 等不支持 SVG 的场景。

[![Release](https://img.shields.io/github/v/release/4o4E/statimg?label=Release)](https://github.com/4o4E/statimg/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/4o4E/statimg/total?label=Download)](https://github.com/4o4E/statimg/releases)
## http服务

`http-server`模块供了一个简单的http server用于根据需求生成皮肤渲染图

### 使用

1. 安装11或更高版本的[java](https://adoptium.net/)
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

url: `/wakatime/lang/{user}/{range}`

| url参数 | 含义          | 示例                            |
|-------|-------------|-------------------------------|
| user  | wakatime用户名 | `404E`                        |
| range | 时间范围        | `7d`/`30d`/`6m`/`30d`/`y`/`y` |

**可用参数**

| 请求参数  | 含义         | 默认值  | 可用值            |
|-------|------------|------|----------------|
| theme | 主题         | null | 参考config       |
| all   | 是否显示所有lang | null | `true`/`false` |

**示例请求**

```http request
GET http://localhost:2345/wakatime/lang/404E/7d?theme=dark
```

#### 获取wakatime 编辑器使用渲染图

url: `/wakatime/editor/{user}/{range}`

| url参数 | 含义          | 示例                            |
|-------|-------------|-------------------------------|
| user  | wakatime用户名 | `404E`                        |
| range | 时间范围        | `7d`/`30d`/`6m`/`30d`/`y`/`y` |

**可用参数**

| 请求参数  | 含义         | 默认值  | 可用值            |
|-------|------------|------|----------------|
| theme | 主题         | null | 参考config       |

**示例请求**

```http request
GET http://localhost:2345/wakatime/editor/404E/7d?theme=dark
```
