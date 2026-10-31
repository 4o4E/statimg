package top.e404.statimg.manual

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.e404.tavolo.draw.compose.*
import top.e404.tavolo.draw.compose.charts.*
import top.e404.tavolo.util.bytes
import java.io.File

/** 人工预览：仅绘制 WakaTime 匿名接口公开的统计，时间范围沿用账户公开设置。 */
class WakaDashboardPrototype {
    @org.junit.jupiter.api.Test
    fun renderPublicDashboard() = runBlocking(Dispatchers.IO) {
        val config = ManualTestConfig.config
        val theme = ManualTestConfig.themes2d
        val stats = fetchPublicStats(config)

        val sections = listOf(
            PublicSection("LANG", "Languages", "is_language_usage_visible", "languages"),
            PublicSection("IDE", "Editors", "is_editor_usage_visible", "editors"),
            PublicSection("OS", "Operating Systems", "is_os_usage_visible", "operating_systems"),
            PublicSection("CAT", "Categories", "is_category_usage_visible", "categories"),
            PublicSection("AI", "AI Models", "is_ai_model_usage_visible", "ai_models")
        ).mapNotNull { section ->
            val isPublic = stats[section.visibilityKey]?.jsonPrimitive?.content == "true"
            val values = (stats[section.dataKey] as? JsonArray)?.mapNotNull { item ->
                val value = item as? JsonObject ?: return@mapNotNull null
                val name = value["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val percent = value["percent"]?.jsonPrimitive?.content?.toFloatOrNull() ?: return@mapNotNull null
                PublicStat(name, percent)
            }.orEmpty()
            if (isPublic && values.isNotEmpty()) section to values.sortedByDescending { it.percent } else null
        }

        check(sections.isNotEmpty()) { "404E has no visible WakaTime breakdowns in the public stats response" }

        val layout = config.layout2d
        val titleStyle = TextModifier.font(fontSize = layout.titleSize, textColor = theme.titleColor, fontFamily = layout.titleFontFamily)
        val labelStyle = TextModifier.font(fontSize = layout.langSize, textColor = theme.titleColor, fontFamily = layout.langFontFamily)
        val bodyStyle = TextModifier.font(fontSize = layout.textSize, textColor = theme.textColor, fontFamily = layout.textFontFamily)
        val colors = listOf(0xff7aa2f7.toInt(), 0xffbb9af7.toInt(), 0xff2ac3b3.toInt(), 0xffff9e64.toInt(), 0xfff7768e.toInt(), 0xff565f89.toInt())
        val cardWidth = 500f
        val chartTheme = PieChartTheme(
            width = 140f,
            height = 140f,
            radius = 65f,
            innerRadius = 36f,
            insets = ChartInsets(5f, 5f, 5f, 5f),
            cornerRadius = 4f,
            sliceGap = 3f,
            strokeWidth = 0f,
            showLabels = false,
            legend = ChartLegendTheme(position = ChartLegendPosition.NONE)
        )

        @UiDsl
        fun UiElement.card(section: PublicSection, values: List<PublicStat>) {
            val shown = values.take(5)
            val remainder = values.drop(5).sumOf { it.percent.toDouble() }.toFloat()
            val chartSlices = shown.mapIndexed { index, stat -> PieSlice(stat.name, stat.percent, colors[index]) } +
                if (remainder > 0f) listOf(PieSlice("Others", remainder, colors[5])) else emptyList()
            column(
                Modifier.width(cardWidth)
                    .clip(Shape.RoundedRect(12f))
                    .border(1f, theme.bolderColor, shape = Shape.RoundedRect(12f)).padding(16f)
            ) {
                row(verticalAlignment = VerticalAlignment.Center) {
                    box(Modifier.size(26f).clip(Shape.RoundedRect(6f)).background(theme.iconColor), HorizontalAlignment.Center, VerticalAlignment.Center) {
                        icon(IconTheme(20f, color = theme.bgColor), sectionIcon(section.marker))
                    }
                    text(section.title, modifier = Modifier.padding(left = 8f), textModifier = labelStyle)
                }
                row(Modifier.padding(top = 14f), verticalAlignment = VerticalAlignment.Center) {
                    donutChart(chartTheme, chartSlices)
                    column(Modifier.padding(left = 14f)) {
                        shown.forEachIndexed { index, stat ->
                            row(Modifier.padding(bottom = 8f), verticalAlignment = VerticalAlignment.Center) {
                                box(Modifier.size(10f).clip(Shape.RoundedRect(5f)).background(colors[index]))
                                text("${stat.name}  ${percentage(stat.percent)}", modifier = Modifier.padding(left = 7f), textModifier = bodyStyle)
                            }
                        }
                        if (remainder > 0f) {
                            row(verticalAlignment = VerticalAlignment.Center) {
                                box(Modifier.size(10f).clip(Shape.RoundedRect(5f)).background(colors[5]))
                                text("Others  ${percentage(remainder)}", modifier = Modifier.padding(left = 7f), textModifier = bodyStyle)
                            }
                        }
                    }
                }
            }
        }

        val image = render {
            column(
                Modifier.width(cardWidth * 2 + layout.margin * 2 + 24f)
                    .clip(Shape.RoundedRect(12f)).background(theme.bgColor)
                    .border(.5f, theme.bolderColor).padding(layout.margin)
            ) {
                val range = stats["human_readable_range"]!!.jsonPrimitive.content
                text("WakaTime · 404E · $range", textModifier = titleStyle)
                val total = stats["human_readable_total_including_other_language"]?.jsonPrimitive?.content
                val average = stats["human_readable_daily_average_including_other_language"]?.jsonPrimitive?.content
                if (total != null) text("Total: $total", modifier = Modifier.padding(top = 6f), textModifier = bodyStyle)
                if (average != null) text("Daily: $average", modifier = Modifier.padding(top = 6f, bottom = 16f), textModifier = bodyStyle)
                waterfall(
                    columns = 2,
                    width = cardWidth * 2 + 24f,
                    columnSpacing = 24f,
                    rowSpacing = 18f
                ) {
                    sections.forEach { (section, values) -> card(section, values) }
                }
            }
        }

        val output = File("waka_dashboard.png")
        image.use { output.writeBytes(it.bytes()) }
        println("WakaTime public dashboard written to ${output.absolutePath}")
    }

    private suspend fun fetchPublicStats(config: top.e404.statimg.Config): JsonObject {
        repeat(7) { attempt ->
            val response = config.client.get("https://wakatime.com/api/v1/users/404E/stats")
            check(response.status.value in 200..299) {
                "WakaTime public stats request failed: HTTP ${response.status.value}"
            }
            val payload = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            payload["data"]?.jsonObject?.let { return it }
            val calculating = payload["message"]?.jsonPrimitive?.content?.contains("Calculating") == true
            if (!calculating || attempt == 6) {
                error("WakaTime response did not contain public stats data")
            }
            delay(2_000L)
        }
        error("WakaTime response did not contain public stats data")
    }

    private fun percentage(value: Float): String = "%.2f".format(java.util.Locale.ROOT, value).trimEnd('0').trimEnd('.') + "%"


    /** 图标来自 Google Material Design Icons，Apache-2.0：https://github.com/google/material-design-icons */
    private fun sectionIcon(marker: String): String {
        val path = when (marker) {
            "LANG" -> "M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6-6 6-1.4-1.4z"
            "IDE" -> "M20 18c1.1 0 1.99-.9 1.99-2L22 6c0-1.1-.9-2-2-2H4c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2H0v2h24v-2h-4zM4 6h16v10H4V6z"
            "OS" -> "M20 3H4c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h6v2H8v2h8v-2h-2v-2h6c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2"
            "AI" -> "M20 9V7c0-1.1-.9-2-2-2h-3c0-1.66-1.34-3-3-3S9 3.34 9 5H6C4.9 5 4 5.9 4 7v2c-1.66 0-3 1.34-3 3s1.34 3 3 3v4c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2v-4c1.66 0 3-1.34 3-3s-1.34-3-3-3zM9 13a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm7 4H8v-2h8v2zm-1-4a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3z"
            else -> "M23 8c0 1.1-.9 2-2 2-.18 0-.35-.02-.51-.07l-3.56 3.55c.05.16.07.34.07.52 0 1.1-.9 2-2 2s-2-.9-2-2c0-.18.02-.36.07-.52l-2.55-2.55c-.16.05-.34.07-.52.07s-.36-.02-.52-.07l-4.55 4.56c.05.16.07.33.07.51 0 1.1-.9 2-2 2s-2-.9-2-2 .9-2 2-2c.18 0 .35.02.51.07l4.56-4.55C8.02 9.36 8 9.18 8 9c0-1.1.9-2 2-2s2 .9 2 2c0 .18-.02.36-.07.52l2.55 2.55c.16-.05.34-.07.52-.07s.36.02.52.07l3.55-3.56C19.02 8.35 19 8.18 19 8c0-1.1.9-2 2-2s2 .9 2 2z"
        }
        return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="$path"/></svg>"""
    }

    private data class PublicSection(
        val marker: String,
        val title: String,
        val visibilityKey: String,
        val dataKey: String
    )
    private data class PublicStat(val name: String, val percent: Float)
}
