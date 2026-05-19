package top.e404.statimg.platform

import org.jetbrains.skia.Color
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.Font
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import top.e404.statimg.feature.Heatmap2dRender
import top.e404.statimg.fetcher.GithubLanguageStat
import top.e404.statimg.fetcher.GithubRank
import top.e404.statimg.fetcher.GithubRepoCardData
import top.e404.statimg.fetcher.GithubStats
import top.e404.tavolo.draw.compose.HorizontalAlignment
import top.e404.tavolo.draw.compose.Modifier
import top.e404.tavolo.draw.compose.Shape
import top.e404.tavolo.draw.compose.TextModifier
import top.e404.tavolo.draw.compose.UiDsl
import top.e404.tavolo.draw.compose.UiElement
import top.e404.tavolo.draw.compose.VerticalAlignment
import top.e404.tavolo.draw.compose.background
import top.e404.tavolo.draw.compose.border
import top.e404.tavolo.draw.compose.box
import top.e404.tavolo.draw.compose.clip
import top.e404.tavolo.draw.compose.column
import top.e404.tavolo.draw.compose.font
import top.e404.tavolo.draw.compose.height
import top.e404.tavolo.draw.compose.image
import top.e404.tavolo.draw.compose.padding
import top.e404.tavolo.draw.compose.render
import top.e404.tavolo.draw.compose.row
import top.e404.tavolo.draw.compose.size
import top.e404.tavolo.draw.compose.svg
import top.e404.tavolo.draw.compose.text
import top.e404.tavolo.draw.compose.width
import top.e404.tavolo.util.FontManager
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

object GithubRankCalculator {
    private fun exponentialCdf(x: Double) = 1 - 2.0.pow(-x)
    private fun logNormalCdf(x: Double) = x / (1 + x)

    fun calculate(
        allCommits: Boolean,
        commits: Int,
        prs: Int,
        issues: Int,
        reviews: Int,
        stars: Int,
        followers: Int
    ): GithubRank {
        val commitMedian = if (allCommits) 1000.0 else 250.0
        val rank = 1 - (
            2 * exponentialCdf(commits / commitMedian) +
                3 * exponentialCdf(prs / 50.0) +
                1 * exponentialCdf(issues / 25.0) +
                1 * exponentialCdf(reviews / 2.0) +
                4 * logNormalCdf(stars / 50.0) +
                1 * logNormalCdf(followers / 10.0)
            ) / 12.0
        val percentile = rank * 100
        val thresholds = doubleArrayOf(1.0, 12.5, 25.0, 37.5, 50.0, 62.5, 75.0, 87.5, 100.0)
        val levels = arrayOf("S", "A+", "A", "A-", "B+", "B", "B-", "C+", "C")
        return GithubRank(levels[thresholds.indexOfFirst { percentile <= it }.coerceAtLeast(0)], percentile)
    }
}

data class GithubStatsRenderOptions(
    val hide: Set<String> = emptySet(),
    val show: Set<String> = emptySet(),
    val showIcons: Boolean = false,
    val hideRank: Boolean = false,
    val includeAllCommits: Boolean = false,
    val commitsYear: Int? = null,
    val rankIcon: RankIcon = RankIcon.DEFAULT,
    val numberFormat: NumberFormat = NumberFormat.SHORT,
    val numberPrecision: Int? = null,
    val customTitle: String? = null
) {
    enum class RankIcon { DEFAULT, PERCENTILE }
    enum class NumberFormat { SHORT, LONG }
}

data class GithubLanguagesRenderOptions(
    val layout: Layout = Layout.NORMAL,
    val langsCount: Int? = null,
    val hide: Set<String> = emptySet(),
    val hideProgress: Boolean = false,
    val statsFormat: StatsFormat = StatsFormat.PERCENTAGES,
    val customTitle: String? = null
) {
    enum class Layout { NORMAL, COMPACT, DONUT, DONUT_VERTICAL, PIE }
    enum class StatsFormat { PERCENTAGES, BYTES }
}

data class GithubRepoRenderOptions(
    val showOwner: Boolean = false,
    val descriptionLinesCount: Int? = null
)

object GithubCardRender {
    private const val DEFAULT_LANG_COLOR = "#858585"

    private data class CardMetrics(val layout: Heatmap2dRender.Layout) {
        val hairline = max(0.5f, layout.textSize / 40f)
        val titleGap = layout.textSize
        val rowGap = layout.textSize * 0.25f
        val inlineGap = layout.barPadding
        val itemGap = layout.textSize
        val wideGap = layout.spacing
        val chartLegendGap = layout.barPadding
        val iconSlotSize = layout.textSize * 1.2f
        val iconSize = layout.textSize * 1.05f
        val rankSize = max(layout.barWidth * 0.56f, layout.titleSize * 5.5f)
        val rankGap = layout.spacing * 1.5f
        val legendDotSize = max(layout.barHeight, layout.textSize * 0.8f)
        val legendGap = layout.barPadding
        val badgeGap = layout.textSize * 1.1f
        val descriptionWidth = max(layout.barWidth * 2.5f, layout.titleSize * 18f)
        val compactBarWidth = layout.barWidth * 3f
        val donutSize = max(layout.barWidth * 0.5f, layout.textSize * 7f)
        val verticalChartSize = max(layout.barWidth * 0.72f, layout.textSize * 10f)
    }

    private fun titleTextStyle(layout: Heatmap2dRender.Layout, theme: Heatmap2dRender.Theme) = TextModifier.font(
        fontSize = layout.titleSize,
        textColor = theme.titleColor,
        fontFamily = layout.titleFontFamily
    )

