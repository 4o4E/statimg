package top.e404.status.render.feature

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import top.e404.status.render.FontFileResolver
import top.e404.tavolo.draw.compose.*
import top.e404.tavolo.util.FontManager
import top.e404.tavolo.util.argb
import top.e404.status.render.ColorSerializer
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
        @SerialName("bg_radii") val bgRadii: Float = 4.5F,
        @SerialName("stroke_radii") val strokeRadii: Float = 4.5F,
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
        val bodyTextStyle = TextModifier.font(
            fontSize = layout.textSize,
            textColor = theme.textColor,
            fontFamily = layout.textFontFamily
        )
        val (_, sr, sg, sb) = theme.bgColor.argb()
        val (_, er, eg, eb) = theme.titleColor.argb()
        fun getColor(count: Int): Int {
            val ratio = count.toFloat() / max
            val r = (sr + (er - sr) * ratio).toInt()
            val g = (sg + (eg - sg) * ratio).toInt()
            val b = (sb + (eb - sb) * ratio).toInt()
            return argb(255, r, g, b)
        }

        @UiDsl
        fun Row.week(week: List<Pair<LocalDate, Int?>>) = column {
            for ((_, count) in week) {
                val color = count?.let { getColor(it) } ?: Color.TRANSPARENT
                box(
                    Modifier.padding(3f)
                        .size(15f)
                        .clip(Shape.RoundedRect(3f))
                        .background(color)
                        .border(.5f, if (count == null) Color.TRANSPARENT else theme.textColor)
                )
            }
        }

        /**
         * 一个月的块, 包含了左上角的年月和下方的几周热力图
         */
        @UiDsl
        fun UiElement.months(
            year: Int,
            month: Int,
            byWeek: List<List<Pair<LocalDate, Int?>>>,
            index: Int
        ) {
            // 处理第一个月不满的情况
            column(horizontalAlignment = if (index == 0) HorizontalAlignment.Right else HorizontalAlignment.Left) {
                // 小于三周的情况下不显示年月
                text(
                    if (byWeek.size >= 3) "${year.toString().substring(2)}.${month.toString().padStart(2, '0')}"
                    else " ",
                    modifier = Modifier.padding(horizontal = 3f),
                    textModifier = bodyTextStyle
                )
                row {
                    for (week in byWeek) week(week)
                }
            }
        }

        return render {
            column(
                Modifier
                    .clip(Shape.RoundedRect(layout.bgRadii))
                    .background(theme.bgColor)
                    .border(.5f, theme.bolderColor)
                    .padding(layout.margin)
            ) {
                text(
                    title,
                    textModifier = titleTextStyle
                )
                row(Modifier.padding(top = 20f)) {
                    // 最左侧星期
                    val boxModifier = Modifier.height(16f)
                    column(Modifier.padding(right = 10f)) {
                        // 一行字的高度
                        text(
                            " ",
                            textModifier = bodyTextStyle
                        )
                        text("Mon", textModifier = bodyTextStyle)
                        box(boxModifier)
                        text("Wed", textModifier = bodyTextStyle)
                        box(boxModifier)
                        text("Fri", textModifier = bodyTextStyle)
                        box(boxModifier)
                        text("Sat", textModifier = bodyTextStyle)
                    }
                    // 每月数据
                    for ((index, e) in byMonth.entries.withIndex()) {
                        val (yearMonth, byWeek) = e
                        months(yearMonth.first, yearMonth.second, byWeek, index)
                    }
                }
            }
        }
    }
}
