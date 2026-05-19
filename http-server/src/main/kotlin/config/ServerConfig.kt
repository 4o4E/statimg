package top.e404.statimg.config

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import top.e404.statimg.*
import top.e404.statimg.feature.Heatmap2dRender
import top.e404.statimg.feature.Heatmap3dRender
import java.io.File

@Serializable
data class ServerConfig(
    val host: String = "localhost",
    val port: Int = 2345,
    @SerialName("proxy") override val proxyConfig: ProxyConfig? = null,
    @SerialName("waka_token") override val wakaToken: String,
    @SerialName("github_token") override val githubToken: String,
    override val layout2d: Heatmap2dRender.Layout,
    override val themes2d: Map<String, Heatmap2dRender.Theme>,
    override val layout3d: Heatmap3dRender.Layout,
    override val themes3d: Map<String, Heatmap3dRender.Theme>,
    override val github3d: Github3d,
): IConfig {
    override val client by lazy {
        HttpClient(OkHttp) {
            statimgHttpClientDefaults(proxyConfig)
        }
    }

    companion object {
        val yaml = Yaml(configuration = YamlConfiguration(strictMode = false, polymorphismStyle = PolymorphismStyle.Property))
        private val file = File("config.yml")
        lateinit var config: ServerConfig
            private set

        fun saveDefault(): String? {
            if (file.exists()) return null
            return this::class.java.classLoader
                .getResourceAsStream("config.yml")!!
                .bufferedReader()
                .use { it.readText() }
                .also { file.writeText(it) }
        }

        fun load() {
            val text = saveDefault() ?: file.readText()
            config = yaml.decodeFromString(text)
        }
    }
}