    private fun bodyTextStyle(layout: Heatmap2dRender.Layout, theme: Heatmap2dRender.Theme, size: Float = layout.textSize) =
        TextModifier.font(
            fontSize = size,
            textColor = theme.textColor,
            fontFamily = layout.textFontFamily,
            fontWeight = 400
        )

    private fun boldTextStyle(layout: Heatmap2dRender.Layout, theme: Heatmap2dRender.Theme, size: Float = layout.textSize) =
        TextModifier.font(
            fontSize = size,
            textColor = theme.textColor,
            fontFamily = layout.langFontFamily
        )

    fun renderStats(
        stats: GithubStats,
        layout: Heatmap2dRender.Layout,
        theme: Heatmap2dRender.Theme,
        options: GithubStatsRenderOptions = GithubStatsRenderOptions()
    ): Image {
        val metrics = CardMetrics(layout)
        val items = statsItems(stats, options).filterNot { it.id in options.hide }
        require(items.isNotEmpty() || !options.hideRank) { "Either stats or rank are required." }
        val statTextSize = layout.textSize
        val title = options.customTitle ?: "${stats.name}'s GitHub Stats"
        val labelWidth = items.maxOfOrNull { estimateLangTextWidth(layout, "${it.label}:", statTextSize) } ?: 0f
        val valueWidth = items.maxOfOrNull {
            estimateLangTextWidth(
                layout,
                formatNumber(it.value, options.numberFormat, options.numberPrecision) + it.unit,
                statTextSize
            )
        } ?: 0f
        val iconSlotSize = if (options.showIcons) metrics.iconSlotSize else 0f
        val iconSize = if (options.showIcons) metrics.iconSize else 0f
        val iconGap = if (options.showIcons) metrics.inlineGap else 0f
        val labelValueGap = metrics.inlineGap * 2
        val statsColumnWidth = iconSlotSize + iconGap + labelWidth + labelValueGap + valueWidth
        val rankWidth = if (options.hideRank) 0f else metrics.rankSize
        val rankGap = if (options.hideRank) 0f else metrics.rankGap
        val contentWidth = max(
            estimateTitleTextWidth(layout, title),
            statsColumnWidth + rankGap + rankWidth
        )
        val cardWidth = contentWidth + layout.margin * 2
        val rankImage = if (options.hideRank) null else rankBadge(stats.rank, theme, layout, options.rankIcon, metrics.rankSize)

        return render {
            column(
                Modifier
                    .width(cardWidth)
                    .clip(Shape.RoundedRect(layout.bgRadii))
                    .background(theme.bgColor)
                    .border(metrics.hairline, theme.bolderColor)
                    .padding(layout.margin)
            ) {
                text(title, modifier = Modifier.padding(bottom = metrics.titleGap), textModifier = titleTextStyle(layout, theme))
                row(verticalAlignment = VerticalAlignment.Center) {
                    column(Modifier.width(statsColumnWidth)) {
                        items.forEachIndexed { index, item ->
                            statRow(item, index, options, layout, theme, labelWidth, statTextSize, iconSlotSize, iconSize, metrics)
                        }
                    }
                    if (rankImage != null) {
                        box(Modifier.width(rankGap))
                        box(Modifier.width(rankWidth), HorizontalAlignment.Right, VerticalAlignment.Center) {
                            image(rankImage)
                        }
                    }
                }
            }
        }
    }

    @UiDsl
    private fun UiElement.statRow(
        item: StatItem,
        index: Int,
        options: GithubStatsRenderOptions,
        layout: Heatmap2dRender.Layout,
        theme: Heatmap2dRender.Theme,
        labelWidth: Float,
        statTextSize: Float,
        iconSlotSize: Float,
        iconSize: Float,
        metrics: CardMetrics
    ) {
        row(Modifier.padding(bottom = metrics.rowGap), verticalAlignment = VerticalAlignment.Center) {
            if (options.showIcons) {
                box(Modifier.size(iconSlotSize), HorizontalAlignment.Center, VerticalAlignment.Center) {
                    svg(tintSvg(item.iconSvg, theme.iconColor, iconSize), Modifier.size(iconSize))
                }
                box(Modifier.width(metrics.inlineGap))
            }
            text(
                "${item.label}:",
                modifier = Modifier.width(labelWidth),
                textModifier = boldTextStyle(layout, theme, statTextSize)
            )
            box(Modifier.width(metrics.inlineGap * 2))
            text(
                formatNumber(item.value, options.numberFormat, options.numberPrecision) + item.unit,
                textModifier = boldTextStyle(layout, theme, statTextSize)
            )
        }
    }

