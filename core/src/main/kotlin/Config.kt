package top.e404.status.render

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import top.e404.tavolo.util.FontManager
import top.e404.status.render.feature.Heatmap3dRender
import top.e404.status.render.feature.Heatmap2dRender
import java.net.InetSocketAddress
import java.net.Proxy

interface IConfig {
    val proxyConfig: ProxyConfig?
    val wakaToken: String
    val githubToken: String
    val layout2d: Heatmap2dRender.Layout
    val themes2d: Map<String, Heatmap2dRender.Theme>
    val layout3d: Heatmap3dRender.Layout
    val themes3d: Map<String, Heatmap3dRender.Theme>
    val github3d: Github3d

    val client: HttpClient
}

@Serializable
data class Github3d(
    val font: FontConfig = FontConfig(),
    val icon: IconConfig = IconConfig()
) {
    @Serializable
    data class FontConfig(
        val normal: String = "font/JetBrainsMono-Medium.ttf",
        val bold: String = "font/JetBrainsMono-Bold.ttf",
    ) {
        val normalFontFamily by lazy { FontManager.registerFile("github3d-normal", FontFileResolver.resolve(normal)) }
        val boldFontFamily by lazy { FontManager.registerFile("github3d-bold", FontFileResolver.resolve(bold)) }
    }

    @Serializable
    data class IconConfig(
        val star: String = """<svg aria-hidden="true" height="16" viewBox="0 0 16 16" version="1.1" width="16" data-view-component="true" class="octicon octicon-star d-inline-block mr-2"><path d="M8 .25a.75.75 0 0 1 .673.418l1.882 3.815 4.21.612a.75.75 0 0 1 .416 1.279l-3.046 2.97.719 4.192a.751.751 0 0 1-1.088.791L8 12.347l-3.766 1.98a.75.75 0 0 1-1.088-.79l.72-4.194L.818 6.374a.75.75 0 0 1 .416-1.28l4.21-.611L7.327.668A.75.75 0 0 1 8 .25Zm0 2.445L6.615 5.5a.75.75 0 0 1-.564.41l-3.097.45 2.24 2.184a.75.75 0 0 1 .216.664l-.528 3.084 2.769-1.456a.75.75 0 0 1 .698 0l2.77 1.456-.53-3.084a.75.75 0 0 1 .216-.664l2.24-2.183-3.096-.45a.75.75 0 0 1-.564-.41L8 2.694Z"></path></svg>""",
        val fork: String = """<svg aria-hidden="true" height="16" viewBox="0 0 16 16" version="1.1" width="16" data-view-component="true" class="octicon octicon-repo-forked mr-2"><path d="M5 5.372v.878c0 .414.336.75.75.75h4.5a.75.75 0 0 0 .75-.75v-.878a2.25 2.25 0 1 1 1.5 0v.878a2.25 2.25 0 0 1-2.25 2.25h-1.5v2.128a2.251 2.251 0 1 1-1.5 0V8.5h-1.5A2.25 2.25 0 0 1 3.5 6.25v-.878a2.25 2.25 0 1 1 1.5 0ZM5 3.25a.75.75 0 1 0-1.5 0 .75.75 0 0 0 1.5 0Zm6.75.75a.75.75 0 1 0 0-1.5.75.75 0 0 0 0 1.5Zm-3 8.75a.75.75 0 1 0-1.5 0 .75.75 0 0 0 1.5 0Z"></path></svg>""",
    )
}

@Serializable
open class Config : IConfig {
    @SerialName("proxy")
    override val proxyConfig: ProxyConfig? = null

    @SerialName("waka_token")
    override val wakaToken: String = "waka_xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
    @SerialName("github_token")
    override val githubToken: String = "ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
    override val layout2d: Heatmap2dRender.Layout = Heatmap2dRender.Layout()
    override val themes2d: Map<String, Heatmap2dRender.Theme> = emptyMap()
    override val layout3d: Heatmap3dRender.Layout = Heatmap3dRender.Layout()
    override val themes3d: Map<String, Heatmap3dRender.Theme> = emptyMap()
    override val github3d: Github3d = Github3d()

    override val client by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    followRedirects(true)
                }
                proxyConfig?.let {
                    proxy = it.toProxy()
                }
            }
        }
    }
}

@Serializable
data class ProxyConfig(
    val type: String,
    val host: String = "localhost",
    val port: Int = 7890
) {
    fun toProxy() = Proxy(Proxy.Type.valueOf(type.uppercase()), InetSocketAddress(host, port))
}
