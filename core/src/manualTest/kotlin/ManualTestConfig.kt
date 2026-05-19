package top.e404.statimg.manual

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.decodeFromString
import top.e404.statimg.Config
import top.e404.statimg.feature.Heatmap2dRender
import top.e404.statimg.feature.Heatmap3dRender
import top.e404.statimg.platform.GithubRender
import top.e404.statimg.platform.WakatimeRender
import java.io.File

object ManualTestConfig {
    val yaml = Yaml(configuration = YamlConfiguration(strictMode = false, polymorphismStyle = PolymorphismStyle.Property))
    val config = yaml.decodeFromString<Config>(File("config.yml").readText())
    val wakatimeRender: WakatimeRender = WakatimeRender(config)
    val githubRender: GithubRender = GithubRender(config)
    val themes2d: Heatmap2dRender.Theme = config.themes2d["tokyonight"]!!
    val themes3d: Heatmap3dRender.Theme = config.themes3d["rainbow"]!!
}