    fun renderTopLanguages(
        topLangs: List<GithubLanguageStat>,
        layoutConfig: Heatmap2dRender.Layout,
        theme: Heatmap2dRender.Theme,
        options: GithubLanguagesRenderOptions = GithubLanguagesRenderOptions()
    ): Image {
        val metrics = CardMetrics(layoutConfig)
        val langs = trimLanguages(topLangs, options)
        val total = langs.sumOf { it.size }.takeIf { it > 0 } ?: 1.0
        val finalLayout = if (options.hideProgress && options.layout == GithubLanguagesRenderOptions.Layout.NORMAL) {
            GithubLanguagesRenderOptions.Layout.COMPACT
        } else options.layout
        val title = options.customTitle ?: "Most Used Languages"
        val longestLang = langs.maxOfOrNull { estimateLangTextWidth(layoutConfig, it.name, layoutConfig.textSize) } ?: 0f
        val longestValue = langs.maxOfOrNull {
            val percent = if (total == 0.0) 0.0 else it.size / total * 100
            estimateBodyTextWidth(layoutConfig, languageDisplayValue(it.size, percent, options.statsFormat), layoutConfig.textSize)
        } ?: 0f
        val titleWidth = estimateTitleTextWidth(layoutConfig, title)
        val normalWidth = layoutConfig.margin * 2 +
            longestLang + metrics.inlineGap + layoutConfig.barWidth + metrics.inlineGap + longestValue
        val legendWidths = legendColumnWidths(langs, total, layoutConfig, options.statsFormat, metrics)
        val legendWidth = legendWidths.sum() + if (legendWidths.size > 1) metrics.wideGap else 0f
        val compactContentWidth = maxOf(titleWidth, legendWidth, if (options.hideProgress) 0f else metrics.compactBarWidth)
        val donutHorizontalContentWidth = legendColumnWidth(langs, total, layoutConfig, options.statsFormat, metrics) +
            metrics.wideGap + metrics.donutSize
        val donutVerticalContentWidth = maxOf(titleWidth, legendWidth, metrics.verticalChartSize)
        val contentWidth = when (finalLayout) {
            GithubLanguagesRenderOptions.Layout.NORMAL -> max(normalWidth, titleWidth + layoutConfig.margin * 2)
            GithubLanguagesRenderOptions.Layout.COMPACT -> compactContentWidth + layoutConfig.margin * 2
            GithubLanguagesRenderOptions.Layout.DONUT -> max(titleWidth, donutHorizontalContentWidth) + layoutConfig.margin * 2
            GithubLanguagesRenderOptions.Layout.DONUT_VERTICAL,
            GithubLanguagesRenderOptions.Layout.PIE -> donutVerticalContentWidth + layoutConfig.margin * 2
        }
        val width = when (finalLayout) {
            else -> contentWidth
        }

        return render {
            column(
                Modifier
                    .width(width)
                    .clip(Shape.RoundedRect(layoutConfig.bgRadii))
                    .background(theme.bgColor)
                    .border(metrics.hairline, theme.bolderColor)
                    .padding(layoutConfig.margin)
            ) {
                text(title, modifier = Modifier.padding(bottom = metrics.titleGap), textModifier = titleTextStyle(layoutConfig, theme))
                when {
                    langs.isEmpty() -> text("No language data", textModifier = bodyTextStyle(layoutConfig, theme))
                    finalLayout == GithubLanguagesRenderOptions.Layout.NORMAL -> normalLanguageLayout(
                        langs,
                        total,
                        layoutConfig.barWidth,
                        longestLang,
                        longestValue,
                        layoutConfig,
                        theme,
                        options.statsFormat
                    )
                    finalLayout == GithubLanguagesRenderOptions.Layout.COMPACT -> compactLanguageLayout(
                        langs,
                        total,
                        width - layoutConfig.margin * 2,
                        layoutConfig,
                        theme,
                        options,
                        metrics
                    )
                    finalLayout == GithubLanguagesRenderOptions.Layout.DONUT -> row(verticalAlignment = VerticalAlignment.Center) {
                        val legendColumnWidth = legendColumnWidth(langs, total, layoutConfig, options.statsFormat, metrics)
                        column(Modifier.width(legendColumnWidth)) {
                            legendColumn(langs, total, layoutConfig, theme, options.statsFormat, metrics, legendColumnWidth)
                        }
                        box(Modifier.width(metrics.wideGap))
                        image(donutChart(langs, total, metrics.donutSize.roundToInt(), false))
                    }
                    finalLayout == GithubLanguagesRenderOptions.Layout.DONUT_VERTICAL -> column(horizontalAlignment = HorizontalAlignment.Center) {
                        image(donutChart(langs, total, metrics.verticalChartSize.roundToInt(), false))
                        box(Modifier.height(metrics.chartLegendGap))
                        legendList(langs, total, layoutConfig, theme, options.statsFormat, metrics)
                    }
                    finalLayout == GithubLanguagesRenderOptions.Layout.PIE -> column(horizontalAlignment = HorizontalAlignment.Center) {
                        image(donutChart(langs, total, metrics.verticalChartSize.roundToInt(), true))
                        box(Modifier.height(metrics.chartLegendGap))
                        legendList(langs, total, layoutConfig, theme, options.statsFormat, metrics)
                    }
                }
            }
        }
    }

    @UiDsl
    private fun UiElement.normalLanguageLayout(
        langs: List<GithubLanguageStat>,
        total: Double,
        barWidth: Float,
        langWidth: Float,
        valueWidth: Float,
        layout: Heatmap2dRender.Layout,
        theme: Heatmap2dRender.Theme,
        statsFormat: GithubLanguagesRenderOptions.StatsFormat
    ) {
        column {
            langs.forEach { lang ->
                val progress = (lang.size / total * 100).toFloat()
                row(Modifier.padding(bottom = layout.textSize * 0.25f), verticalAlignment = VerticalAlignment.Center) {
                    text(lang.name, modifier = Modifier.width(langWidth), textModifier = boldTextStyle(layout, theme, layout.textSize))
                    box(Modifier.width(layout.barPadding))
                    progressBar(barWidth, layout.barHeight, progress, theme.titleColor, theme.textColor)
                    box(Modifier.width(layout.barPadding))
                    text(
                        languageDisplayValue(lang.size, progress.toDouble(), statsFormat),
                        modifier = Modifier.width(valueWidth),
                        textModifier = bodyTextStyle(layout, theme, layout.textSize)
                    )
                }
            }
        }
    }

