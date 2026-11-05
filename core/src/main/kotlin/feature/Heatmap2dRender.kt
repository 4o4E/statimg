package top.e404.statimg.feature

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import top.e404.statimg.FontFileResolver
import top.e404.tavolo.draw.compose.*
import top.e404.tavolo.util.FontManager
import top.e404.tavolo.util.argb
import top.e404.statimg.ColorSerializer
import java.time.LocalDate

object Heatmap2dRender {
    @Serializable
    data class Layout(
        @SerialName("title_font") val titleFontFile: String = "font/JetBrainsMono-Bold.ttf",
        @SerialName("title_size") val titleSize: Float = 26F,
        @SerialName("lang_font") val langFontFile: String = "font/JetBrainsMono-Bold.ttf",
        @SerialName("lang_size") val langSize: Float = 20F,
        @SerialName("text_font") val textFontFile: String = "font/JetBrainsMono-Medium.ttf",
        @SerialName("text_size") val textSize: Float = 20F,
        @SerialName("bg_radii") val bgRadii: Float = 12F,
        @SerialName("stroke_radii") val strokeRadii: Float = 12F,
        val margin: Float = 25F,
        val spacing: Float = 30F,
        @SerialName("bar_padding") val barPadding: Float = 10F,
        @SerialName("bar_height") val barHeight: Float = 10F,
        @SerialName("bar_width") val barWidth: Float = 280F
    ) {
        val titleFontFamily by lazy { FontManager.registerFile("heatmap2d-title", FontFileResolver.resolve(titleFontFile)) }
        val langFontFamily by lazy { FontManager.registerFile("heatmap2d-lang", FontFileResolver.resolve(langFontFile)) }
        val textFontFamily by lazy { FontManager.registerFile("heatmap2d-text", FontFileResolver.resolve(textFontFile)) }
    }

    @Serializable
    data class Theme(
        @SerialName("title_color")
        @Serializable(ColorSerializer::class)
        val titleColor: Int = 0xff2f80ed.toInt(),
        @SerialName("icon_color")
        @Serializable(ColorSerializer::class)
        val iconColor: Int = 0xff4c71f2.toInt(),
        @SerialName("text_color")
        @Serializable(ColorSerializer::class)
        val textColor: Int = 0xff434d58.toInt(),
        @SerialName("bg_color")
        @Serializable(ColorSerializer::class)
        val bgColor: Int = 0xfffffefe.toInt(),
        @SerialName("border_color")
        @Serializable(ColorSerializer::class)
        val bolderColor: Int = 0xffe4e2e2.toInt(),
    ) {
        companion object {
            val default = Theme()
        }
    }

    internal fun intensityColor(value: Int, max: Int, theme: Theme): Int {
        val ratio = if (max == 0) 0f else value.toFloat() / max
        fun channel(shift: Int): Int {
            val start = (theme.bgColor ushr shift) and 0xff
            val end = (theme.titleColor ushr shift) and 0xff
            return (start + (end - start) * ratio).toInt()
        }
        return argb(255, channel(16), channel(8), channel(0))
    }

    /**
     * 可组合的日历网格，不包含标题、背景或外框。
     * 每列表示一周，行从周一排列至周日；空值保留透明。
     */
    @UiDsl
    fun UiElement.calendarGrid(
        byMonth: Map<Pair<Int, Int>, List<List<Pair<LocalDate, Int?>>>>,
        max: Int,
        layout: Layout,
        theme: Theme,
        cellSize: Float = 15f,
        cellPadding: Float = 3f,
        textSize: Float = layout.textSize,
        weekdayLabel: (Int) -> String? = { day -> when (day) {
            0 -> "Mon"
            2 -> "Wed"
            4 -> "Fri"
            5 -> "Sat"
            else -> null
        } }
    ) {
        val bodyTextStyle = TextModifier.font(
            fontSize = textSize,
            textColor = theme.textColor,
            fontFamily = layout.textFontFamily
        )
        val cellShape = Shape.RoundedRect(3f)

        val cellStep = cellSize + cellPadding * 2

        @UiDsl
        fun Row.week(week: List<Pair<LocalDate, Int?>>) = column {
            for ((_, count) in week) {
                val color = count?.let { intensityColor(it, max, theme) } ?: Color.TRANSPARENT
                box(
                    Modifier.padding(cellPadding)
                        .size(cellSize)
                        .clip(cellShape)
                        .background(color)
                        .border(.5f, if (count == null) Color.TRANSPARENT else theme.textColor, shape = cellShape)
                )
            }
        }

        @UiDsl
        fun UiElement.month(
            year: Int,
            month: Int,
            byWeek: List<List<Pair<LocalDate, Int?>>>,
            index: Int
        ) {
            column(horizontalAlignment = if (index == 0) HorizontalAlignment.Right else HorizontalAlignment.Left) {
                text(
                    if (byWeek.size >= 3) "${year.toString().substring(2)}.${month.toString().padStart(2, '0')}"
                    else " ",
                    modifier = Modifier.padding(horizontal = cellPadding),
                    textModifier = bodyTextStyle
                )
                row {
                    for (week in byWeek) week(week)
                }
            }
        }

        row {
            column(Modifier.padding(right = 10f)) {
                text(" ", textModifier = bodyTextStyle)
                for (day in 0..6) {
                    val label = weekdayLabel(day)
                    if (label == null) box(Modifier.height(cellStep))
                    else text(label, modifier = Modifier.height(cellStep), textModifier = bodyTextStyle)
                }
            }
            for ((index, entry) in byMonth.entries.withIndex()) {
                val (yearMonth, byWeek) = entry
                month(yearMonth.first, yearMonth.second, byWeek, index)
            }
        }
    }

    /**
     * 渲染热力图
     * @param byMonth 日期和对应的计数对列表, 如果某天没有数据则计数为null, 日期第一周数据不满7天的应该在调用前填充
     * @param title 热力图标题
     * @param layout 布局配置
     * @param theme 主题配置
     * @return 返回渲染好的图片
     */
    fun renderCommit(
        byMonth: Map<Pair<Int, Int>, List<List<Pair<LocalDate, Int?>>>>,
        max: Int,
        title: String,
        layout: Layout,
        theme: Theme
    ): Image {
        val titleTextStyle = TextModifier.font(
            fontSize = layout.titleSize,
            textColor = theme.titleColor,
            fontFamily = layout.titleFontFamily
        )
        val frame = Shape.RoundedRect(layout.bgRadii)

        return render {
            column(
                Modifier
                    .clip(frame)
                    .background(theme.bgColor)
                    .border(2f, theme.bolderColor, shape = frame)
                    .padding(layout.margin)
            ) {
                text(title, textModifier = titleTextStyle)
                row(Modifier.padding(top = 20f)) {
                    calendarGrid(byMonth, max, layout, theme)
                }
            }
        }
    }
}
