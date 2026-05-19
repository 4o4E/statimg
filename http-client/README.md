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
    implementation("top.e404:github-readme-stats-render-http-client:1.3.2")
}
```

```kotlin
import top.e404.status.render.client.GithubReadmeStatsRenderClient
import top.e404.status.render.client.GithubStatsRankIcon
import top.e404.status.render.client.GithubStatsRequest

suspend fun main() {
    GithubReadmeStatsRenderClient("http://localhost:2345").use { client ->
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

传入外部 `HttpClient` 时，`GithubReadmeStatsRenderClient.close()` 不会关闭该实例，生命周期由调用方管理。

```kotlin
import io.ktor.client.HttpClient
import io.ktor.http.Url
import top.e404.status.render.client.GithubReadmeStatsRenderClient

suspend fun renderWithSharedClient(httpClient: HttpClient) {
    val client = GithubReadmeStatsRenderClient(Url("http://localhost:2345"), httpClient)
    val png = client.githubRepo("octocat", "Hello-World", theme = "dark")
}
```

## 当前覆盖的接口

- `wakatimeThemes()` / `githubThemes()`：读取可用主题。
- `wakatimeLanguage(...)` / `wakatimeEditor(...)`：渲染 Wakatime PNG。
- `githubContribution(...)` / `githubContribution3d(...)`：渲染 GitHub 贡献图 PNG。
- `githubStats(...)` / `githubTopLanguages(...)` / `githubRepo(...)`：渲染 GitHub 卡片 PNG。

需要服务端新增 query 参数时，可以优先通过各请求对象的 `extraQueryParameters` 透传，稳定后再补充类型化字段。