    @UiDsl
    private fun UiElement.compactLanguageLayout(
        langs: List<GithubLanguageStat>,
        total: Double,
        width: Float,
        layout: Heatmap2dRender.Layout,
        theme: Heatmap2dRender.Theme,
        options: GithubLanguagesRenderOptions,
        metrics: CardMetrics
    ) {
        if (!options.hideProgress) {
            image(stackedBar(langs, total, width.roundToInt(), layout.barHeight.roundToInt()))
            box(Modifier.height(metrics.wideGap))
        }
        legendList(langs, total, layout, theme, options.statsFormat, metrics)
    }

    @UiDsl
    private fun UiElement.legendList(
        langs: List<GithubLanguageStat>,
        total: Double,
        layout: Heatmap2dRender.Layout,
        theme: Heatmap2dRender.Theme,
        statsFormat: GithubLanguagesRenderOptions.StatsFormat,
        metrics: CardMetrics
    ) {
        val left = langs.filterIndexed { index, _ -> index % 2 == 0 }
        val right = langs.filterIndexed { index, _ -> index % 2 == 1 }
        val widths = legendColumnWidths(langs, total, layout, statsFormat, metrics)
        row {
            legendColumn(left, total, layout, theme, statsFormat, metrics, widths.getOrElse(0) { 0f })
            if (right.isNotEmpty()) {
                box(Modifier.width(metrics.wideGap))
                legendColumn(right, total, layout, theme, statsFormat, metrics, widths.getOrElse(1) { 0f })
            }
        }
    }

    @UiDsl
    private fun UiElement.legendColumn(
        langs: List<GithubLanguageStat>,
        total: Double,
        layout: Heatmap2dRender.Layout,
        theme: Heatmap2dRender.Theme,
        statsFormat: GithubLanguagesRenderOptions.StatsFormat,
        metrics: CardMetrics,
        width: Float
    ) {
        column(Modifier.width(width)) {
            langs.forEachIndexed { index, lang ->
                val percent = lang.size / total * 100
                row(
                    Modifier.padding(bottom = if (index == langs.lastIndex) 0f else metrics.legendGap),
                    verticalAlignment = VerticalAlignment.Center
                ) {
                    box(Modifier.size(metrics.legendDotSize).clip(Shape.RoundedRect(metrics.legendDotSize / 2)).background(parseColor(lang.color)))
                    text(
                        "${lang.name} ${languageDisplayValue(lang.size, percent, statsFormat)}",
                        modifier = Modifier.padding(left = metrics.inlineGap),
                        textModifier = bodyTextStyle(layout, theme, layout.textSize)
                    )
                }
            }
        }
    }

    private fun legendColumnWidths(
        langs: List<GithubLanguageStat>,
        total: Double,
        layout: Heatmap2dRender.Layout,
        statsFormat: GithubLanguagesRenderOptions.StatsFormat,
        metrics: CardMetrics
    ): List<Float> {
        val left = langs.filterIndexed { index, _ -> index % 2 == 0 }
        val right = langs.filterIndexed { index, _ -> index % 2 == 1 }
        return listOf(left, right)
            .filter { it.isNotEmpty() }
            .map { legendColumnWidth(it, total, layout, statsFormat, metrics) }
    }

    private fun legendColumnWidth(
        langs: List<GithubLanguageStat>,
        total: Double,
        layout: Heatmap2dRender.Layout,
        statsFormat: GithubLanguagesRenderOptions.StatsFormat,
        metrics: CardMetrics
    ): Float = metrics.legendDotSize + metrics.inlineGap + (
        langs.maxOfOrNull { lang ->
            val percent = lang.size / total * 100
            estimateBodyTextWidth(layout, "${lang.name} ${languageDisplayValue(lang.size, percent, statsFormat)}")
        } ?: 0f
    )

