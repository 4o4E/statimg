package top.e404.statimg.platform

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import top.e404.tavolo.draw.compose.*
import top.e404.tavolo.draw.compose.charts.*
import top.e404.tavolo.util.FontManager
import org.jetbrains.skia.Font
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
    private val client inline get() = config.client

    private fun titleTextStyle(theme: Heatmap2dRender.Theme) = TextModifier.font(
        fontSize = layout.titleSize,
        textColor = theme.titleColor,
        fontFamily = layout.titleFontFamily
    )

    private fun bodyTextStyle(theme: Heatmap2dRender.Theme) = TextModifier.font(
        fontSize = layout.textSize,
        textColor = theme.textColor,
        fontFamily = layout.textFontFamily
    )

    suspend fun renderStats(user: String, theme: Heatmap2dRender.Theme): ByteArray {
        // 日历与分类统计分别公开；任意一项可见都可以生成综合图片。
        val stats = try {
            fetchUserStats(user)
        } catch (_: WakatimePublicDataUnavailable) {
            null
        }
        val activity = fetchWakatimeActivity(client, user)
        val definitions = listOf(
            StatsSection("LANG", "Languages", "is_language_usage_visible", "languages"),
            StatsSection("IDE", "Editors", "is_editor_usage_visible", "editors"),
            StatsSection("OS", "Operating Systems", "is_os_usage_visible", "operating_systems"),
            StatsSection("CAT", "Categories", "is_category_usage_visible", "categories"),
            StatsSection("AI", "AI Models", "is_ai_model_usage_visible", "ai_models")
        )
        val sections = definitions.mapNotNull { section ->
            if (stats?.get(section.visibilityKey)?.jsonPrimitive?.boolean != true) return@mapNotNull null
            val values = (stats[section.dataKey] as? JsonArray)?.map { item ->
                val value = item.jsonObject
                val name = value.getValue("name").jsonPrimitive.content
                val percent = value.getValue("percent").jsonPrimitive.float
                StatsValue(name, percent, percentage(percent))
            }.orEmpty().sortedByDescending { it.percent }
            if (values.isEmpty()) null else section to values
        }
        if (sections.isEmpty() && activity == null) throw WakatimePublicDataUnavailable()
        val range = if (stats != null) stats.getValue("human_readable_range").jsonPrimitive.content else checkNotNull(activity).range
        val title = "WakaTime · $user · $range"
        val durationsVisible = stats?.get("is_coding_activity_visible")?.jsonPrimitive?.boolean == true
        val total = stats.takeIf { durationsVisible }
            ?.get("human_readable_total_including_other_language")?.jsonPrimitive?.contentOrNull
        val daily = stats.takeIf { durationsVisible }
            ?.get("human_readable_daily_average_including_other_language")?.jsonPrimitive?.contentOrNull
        return renderDashboard(title, total, daily, sections, activity, theme)
    }

    private fun renderDashboard(
        title: String,
        total: String?,
        daily: String?,
        sections: List<Pair<StatsSection, List<StatsValue>>>,
        activity: WakatimeActivity?,
        theme: Heatmap2dRender.Theme
    ): ByteArray {
        val colors = listOf(0xff7aa2f7.toInt(), 0xffbb9af7.toInt(), 0xff2ac3b3.toInt(), 0xffff9e64.toInt(), 0xfff7768e.toInt(), 0xff565f89.toInt())
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
        val titleStyle = titleTextStyle(theme)
        val labelStyle = TextModifier.font(fontSize = layout.langSize, textColor = theme.titleColor, fontFamily = layout.langFontFamily)
        val bodyStyle = bodyTextStyle(theme)
        val (legendWidth, durationWidth) = Font(FontManager.resolve(layout.textFontFamily), layout.textSize).use { font ->
            val longestLegend = sections.maxOfOrNull { (_, values) ->
                val shownWidth = values.take(5).maxOf { font.measureTextWidth("${it.name}  ${it.percentageLabel}") }
                val remainder = values.drop(5).sumOf { it.percent.toDouble() }.toFloat()
                max(shownWidth, if (remainder > 0f) font.measureTextWidth("Others  ${percentage(remainder)}") else 0f)
            } ?: 0f
            val longestDuration = max(
                total?.let { font.measureTextWidth("Total: $it") } ?: 0f,
                daily?.let { font.measureTextWidth("Daily: $it") } ?: 0f
            )
            longestLegend to longestDuration
        }
        val cardWidth = max(
            500f,
            140f + 14f + 17f + legendWidth + 32f
        )
        val dashboardWidth = cardWidth * 2 + 24f
        val headerWidth = Font(FontManager.resolve(layout.titleFontFamily), layout.titleSize).use { font ->
            max(font.measureTextWidth(title), durationWidth)
        }
        val frameShape = Shape.RoundedRect(12f)
        // 外框的 1px 占位计入尺寸，使内部细边框落在整数像素边界，而非分散到相邻两行或两列。
        val outerWidth = max(dashboardWidth, headerWidth) + (layout.margin + 1f) * 2

        return render {
            column(
                Modifier.width(outerWidth)
                    .clip(frameShape).background(theme.bgColor)
                    .border(1f, theme.bolderColor, shape = frameShape).padding(layout.margin)
            ) {
                text(title, textModifier = titleStyle)
                if (total != null) text("Total: $total", modifier = Modifier.padding(top = 6f), textModifier = bodyStyle)
                if (daily != null) text("Daily: $daily", modifier = Modifier.padding(top = 6f), textModifier = bodyStyle)
                if (activity != null) {
                    column(
                        Modifier.padding(top = 16f).width(dashboardWidth)
                            .border(1f, theme.bolderColor, shape = frameShape).padding(16f)
                    ) {
                        text("Activity · ${activity.range}", textModifier = labelStyle)
                        column(Modifier.padding(top = 14f)) {
                            with(Heatmap2dRender) {
                                calendarGrid(activity.byMonth, 5, layout, theme, cellSize = 13f, cellPadding = 2f, textSize = 14f)
                            }
                        }
                        val legendStyle = TextModifier.font(
                            fontSize = 14f, textColor = theme.textColor, fontFamily = layout.textFontFamily
                        )
                        row(Modifier.padding(top = 10f), verticalAlignment = VerticalAlignment.Center) {
                            text("Less", modifier = Modifier.padding(right = 6f), textModifier = legendStyle)
                            for (level in 0..5) {
                                box(
                                    Modifier.padding(right = 4f).size(13f)
                                        .clip(Shape.RoundedRect(3f))
                                        .background(Heatmap2dRender.intensityColor(level, 5, theme))
                                        .border(.5f, theme.textColor, shape = Shape.RoundedRect(3f))
                                )
                            }
                            text("More", modifier = Modifier.padding(left = 2f), textModifier = legendStyle)
                        }
                    }
                }
                if (sections.isNotEmpty()) waterfall(columns = 2, width = dashboardWidth, columnSpacing = 24f, rowSpacing = 18f,
                    modifier = Modifier.padding(top = if (activity == null) 16f else 18f)) {
                    sections.forEach { (section, values) ->
                        val shown = values.take(5)
                        val remainder = values.drop(5).sumOf { it.percent.toDouble() }.toFloat()
                        val chartSlices = shown.mapIndexed { index, value -> PieSlice(value.name, value.percent, colors[index]) } +
                            if (remainder > 0f) listOf(PieSlice("Others", remainder, colors[5])) else emptyList()
                        column(
                            // 内容已有内边距，只绘制完整圆角边框，避免重复裁切描边的抗锯齿边缘。
                            Modifier.width(cardWidth)
                                .border(1f, theme.bolderColor, shape = frameShape).padding(16f)
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
                                    shown.forEachIndexed { index, value ->
                                        row(Modifier.padding(bottom = 8f), verticalAlignment = VerticalAlignment.Center) {
                                            box(Modifier.size(10f).clip(Shape.RoundedRect(5f)).background(colors[index]))
                                            text("${value.name}  ${value.percentageLabel}", modifier = Modifier.padding(left = 7f), textModifier = bodyStyle)
                                        }
                                    }
                                    if (remainder > 0f) row(verticalAlignment = VerticalAlignment.Center) {
                                        box(Modifier.size(10f).clip(Shape.RoundedRect(5f)).background(colors[5]))
                                        text("Others  ${percentage(remainder)}", modifier = Modifier.padding(left = 7f), textModifier = bodyStyle)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.use { it.bytes() }
    }

    private fun percentage(value: Float): String =
        "%.2f".format(java.util.Locale.ROOT, value).trimEnd('0').trimEnd('.') + "%"

    /** 图标来自 Google Material Design Icons，Apache-2.0：https://github.com/google/material-design-icons */
    private fun sectionIcon(marker: String): String {
        val path = when (marker) {
            "LANG" -> "M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6-6 6-1.4-1.4z"
            "IDE" -> "M20 18c1.1 0 1.99-.9 1.99-2L22 6c0-1.1-.9-2-2-2H4c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2H0v2h24v-2h-4zM4 6h16v10H4V6z"
            "OS" -> "M20 3H4c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h6v2H8v2h8v-2h-2v-2h6c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2"
            "AI" -> "M20 9V7c0-1.1-.9-2-2-2h-3c0-1.66-1.34-3-3-3S9 3.34 9 5H6C4.9 5 4 5.9 4 7v2c-1.66 0-3 1.34-3 3s1.34 3 3 3v4c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2v-4c1.66 0 3-1.34 3-3s-1.34-3-3-3zM9 13a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm7 4H8v-2h8v2zm-1-4a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3z"
            else -> "M23 8c0 1.1-.9 2-2 2-.18 0-.35-.02-.51-.07l-3.56 3.55c.05.16.07.34.07.52 0 1.1-.9 2-2 2s-2-.9-2-2c0-.18.02-.36.07-.52l-2.55-2.55c-.16.05-.34-.07-.52-.07s-.36.02-.52-.07l-4.55 4.56c.05.16.07.33.07.51 0 1.1-.9 2-2 2s-2-.9-2-2 .9-2 2-2c.18 0 .35.02.51.07l4.56-4.55C8.02 9.36 8 9.18 8 9c0-1.1.9-2 2-2s2 .9 2 2c0 .18-.02.36-.07.52l2.55 2.55c.16.05.34.07.52.07s.36-.02.52-.07l3.55-3.56C19.02 8.35 19 8.18 19 8c0-1.1.9-2 2-2s2 .9 2 2z"
        }
        return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="$path"/></svg>"""
    }

    private data class StatsSection(val marker: String, val title: String, val visibilityKey: String, val dataKey: String)
    private data class StatsValue(val name: String, val percent: Float, val percentageLabel: String)

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
