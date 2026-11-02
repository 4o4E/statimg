# http-client 模块

`http-client` 提供基于 Ktor `HttpClient` 的类型化调用封装，只负责访问当前项目的 HTTP API，不依赖 `core`、`http-server` 或 Skiko 渲染运行时。

## 基本用法

```kotlin
repositories {
    mavenCentral()
    // 仅使用 -SNAPSHOT 版本时需要添加 Sonatype snapshot 仓库。
    maven("https://central.sonatype.com/repository/maven-snapshots/")
}

dependencies {
    implementation("top.e404.statimg:statimg-http-client:6.0.0")
}
```

```kotlin
import top.e404.statimg.client.StatimgClient
import top.e404.statimg.client.GithubStatsRankIcon
import top.e404.statimg.client.GithubStatsRequest

suspend fun main() {
    StatimgClient("http://localhost:2345").use { client ->
        val png = client.githubStats(
            GithubStatsRequest(
                username = "octocat",
                theme = "tokyonight",
                showIcons = true,
                rankIcon = GithubStatsRankIcon.PERCENTILE
            )
        )
    }
}
```

## 复用下游 HttpClient

传入外部 `HttpClient` 时，`StatimgClient.close()` 不会关闭该实例，生命周期由调用方管理。

SDK 自建客户端仅为 `githubDashboard(...)` 设置300秒请求总超时及读取超时，以容纳完整分页、其他统计数据及绘图；旧接口保留原有默认超时行为。复用下游 `HttpClient` 时完全沿用调用方的插件、连接、读取及请求总超时，不覆盖请求级设置；调用方应为综合图配置足够的等待时间。外部客户端生命周期由调用方管理。

```kotlin
import io.ktor.client.HttpClient
import io.ktor.http.Url
import top.e404.statimg.client.StatimgClient

suspend fun renderWithSharedClient(httpClient: HttpClient) {
    val client = StatimgClient(Url("http://localhost:2345"), httpClient)
    val png = client.githubRepo("octocat", "Hello-World", theme = "dark")
}
```

## 当前覆盖的接口

- `wakatimeThemes()` / `githubThemes()`：读取可用主题。
- `wakatimeStats(...)`：通过 `/wakatime/stats/{username}` 渲染 WakaTime 综合统计 PNG。
- `githubContribution(...)` / `githubContribution3d(...)`：渲染 GitHub 贡献图 PNG。
- `githubDashboard(...)`：通过 `/github/dashboard/{username}` 渲染 GitHub 综合统计 PNG。
- `githubStats(...)` / `githubTopLanguages(...)` / `githubRepo(...)`：渲染 GitHub 卡片 PNG。

需要服务端新增 query 参数时，可以优先通过各请求对象的 `extraQueryParameters` 透传，稳定后再补充类型化字段。

综合图使用独立请求对象，不复用独立评分卡的显示选项：

```kotlin
import top.e404.statimg.client.GithubDashboardRequest

val png = client.githubDashboard(
    GithubDashboardRequest(username = "4o4E", theme = "tokyonight")
)
// 也可直接调用 client.githubDashboard("4o4E", theme = "tokyonight")
```