    fun renderRepo(
        repo: GithubRepoCardData,
        layout: Heatmap2dRender.Layout,
        theme: Heatmap2dRender.Theme,
        options: GithubRepoRenderOptions = GithubRepoRenderOptions()
    ): Image {
        val metrics = CardMetrics(layout)
        val maxLines = options.descriptionLinesCount?.coerceIn(1, 3) ?: 3
        val title = if (options.showOwner) repo.nameWithOwner else repo.name
        val iconSize = layout.titleSize
        val badge = when {
            repo.isTemplate -> "template"
            repo.isArchived -> "archived"
            else -> null
        }
        val badgeWidth = badge?.let { metrics.badgeGap + estimateBodyTextWidth(layout, it, layout.textSize) } ?: 0f
        val titleRowWidth = iconSize + metrics.inlineGap + estimateTitleTextWidth(layout, title) + badgeWidth
        val metaWidth = (repo.primaryLanguageName?.let {
            layout.textSize + metrics.inlineGap + estimateBodyTextWidth(layout, it, layout.textSize) + metrics.wideGap
        } ?: 0f) +
            iconSize + metrics.inlineGap + estimateBodyTextWidth(layout, formatNumber(repo.starCount), layout.textSize) + metrics.wideGap +
            iconSize + metrics.inlineGap + estimateBodyTextWidth(layout, formatNumber(repo.forkCount), layout.textSize)
        val initialContentWidth = maxOf(metrics.descriptionWidth, titleRowWidth, metaWidth)
        val descLines = wrapByWidth(
            repo.description ?: "No description provided",
            initialContentWidth,
            layout.textFontFamily,
            layout.textSize,
            maxLines
        )
        val descWidth = descLines.maxOfOrNull { estimateBodyTextWidth(layout, it, layout.textSize) } ?: 0f
        val cardWidth = maxOf(titleRowWidth, descWidth, metaWidth) + layout.margin * 2

        return render {
            column(
                Modifier
                    .width(cardWidth)
                    .clip(Shape.RoundedRect(layout.bgRadii))
                    .background(theme.bgColor)
                    .border(metrics.hairline, theme.bolderColor)
                    .padding(layout.margin)
            ) {
                row(verticalAlignment = VerticalAlignment.Center) {
                    svg(tintSvg(Octicons.contribs, theme.iconColor, iconSize), Modifier.size(iconSize))
                    text(title, modifier = Modifier.padding(left = metrics.inlineGap), textModifier = titleTextStyle(layout, theme))
                    if (badge != null) {
                        text(
                            badge,
                            modifier = Modifier.padding(left = metrics.badgeGap),
                            textModifier = bodyTextStyle(layout, theme, layout.textSize)
                        )
                    }
                }
                column(Modifier.padding(top = metrics.titleGap)) {
                    descLines.forEach { line ->
                        text(line, textModifier = bodyTextStyle(layout, theme, layout.textSize))
                    }
                }
                row(Modifier.padding(top = metrics.wideGap), verticalAlignment = VerticalAlignment.Center) {
                    repo.primaryLanguageName?.let { lang ->
                        box(Modifier.size(layout.textSize).clip(Shape.RoundedRect(layout.textSize / 2)).background(parseColor(repo.primaryLanguageColor)))
                        text(lang, modifier = Modifier.padding(left = metrics.inlineGap, right = metrics.wideGap), textModifier = bodyTextStyle(layout, theme, layout.textSize))
                    }
                    svg(tintSvg(Octicons.star, theme.iconColor, iconSize), Modifier.size(iconSize))
                    text(formatNumber(repo.starCount), modifier = Modifier.padding(left = metrics.inlineGap, right = metrics.wideGap), textModifier = bodyTextStyle(layout, theme, layout.textSize))
                    svg(tintSvg(Octicons.fork, theme.iconColor, iconSize), Modifier.size(iconSize))
                    text(formatNumber(repo.forkCount), modifier = Modifier.padding(left = metrics.inlineGap), textModifier = bodyTextStyle(layout, theme, layout.textSize))
                }
            }
        }
    }

    @UiDsl
    private fun UiElement.progressBar(width: Float, height: Float, progress: Float, color: Int, bgColor: Int) {
        box(Modifier.width(width).height(height).clip(Shape.RoundedRect(height / 2)).background(bgColor)) {
            box(
                Modifier
                    .width(max(height, width * progress.coerceIn(0f, 100f) / 100f))
                    .height(height)
                    .clip(Shape.RoundedRect(height / 2))
                    .background(color)
            )
        }
    }

    private fun rankBadge(
        rank: GithubRank,
        theme: Heatmap2dRender.Theme,
        layout: Heatmap2dRender.Layout,
        rankIcon: GithubStatsRenderOptions.RankIcon,
        badgeSize: Float
    ): Image {
        val size = badgeSize.roundToInt()
        val center = size / 2f
        val radius = size * 0.4f
        val stroke = size * 0.0625f
        return Surface.makeRasterN32Premul(size, size).use { surface ->
            val canvas = surface.canvas
            canvas.clear(Color.TRANSPARENT)
            val rim = Paint().apply {
                isAntiAlias = true
                mode = PaintMode.STROKE
                strokeWidth = stroke
                color = theme.titleColor.withAlpha(0x33)
            }
            val ring = Paint().apply {
                isAntiAlias = true
                mode = PaintMode.STROKE
                strokeCap = PaintStrokeCap.ROUND
                strokeWidth = stroke
                color = theme.titleColor.withAlpha(0xcc)
            }
            canvas.drawCircle(center, center, radius, rim)
            val progress = (100 - rank.percentile).coerceIn(0.0, 100.0).toFloat()
            canvas.drawArc(
                center - radius,
                center - radius,
                center + radius,
                center + radius,
                -90f,
                progress * 3.6f,
                false,
                ring
            )
            val textPaint = Paint().apply {
                isAntiAlias = true
                color = theme.textColor
            }
            val font = Font(
                FontManager.resolve(layout.langFontFamily),
                if (rankIcon == GithubStatsRenderOptions.RankIcon.PERCENTILE) size * 0.15f else size * 0.2625f
            ).apply {
                isEmboldened = true
            }
            if (rankIcon == GithubStatsRenderOptions.RankIcon.PERCENTILE) {
                drawCentered(canvas, "Top", center, center - size * 0.0875f, font, textPaint)
                drawCentered(canvas, "${"%.1f".format(rank.percentile)}%", center, center + size * 0.1375f, font, textPaint)
            } else {
                drawCentered(canvas, rank.level, center, center + size * 0.0875f, font, textPaint)
            }
            surface.makeImageSnapshot()
        }
    }

    private fun stackedBar(langs: List<GithubLanguageStat>, total: Double, width: Int, height: Int): Image =
        Surface.makeRasterN32Premul(width, height).use { surface ->
            val canvas = surface.canvas
            val paint = Paint().apply { isAntiAlias = true }
            canvas.clear(Color.TRANSPARENT)
            val bounds = RRect.makeXYWH(0f, 0f, width.toFloat(), height.toFloat(), height / 2f)
            canvas.save()
            canvas.clipRRect(bounds, ClipMode.INTERSECT, true)
            var offset = 0f
            langs.forEach { lang ->
                val actualWidth = (lang.size / total * width).toFloat()
                paint.color = parseColor(lang.color)
                canvas.drawRect(Rect.makeXYWH(offset, 0f, max(actualWidth, if (actualWidth > 0) 2f else 0f), height.toFloat()), paint)
                offset += actualWidth
            }
            canvas.restore()
            surface.makeImageSnapshot()
        }

