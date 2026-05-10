package top.e404.status.render.platform

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import top.e404.tavolo.draw.compose.*
import top.e404.tavolo.util.bytes
import top.e404.status.render.IConfig
import top.e404.status.render.feature.Heatmap2dRender
import kotlin.math.max

class WakatimeRender(val config: IConfig) {
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

    suspend fun renderLang(user: String, range: FetchRange, allLang: Boolean, theme: Heatmap2dRender.Theme): ByteArray {
        val stats = fetchUserStats(user, range)
        val statsList = stats["languages"]!!.jsonArray.map {
            StatsItem(it as JsonObject)
        }.let { statsList ->
            if (allLang) statsList
            else statsList.filter { it.duration != "0 secs" }
        }

        return renderStats("$user's wakatime stats in ${range.display}", statsList, theme)
    }

    suspend fun renderEditor(user: String, range: FetchRange, theme: Heatmap2dRender.Theme): ByteArray {
        val stats = fetchUserStats(user, range)
        val statsList = stats["editors"]!!.jsonArray.map {
            StatsItem(it as JsonObject)
        }

        return renderStats("$user's editor usage stats in ${range.display}", statsList, theme)
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
                                    .background(theme.textColor)
                                    .clip(Shape.RoundedRect(50f))
                            )
                            box(
                                Modifier
                                    .width(max(barWidth * lang.percent / 100, barHeight))
                                    .height(layout.barHeight)
                                    .background(theme.titleColor)
                                    .clip(Shape.RoundedRect(50f))
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
            duration = jo["text"]!!.jsonPrimitive.content
        )
    }

    private suspend fun fetchUserStats(user: String, range: FetchRange): JsonObject {
        val response = client.get("https://wakatime.com/api/v1/users/${user}/stats/${range.path}") {
            parameter("is_including_today", true)
            parameter("api_key", config.wakaToken)
        }
        if (response.status == HttpStatusCode.NotFound) error("unknown wakatime user, please register at wakatime.com")
        val jo = response.bodyAsText().let { Json.parseToJsonElement(it).jsonObject }
        if (jo["message"]?.jsonPrimitive?.content?.contains("Calculating") == true) {
            delay(500)
            return fetchUserStats(user, range)
        }
        val error = jo["error"]?.jsonPrimitive?.content
        if (error != null) throw Exception("unknown error $error")
        return jo["data"]!!.jsonObject
    }

    @Suppress("UNUSED")
    enum class FetchRange(val path: String, val display: String, regex: String) {
        LAST_7_DAYS("last_7_days", "last 7 days", "(?i)7d"),
        LAST_30_DAYS("last_30_days", "last 30 days", "(?i)30d"),
        LAST_6_MONTHS("last_6_months", "last 6 months", "(?i)6m"),
        LAST_YEAR("last_year", "last year", "(?i)year|1?y"),
        ALL_TIME("all_time", "all time", "(?i)all|a");

        val regex = Regex(regex)

        companion object {
            fun byName(name: String) = entries.firstOrNull { it.regex.matches(name) }
        }
    }
}
