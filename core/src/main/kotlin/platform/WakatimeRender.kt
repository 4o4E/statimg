package top.e404.statimg.platform

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import top.e404.tavolo.draw.compose.*
import top.e404.tavolo.util.bytes
import top.e404.statimg.IConfig
import top.e404.statimg.feature.Heatmap2dRender
import kotlin.math.max

class WakatimePublicDataUnavailable : RuntimeException()
class WakatimeUserNotFound : RuntimeException()

class WakatimeRender(val config: IConfig) {
    private companion object {
        const val CALCULATING_RETRY_LIMIT = 6
        const val CALCULATING_RETRY_DELAY_MILLIS = 2_000L
    }

    private val layout inline get() = config.layout2d
    private val barHeight inline get() = layout.barHeight
    private val barWidth inline get() = layout.barWidth
    private val client inline get() = config.client

    private fun titleTextStyle(theme: Heatmap2dRender.Theme) = TextModifier.font(
        fontSize = layout.titleSize,
        textColor = theme.titleColor,
        fontFamily = layout.titleFontFamily
    )

    private fun langTextStyle(theme: Heatmap2dRender.Theme) = TextModifier.font(
        fontSize = layout.textSize,
        textColor = theme.textColor,
        fontFamily = layout.langFontFamily
    )

    private fun bodyTextStyle(theme: Heatmap2dRender.Theme) = TextModifier.font(
        fontSize = layout.textSize,
        textColor = theme.textColor,
        fontFamily = layout.textFontFamily
    )

    suspend fun renderLang(user: String, allLang: Boolean, theme: Heatmap2dRender.Theme): ByteArray {
        val stats = fetchUserStats(user)
        if (stats["is_language_usage_visible"]?.jsonPrimitive?.boolean == false || stats["languages"] == null) {
            throw WakatimePublicDataUnavailable()
        }
        val statsList = stats["languages"]!!.jsonArray.map {
            StatsItem(it as JsonObject)
        }.let { statsList ->
            if (allLang) statsList
            else statsList.filter { it.duration != "0 secs" }
        }

        return renderStats("$user's wakatime stats ${stats["human_readable_range"]!!.jsonPrimitive.content}", statsList, theme)
    }

    suspend fun renderEditor(user: String, theme: Heatmap2dRender.Theme): ByteArray {
        val stats = fetchUserStats(user)
        if (stats["is_editor_usage_visible"]?.jsonPrimitive?.boolean == false || stats["editors"] == null) {
            throw WakatimePublicDataUnavailable()
        }
        val statsList = stats["editors"]!!.jsonArray.map {
            StatsItem(it as JsonObject)
        }

        return renderStats("$user's editor usage stats ${stats["human_readable_range"]!!.jsonPrimitive.content}", statsList, theme)
    }

    private fun renderStats(title: String, statsList: List<StatsItem>, theme: Heatmap2dRender.Theme) = render {
        column(
            modifier = Modifier
                .clip(Shape.RoundedRect(layout.bgRadii))
                .background(theme.bgColor)
                .border(.5f, theme.bolderColor)
                .padding(layout.margin)
        ) {
            text(
                title,
                modifier = Modifier.padding(bottom = 20f),
                textModifier = titleTextStyle(theme)
            )
            statsList(statsList, theme)
        }
    }.bytes()

    @UiDsl
    private fun UiElement.statsList(statsList: List<StatsItem>, theme: Heatmap2dRender.Theme) {
        table(columnSpacing = 10f, rowSpacing = 5f) {
            for (lang in statsList) {
                tableRow {
                    cell(
                        horizontalAlignment = HorizontalAlignment.Left,
                        verticalAlignment = VerticalAlignment.Center,
                    ) {
                        text(
                            lang.name,
                            textModifier = langTextStyle(theme)
                        )
                    }
                    cell(
                        horizontalAlignment = HorizontalAlignment.Left,
                        verticalAlignment = VerticalAlignment.Center,
                    ) {
                        box(Modifier.width(layout.barWidth)) {
                            box(
                                Modifier
                                    .width(layout.barWidth)
                                    .height(layout.barHeight)
                                    .clip(Shape.RoundedRect(50f))
                                    .background(theme.textColor)
                            )
                            box(
                                Modifier
                                    .width(max(barWidth * lang.percent / 100, barHeight))
                                    .height(layout.barHeight)
                                    .clip(Shape.RoundedRect(50f))
                                    .background(theme.titleColor)
                            )
                        }
                    }
                    cell(
                        horizontalAlignment = HorizontalAlignment.Left,
                        verticalAlignment = VerticalAlignment.Center,
                    ) {
                        text(
                            lang.duration,
                            textModifier = bodyTextStyle(theme)
                        )
                    }
                }
            }
        }
    }

    private class StatsItem(
        val name: String,
        val percent: Float,
        val duration: String
    ) {
        constructor(jo: JsonObject) : this(
            name = jo["name"]!!.jsonPrimitive.content,
            percent = jo["percent"]!!.jsonPrimitive.float,
            duration = jo["text"]?.jsonPrimitive?.content ?: "${jo["percent"]!!.jsonPrimitive.content}%"
        )
    }

    private suspend fun fetchUserStats(user: String, retryCount: Int = 0): JsonObject {
        val response = client.get("https://wakatime.com/api/v1/users/${user}/stats")
        if (response.status == HttpStatusCode.NotFound) throw WakatimeUserNotFound()
        if (response.status == HttpStatusCode.Forbidden || response.status == HttpStatusCode.Unauthorized) {
            throw WakatimePublicDataUnavailable()
        }
        val jo = response.bodyAsText().let { Json.parseToJsonElement(it).jsonObject }
        val error = jo["error"]?.jsonPrimitive?.content
        if (error != null) error("WakaTime: $error")
        if (!response.status.isSuccess() && response.status != HttpStatusCode.Accepted) {
            error("WakaTime HTTP ${response.status.value}")
        }

        jo["data"]?.jsonObject?.let { return it }
        if (jo["message"]?.jsonPrimitive?.content?.contains("Calculating") == true && retryCount < CALCULATING_RETRY_LIMIT) {
            // Wakatime 可能先返回计算中状态，短暂等待后再拉取完整数据。
            delay(CALCULATING_RETRY_DELAY_MILLIS)
            return fetchUserStats(user, retryCount + 1)
        }
        error("wakatime response missing data: ${jo["message"]?.jsonPrimitive?.content ?: "unknown message"}")
    }

}