    private fun donutChart(langs: List<GithubLanguageStat>, total: Double, size: Int, pie: Boolean): Image =
        Surface.makeRasterN32Premul(size, size).use { surface ->
            val canvas = surface.canvas
            canvas.clear(Color.TRANSPARENT)
            val paint = Paint().apply { isAntiAlias = true }
            val radius = size * 0.42f
            val strokeWidth = size * 0.08f
            val left = size / 2f - radius
            val top = size / 2f - radius
            val right = size / 2f + radius
            val bottom = size / 2f + radius
            var start = -90f
            langs.forEach { lang ->
                val sweep = (lang.size / total * 360).toFloat()
                paint.color = parseColor(lang.color)
                if (pie) {
                    paint.mode = PaintMode.FILL
                    canvas.drawArc(left, top, right, bottom, start, sweep, true, paint)
                } else {
                    paint.mode = PaintMode.STROKE
                    paint.strokeWidth = strokeWidth
                    paint.strokeCap = PaintStrokeCap.BUTT
                    canvas.drawArc(left, top, right, bottom, start, sweep, false, paint)
                }
                start += sweep
            }
            surface.makeImageSnapshot()
        }

    private fun drawCentered(canvas: org.jetbrains.skia.Canvas, text: String, x: Float, baseline: Float, font: Font, paint: Paint) {
        val width = font.measureTextWidth(text)
        canvas.drawString(text, x - width / 2, baseline, font, paint)
    }

    private fun statsItems(stats: GithubStats, options: GithubStatsRenderOptions): List<StatItem> {
        val commitLabel = when {
            options.includeAllCommits -> "Total Commits"
            options.commitsYear != null -> "Total Commits (${options.commitsYear})"
            else -> "Total Commits (Last Year)"
        }
        return buildList {
            add(StatItem("stars", "Total Stars Earned", stats.totalStars, Octicons.star))
            add(StatItem("commits", commitLabel, stats.totalCommits, Octicons.commits))
            add(StatItem("prs", "Total PRs", stats.totalPRs, Octicons.prs))
            if ("prs_merged" in options.show) add(StatItem("prs_merged", "Merged PRs", stats.totalPRsMerged, Octicons.prsMerged))
            if ("prs_merged_percentage" in options.show) {
                add(StatItem("prs_merged_percentage", "Merged PRs Percentage", stats.mergedPRsPercentage, Octicons.prsMergedPercentage, "%"))
            }
            if ("reviews" in options.show) add(StatItem("reviews", "Total Reviews", stats.totalReviews, Octicons.reviews))
            add(StatItem("issues", "Total Issues", stats.totalIssues, Octicons.issues))
            if ("discussions_started" in options.show) add(StatItem("discussions_started", "Discussions Started", stats.totalDiscussionsStarted, Octicons.discussionsStarted))
            if ("discussions_answered" in options.show) add(StatItem("discussions_answered", "Discussions Answered", stats.totalDiscussionsAnswered, Octicons.discussionsAnswered))
            add(StatItem("contribs", "Contributed to", stats.contributedTo, Octicons.contribs))
        }
    }

    private fun trimLanguages(topLangs: List<GithubLanguageStat>, options: GithubLanguagesRenderOptions): List<GithubLanguageStat> {
        val defaultCount = when {
            options.hideProgress || options.layout == GithubLanguagesRenderOptions.Layout.COMPACT -> 6
            options.layout == GithubLanguagesRenderOptions.Layout.DONUT -> 5
            options.layout == GithubLanguagesRenderOptions.Layout.NORMAL -> 5
            else -> 6
        }
        val count = (options.langsCount ?: defaultCount).coerceIn(1, 20)
        val hide = options.hide.map { it.trim().lowercase() }.toSet()
        return topLangs
            .sortedByDescending { it.size }
            .filterNot { it.name.trim().lowercase() in hide }
            .take(count)
    }

    private fun languageDisplayValue(size: Double, percent: Double, statsFormat: GithubLanguagesRenderOptions.StatsFormat): String =
        when (statsFormat) {
            GithubLanguagesRenderOptions.StatsFormat.PERCENTAGES -> "%.2f%%".format(percent)
            GithubLanguagesRenderOptions.StatsFormat.BYTES -> formatBytes(size)
        }

    private fun formatNumber(
        value: Number,
        format: GithubStatsRenderOptions.NumberFormat = GithubStatsRenderOptions.NumberFormat.SHORT,
        precision: Int? = null
    ): String {
        val double = value.toDouble()
        if (format == GithubStatsRenderOptions.NumberFormat.LONG || kotlin.math.abs(double) < 1000) {
            return if (value is Double || value is Float) "%.2f".format(double) else value.toLong().toString()
        }
        val digits = precision?.coerceIn(0, 2) ?: 1
        val units = arrayOf("" to 1.0, "k" to 1_000.0, "M" to 1_000_000.0, "B" to 1_000_000_000.0)
        val unit = units.last { kotlin.math.abs(double) >= it.second }
        return "%.${digits}f%s".format(double / unit.second, unit.first).replace(Regex("\\.0+(?=[kMB])"), "")
    }

    private fun formatBytes(size: Double): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = size
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        return if (index == 0) "${value.roundToInt()} ${units[index]}" else "%.2f %s".format(value, units[index])
    }

    private fun wrapByWidth(
        text: String,
        maxWidth: Float,
        fontFamily: String,
        fontSize: Float,
        maxLines: Int
    ): List<String> {
        val font = Font(FontManager.resolve(fontFamily), fontSize)
        val words = text.replace("\n", " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var line = ""
        var consumedWords = 0
        for ((index, word) in words.withIndex()) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && font.measureTextWidth(next) > maxWidth) {
                lines += line
                consumedWords = index
                if (lines.size == maxLines) break
                line = word
            } else {
                line = next
                consumedWords = index + 1
            }
        }
        if (lines.size < maxLines && line.isNotEmpty()) lines += line
        if (consumedWords < words.size && lines.isNotEmpty()) {
            lines[lines.lastIndex] = ellipsizeToWidth(lines.last(), maxWidth, font)
        }
        return lines
    }

    private fun ellipsizeToWidth(text: String, maxWidth: Float, font: Font): String {
        var candidate = text.trimEnd('.')
        while (candidate.isNotEmpty() && font.measureTextWidth("$candidate...") > maxWidth) {
            candidate = candidate.dropLast(1)
        }
        return if (candidate.isEmpty()) "..." else "$candidate..."
    }

    private fun estimateTitleTextWidth(layout: Heatmap2dRender.Layout, text: String): Float =
        estimateTextWidth(layout.titleFontFamily, text, layout.titleSize)

    private fun estimateLangTextWidth(layout: Heatmap2dRender.Layout, text: String, fontSize: Float = layout.langSize): Float =
        estimateTextWidth(layout.langFontFamily, text, fontSize)

    private fun estimateBodyTextWidth(layout: Heatmap2dRender.Layout, text: String, fontSize: Float = layout.textSize): Float =
        estimateTextWidth(layout.textFontFamily, text, fontSize)

    private fun estimateTextWidth(fontFamily: String, text: String, fontSize: Float): Float =
        Font(FontManager.resolve(fontFamily), fontSize).measureTextWidth(text)

    private fun parseColor(color: String?): Int {
        val raw = color?.removePrefix("#")
        if (raw == null || raw.length !in setOf(3, 6, 8)) return Color.makeRGB(0x85, 0x85, 0x85)
        val normalized = when (raw.length) {
            3 -> raw.map { "$it$it" }.joinToString("")
            6 -> "ff$raw"
            else -> raw
        }
        return normalized.toUInt(16).toInt()
    }

    private fun Int.withAlpha(alpha: Int): Int = (this and 0x00ffffff) or (alpha.coerceIn(0, 255) shl 24)

    private fun tintSvg(svg: String, color: Int, size: Float): String =
        svg.replace("currentColor", color.toRgbHex())
            .replace(Regex("""width="[^"]+""""), """width="${size}px"""")
            .replace(Regex("""height="[^"]+""""), """height="${size}px"""")

    private fun Int.toRgbHex(): String = "#%02x%02x%02x".format(
        this shr 16 and 0xff,
        this shr 8 and 0xff,
        this and 0xff
    )

    private data class StatItem(
        val id: String,
        val label: String,
        val value: Number,
        val iconSvg: String,
        val unit: String = ""
    )

    private object Octicons {
        val star = svg("""<path fill-rule="evenodd" d="M8 .25a.75.75 0 01.673.418l1.882 3.815 4.21.612a.75.75 0 01.416 1.279l-3.046 2.97.719 4.192a.75.75 0 01-1.088.791L8 12.347l-3.766 1.98a.75.75 0 01-1.088-.79l.72-4.194L.818 6.374a.75.75 0 01.416-1.28l4.21-.611L7.327.668A.75.75 0 018 .25zm0 2.445L6.615 5.5a.75.75 0 01-.564.41l-3.097.45 2.24 2.184a.75.75 0 01.216.664l-.528 3.084 2.769-1.456a.75.75 0 01.698 0l2.77 1.456-.53-3.084a.75.75 0 01.216-.664l2.24-2.183-3.096-.45a.75.75 0 01-.564-.41L8 2.694v.001z"/>""")
        val commits = svg("""<path fill-rule="evenodd" d="M1.643 3.143L.427 1.927A.25.25 0 000 2.104V5.75c0 .138.112.25.25.25h3.646a.25.25 0 00.177-.427L2.715 4.215a6.5 6.5 0 11-1.18 4.458.75.75 0 10-1.493.154 8.001 8.001 0 101.6-5.684zM7.75 4a.75.75 0 01.75.75v2.992l2.028.812a.75.75 0 01-.557 1.392l-2.5-1A.75.75 0 017 8.25v-3.5A.75.75 0 017.75 4z"/>""")
        val prs = svg("""<path fill-rule="evenodd" d="M7.177 3.073L9.573.677A.25.25 0 0110 .854v4.792a.25.25 0 01-.427.177L7.177 3.427a.25.25 0 010-.354zM3.75 2.5a.75.75 0 100 1.5.75.75 0 000-1.5zm-2.25.75a2.25 2.25 0 113 2.122v5.256a2.251 2.251 0 11-1.5 0V5.372A2.25 2.25 0 011.5 3.25zM11 2.5h-1V4h1a1 1 0 011 1v5.628a2.251 2.251 0 101.5 0V5A2.5 2.5 0 0011 2.5zm1 10.25a.75.75 0 111.5 0 .75.75 0 01-1.5 0zM3.75 12a.75.75 0 100 1.5.75.75 0 000-1.5z"/>""")
        val prsMerged = svg("""<path fill-rule="evenodd" d="M5.45 5.154A4.25 4.25 0 0 0 9.25 7.5h1.378a2.251 2.251 0 1 1 0 1.5H9.25A5.734 5.734 0 0 1 5 7.123v3.505a2.25 2.25 0 1 1-1.5 0V5.372a2.25 2.25 0 1 1 1.95-.218ZM4.25 13.5a.75.75 0 1 0 0-1.5.75.75 0 0 0 0 1.5Zm8.5-4.5a.75.75 0 1 0 0-1.5.75.75 0 0 0 0 1.5ZM5 3.25a.75.75 0 1 0 0 .005V3.25Z" />""")
        val prsMergedPercentage = svg("""<path fill-rule="evenodd" d="M13.442 2.558a.625.625 0 0 1 0 .884l-10 10a.625.625 0 1 1-.884-.884l10-10a.625.625 0 0 1 .884 0zM4.5 6a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm0 1a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5zm7 6a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm0 1a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z" />""")
        val issues = svg("""<path fill-rule="evenodd" d="M8 1.5a6.5 6.5 0 100 13 6.5 6.5 0 000-13zM0 8a8 8 0 1116 0A8 8 0 010 8zm9 3a1 1 0 11-2 0 1 1 0 012 0zm-.25-6.25a.75.75 0 00-1.5 0v3.5a.75.75 0 001.5 0v-3.5z"/>""")
        val contribs = svg("""<path fill-rule="evenodd" d="M2 2.5A2.5 2.5 0 014.5 0h8.75a.75.75 0 01.75.75v12.5a.75.75 0 01-.75.75h-2.5a.75.75 0 110-1.5h1.75v-2h-8a1 1 0 00-.714 1.7.75.75 0 01-1.072 1.05A2.495 2.495 0 012 11.5v-9zm10.5-1V9h-8c-.356 0-.694.074-1 .208V2.5a1 1 0 011-1h8zM5 12.25v3.25a.25.25 0 00.4.2l1.45-1.087a.25.25 0 01.3 0L8.6 15.7a.25.25 0 00.4-.2v-3.25a.25.25 0 00-.25-.25h-3.5a.25.25 0 00-.25.25z"/>""")
        val fork = svg("""<path fill-rule="evenodd" d="M5 3.25a.75.75 0 11-1.5 0 .75.75 0 011.5 0zm0 2.122a2.25 2.25 0 10-1.5 0v.878A2.25 2.25 0 005.75 8.5h1.5v2.128a2.251 2.251 0 101.5 0V8.5h1.5a2.25 2.25 0 002.25-2.25v-.878a2.25 2.25 0 10-1.5 0v.878a.75.75 0 01-.75.75h-4.5A.75.75 0 015 6.25v-.878zm3.75 7.378a.75.75 0 11-1.5 0 .75.75 0 011.5 0zm3-8.75a.75.75 0 100-1.5.75.75 0 000 1.5z"></path>""")
        val reviews = svg("""<path fill-rule="evenodd" d="M8 2c1.981 0 3.671.992 4.933 2.078 1.27 1.091 2.187 2.345 2.637 3.023a1.62 1.62 0 0 1 0 1.798c-.45.678-1.367 1.932-2.637 3.023C11.67 13.008 9.981 14 8 14c-1.981 0-3.671-.992-4.933-2.078C1.797 10.83.88 9.576.43 8.898a1.62 1.62 0 0 1 0-1.798c.45-.677 1.367-1.931 2.637-3.022C4.33 2.992 6.019 2 8 2ZM1.679 7.932a.12.12 0 0 0 0 .136c.411.622 1.241 1.75 2.366 2.717C5.176 11.758 6.527 12.5 8 12.5c1.473 0 2.825-.742 3.955-1.715 1.124-.967 1.954-2.096 2.366-2.717a.12.12 0 0 0 0-.136c-.412-.621-1.242-1.75-2.366-2.717C10.824 4.242 9.473 3.5 8 3.5c-1.473 0-2.825.742-3.955 1.715-1.124.967-1.954 2.096-2.366 2.717ZM8 10a2 2 0 1 1-.001-3.999A2 2 0 0 1 8 10Z"/>""")
        val discussionsStarted = svg("""<path fill-rule="evenodd" d="M1.75 1h8.5c.966 0 1.75.784 1.75 1.75v5.5A1.75 1.75 0 0 1 10.25 10H7.061l-2.574 2.573A1.458 1.458 0 0 1 2 11.543V10h-.25A1.75 1.75 0 0 1 0 8.25v-5.5C0 1.784.784 1 1.75 1ZM1.5 2.75v5.5c0 .138.112.25.25.25h1a.75.75 0 0 1 .75.75v2.19l2.72-2.72a.749.749 0 0 1 .53-.22h3.5a.25.25 0 0 0 .25-.25v-5.5a.25.25 0 0 0-.25-.25h-8.5a.25.25 0 0 0-.25.25Zm13 2a.25.25 0 0 0-.25-.25h-.5a.75.75 0 0 1 0-1.5h.5c.966 0 1.75.784 1.75 1.75v5.5A1.75 1.75 0 0 1 14.25 12H14v1.543a1.458 1.458 0 0 1-2.487 1.03L9.22 12.28a.749.749 0 0 1 .326-1.275.749.749 0 0 1 .734.215l2.22 2.22v-2.19a.75.75 0 0 1 .75-.75h1a.25.25 0 0 0 .25-.25Z" />""")
        val discussionsAnswered = svg("""<path fill-rule="evenodd" d="M13.78 4.22a.75.75 0 0 1 0 1.06l-7.25 7.25a.75.75 0 0 1-1.06 0L2.22 9.28a.751.751 0 0 1 .018-1.042.751.751 0 0 1 1.042-.018L6 10.94l6.72-6.72a.75.75 0 0 1 1.06 0Z"/>""")

        private fun svg(path: String): String =
            """<svg aria-hidden="true" width="16px" height="16px" viewBox="0 0 16 16" fill="currentColor">$path</svg>"""
    }
}
