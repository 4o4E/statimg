package top.e404.statimg.platform

import org.jetbrains.skia.Image
import top.e404.statimg.feature.Heatmap2dRender
import top.e404.statimg.fetcher.GithubDashboardData
import top.e404.statimg.fetcher.GithubDashboardDay
import top.e404.statimg.fetcher.GithubDashboardRepository
import top.e404.tavolo.draw.compose.*
import top.e404.tavolo.draw.compose.charts.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt

/** 综合图使用类型化公开统计；调用方持有头像与返回图片，内部评级图片仅在合成期间存活。 */
object GithubDashboardRender {
    private val colors = listOf(
        0xff7aa2f7.toInt(), 0xffbb9af7.toInt(), 0xff2ac3b3.toInt(),
        0xffff9e64.toInt(), 0xfff7768e.toInt(), 0xff565f89.toInt()
    )
    private val frame = Shape.RoundedRect(12f)
    private const val border = 2f
    private const val padding = 14f
    private const val gap = 16f
    private const val cardInsets = (border + padding) * 2
    private const val donutSize = 140f
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd")

    fun render(
        data: GithubDashboardData,
        avatar: Image,
        layout: Heatmap2dRender.Layout,
        theme: Heatmap2dRender.Theme,
        date: LocalDate = LocalDate.now(ZoneId.of("Asia/Taipei"))
    ): Image {
        val context = MeasureContext()
        val titleStyle = TextModifier.font(fontSize = layout.titleSize, textColor = theme.titleColor, fontFamily = layout.titleFontFamily)
        val bodyStyle = TextModifier.font(fontSize = layout.textSize, textColor = theme.textColor, fontFamily = layout.textFontFamily)
        val smallStyle = TextModifier.font(fontSize = layout.textSize * .72f, textColor = theme.textColor, fontFamily = layout.textFontFamily)
        val heatmap = heatmap(data, layout, theme, titleStyle, smallStyle)
        heatmap.measure(context)
        // 日历格子决定整体宽度，不以卡片、头像或较长名称反向扩宽日历。
        val width = ceil(heatmap.width / 2f) * 2f
        val cardWidth = (width - gap) / 2f
        val stars = data.repositories.sumOf { it.stars }
        val header = header(data, stars, avatar, width, date, context, layout, theme, bodyStyle, smallStyle)
        val rank = GithubRankCalculator.calculate(
            allCommits = false, commits = data.activity.commits,
            prs = data.pullRequests.total, issues = data.totalIssues,
            reviews = data.activity.reviews, stars = stars, followers = data.followers
        )
        val rankSize = maxOf(layout.barWidth * .56f, layout.titleSize * 5.5f).roundToInt().toFloat()
        return GithubCardRender.rankBadge(rank, theme, layout, GithubStatsRenderOptions.RankIcon.DEFAULT, rankSize).use { badge ->
            val cards = listOf(
                statsCard(data, stars, badge, cardWidth, layout, theme, titleStyle),
                donutCard("Languages", data.languages.map { it.name to it.bytes }, cardWidth, context, layout, theme, titleStyle, bodyStyle, "No language bytes reported", ::compactBytes),
                radarCard(data, cardWidth, layout, theme, titleStyle),
                monthlyCard(data.activity.days, cardWidth, layout, theme, titleStyle, bodyStyle),
                donutCard("Public pull requests", listOf("Merged" to data.pullRequests.merged, "Closed" to data.pullRequests.closed, "Open" to data.pullRequests.open), cardWidth, context, layout, theme, titleStyle, bodyStyle, "No pull requests reported"),
                repoStarsCard(data.repositories, cardWidth, context, theme, titleStyle, bodyStyle, smallStyle)
            )
            heatmap.modifier = Modifier.padding(top = gap).width(width).then(heatmap.modifier)
            render(measureContext = context) {
                column(Modifier.clip(frame).background(theme.bgColor).border(border, theme.bolderColor, shape = frame).padding(padding)) {
                    add(header)
                    add(heatmap)
                    waterfall(columns = 2, width = width, columnSpacing = gap, rowSpacing = gap, modifier = Modifier.padding(top = gap)) {
                        cards.forEach { add(it) }
                    }
                }
            }
        }
    }

    private fun header(
        data: GithubDashboardData, stars: Int, avatar: Image, width: Float, date: LocalDate,
        context: MeasureContext, layout: Heatmap2dRender.Layout, theme: Heatmap2dRender.Theme,
        bodyStyle: TextModifier, smallStyle: TextModifier
    ): Row {
        val name = Text(data.name, textModifier = TextModifier.font(fontSize = 36f, textColor = theme.titleColor, fontFamily = layout.titleFontFamily), textOverflow = TextOverflow.Ellipsis)
        val login = Text("@${data.login}", textModifier = bodyStyle).apply { modifier = Modifier.padding(left = 12f) }
        val details = Column().apply {
            row(verticalAlignment = VerticalAlignment.Center) { add(name); add(login) }
            row(Modifier.padding(top = 12f)) {
                metric("Repos", "contribs", data.repositories.size, layout, theme, bodyStyle)
                metric("Stars", "stars", stars, layout, theme, bodyStyle)
                metric("Forks", "fork", data.repositories.sumOf { it.forks }, layout, theme, bodyStyle)
                metric("Followers", "followers", data.followers, layout, theme, bodyStyle)
                metric("Following", "following", data.following, layout, theme, bodyStyle)
            }
        }
        details.measure(context)
        val avatarSize = details.height
        val dateText = Text(date.format(dateFormat), textModifier = smallStyle).apply { measure(context) }
        login.measure(context)
        // 姓名仅占一行；较长显示名在保留用户ID、头像及当天日期的位置后使用原生省略。
        name.modifier = Modifier.widthIn(max = width - dateText.width - gap - avatarSize - gap - login.width)
        details.measure(context)
        val identity = Row(VerticalAlignment.Center).apply {
            image(avatar, Modifier.size(avatarSize).clip(Shape.Circle), ImageOverflow.Crop)
            box(Modifier.width(gap))
            add(details)
        }
        identity.measure(context)
        return Row().apply {
            add(identity)
            box(Modifier.width(width - identity.width - dateText.width))
            add(dateText)
        }
    }

    private fun UiElement.metric(
        label: String, icon: String, value: Int, layout: Heatmap2dRender.Layout,
        theme: Heatmap2dRender.Theme, bodyStyle: TextModifier
    ) {
        column(Modifier.padding(right = 20f)) {
            row(verticalAlignment = VerticalAlignment.Center) {
                icon(icon, theme.textColor)
                text(label, modifier = Modifier.padding(left = 6f), textModifier = bodyStyle)
            }
            text(value.toString(), modifier = Modifier.padding(top = 4f), textModifier = TextModifier.font(fontSize = layout.textSize, textColor = theme.titleColor, fontFamily = layout.langFontFamily))
        }
    }

    private fun UiElement.icon(name: String, color: Int, size: Float = 18f) {
        svg(GithubCardRender.tintSvg(GithubCardRender.dashboardIcon(name), color, size), Modifier.size(size))
    }

    private fun heatmap(
        data: GithubDashboardData, layout: Heatmap2dRender.Layout,
        theme: Heatmap2dRender.Theme, titleStyle: TextModifier, smallStyle: TextModifier
    ): Column = Column().apply {
        modifier = Modifier.border(border, theme.bolderColor, shape = frame).padding(16f)
        text("${data.activity.total} Contributions", textModifier = titleStyle)
        column(Modifier.padding(top = 12f)) {
            with(Heatmap2dRender) {
                calendarGrid(
                    calendarMonths(data.activity.from, data.activity.to, data.activity.days),
                    data.activity.days.maxOfOrNull { it.count } ?: 0, layout, theme,
                    cellSize = 13f, cellPadding = 2f, textSize = layout.textSize * .7f,
                    weekdayLabel = { day -> when (day) { 0 -> "Mon"; 2 -> "Wed"; 4 -> "Fri"; else -> null } }
                )
            }
            row(Modifier.padding(top = 8f), verticalAlignment = VerticalAlignment.Center) {
                text("Less", modifier = Modifier.padding(right = 6f), textModifier = smallStyle)
                for (level in 0..5) {
                    box(Modifier.padding(right = 4f).size(11f).clip(Shape.RoundedRect(3f))
                        .background(Heatmap2dRender.intensityColor(level, 5, theme))
                        .border(.5f, theme.textColor, shape = Shape.RoundedRect(3f)))
                }
                text("More", modifier = Modifier.padding(left = 2f), textModifier = smallStyle)
            }
        }
    }

    private fun calendarMonths(
        from: LocalDate, to: LocalDate, days: List<GithubDashboardDay>
    ): Map<Pair<Int, Int>, List<List<Pair<LocalDate, Int?>>>> {
        val counts = days.associate { it.date to it.count }
        val firstMonday = from.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val weeks = generateSequence(firstMonday) { it.plusWeeks(1) }.takeWhile { !it.isAfter(to) }.map { monday ->
            (0L..6L).map { offset ->
                val date = monday.plusDays(offset)
                date to if (date in from..to) counts[date] else null
            }
        }.toList()
        return weeks.groupBy { week ->
            val date = week.firstOrNull { it.second != null }?.first ?: week.first().first
            date.year to date.monthValue
        }.toSortedMap(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))
    }

    private fun statsCard(
        data: GithubDashboardData, stars: Int, badge: Image, width: Float,
        layout: Heatmap2dRender.Layout, theme: Heatmap2dRender.Theme, titleStyle: TextModifier
    ): Column {
        val values = listOf(
            Triple("stars", "Total Stars:", GithubCardRender.formatNumber(stars)),
            Triple("commits", "Total Commits:", GithubCardRender.formatNumber(data.activity.commits)),
            Triple("prs", "Total PRs:", GithubCardRender.formatNumber(data.pullRequests.total)),
            Triple("issues", "Total Issues:", GithubCardRender.formatNumber(data.totalIssues)),
            Triple("contribs", "Contributed to:", GithubCardRender.formatNumber(data.contributedTo))
        )
        val style = TextModifier.font(fontSize = layout.textSize, textColor = theme.textColor, fontFamily = layout.langFontFamily)
        return Column().apply {
            modifier = Modifier.width(width).border(border, theme.bolderColor, shape = frame).padding(padding)
            text("GitHub Stats", modifier = Modifier.padding(bottom = 8f), textModifier = titleStyle)
            row(verticalAlignment = VerticalAlignment.Top) {
                table(modifier = Modifier.width(width - cardInsets - badge.width - 12f), columnSpacing = 12f, rowSpacing = layout.textSize * .25f) {
                    values.forEach { (key, label, value) ->
                        tableRow {
                            cell {
                                row(verticalAlignment = VerticalAlignment.Center) {
                                    icon(key, theme.textColor)
                                    text(label, modifier = Modifier.padding(left = 6f), textModifier = style)
                                }
                            }
                            cell { text(value, textModifier = style) }
                        }
                    }
                }
                box(Modifier.width(12f))
                image(badge)
            }
        }
    }

    private fun donutCard(
        title: String, values: List<Pair<String, Number>>, width: Float, context: MeasureContext,
        layout: Heatmap2dRender.Layout, theme: Heatmap2dRender.Theme,
        titleStyle: TextModifier, bodyStyle: TextModifier, emptyText: String,
        valueLabel: (Float) -> String = { it.toLong().toString() }
    ): Column {
        val positive = values.filter { it.second.toDouble() > 0.0 }.sortedByDescending { it.second.toDouble() }
        val slices = positive.take(5).mapIndexed { index, (name, value) -> PieSlice(name, value.toFloat(), colors[index]) }.toMutableList()
        val remainder = positive.drop(5).sumOf { it.second.toDouble() }
        if (remainder > 0) slices += PieSlice("Others", remainder.toFloat(), colors[5])
        val numbers = slices.map { Text(valueLabel(it.value), textModifier = bodyStyle).apply { measure(context) } }
        val numberWidth = numbers.maxOfOrNull { it.width } ?: 0f
        // 保留完整名称；较长语言名在固定的图例区域换行，不缩小或隐藏环形图。
        val labelWidth = width - cardInsets - donutSize - 12f - 9f - 24f - numberWidth
        val legend = Table(columnSpacing = 12f, rowSpacing = 7f).apply {
            slices.forEachIndexed { index, slice ->
                tableRow {
                    cell(verticalAlignment = VerticalAlignment.Center) {
                        box(Modifier.size(9f).clip(Shape.RoundedRect(4f)).background(colors[index]))
                    }
                    cell(verticalAlignment = VerticalAlignment.Center) {
                        text(slice.label, modifier = Modifier.widthIn(max = labelWidth), textModifier = bodyStyle)
                    }
                    cell(horizontalAlignment = HorizontalAlignment.Right, verticalAlignment = VerticalAlignment.Center) { add(numbers[index]) }
                }
            }
        }
        legend.measure(context)
        return Column().apply {
            modifier = Modifier.width(width).border(border, theme.bolderColor, shape = frame).padding(padding)
            text(title, textModifier = titleStyle)
            if (slices.isEmpty()) text(emptyText, modifier = Modifier.padding(top = 10f), textModifier = bodyStyle)
            else row(Modifier.padding(top = 12f), verticalAlignment = VerticalAlignment.Center) {
                donutChart(
                    PieChartTheme(width = donutSize, height = donutSize, radius = 65f, innerRadius = 36f,
                        insets = ChartInsets(5f, 5f, 5f, 5f), palette = ChartPalette(colors), cornerRadius = 4f,
                        sliceGap = 3f, strokeWidth = 0f, showLabels = false,
                        legend = ChartLegendTheme(position = ChartLegendPosition.NONE)), slices
                )
                box(Modifier.width(width - cardInsets - legend.width - donutSize))
                add(legend)
            }
        }
    }

    private fun radarCard(
        data: GithubDashboardData, width: Float,
        layout: Heatmap2dRender.Layout, theme: Heatmap2dRender.Theme, titleStyle: TextModifier
    ): Column {
        val values = listOf(
            "Commit" to data.activity.commits, "Issue" to data.activity.issues,
            "PR" to data.activity.pullRequests, "Review" to data.activity.reviews,
            "Repo" to data.activity.repositories
        )
        val radarTheme = RadarTheme(
            width = 320f, height = 280f, radius = 105f, gridCount = 5,
            fillOutlineColor = colors[0], gridLineColor = theme.textColor and 0x00ffffff or 0x66000000,
            gridLineStyle = StrokeStyle.Dashed(listOf(5f, 5f)),
            gridFontColor = theme.textColor, gridFontSize = layout.textSize * .7f, gridFontFamily = layout.textFontFamily,
            gridFontProvider = { index -> val count = 10.0.pow(index).toInt(); if (count > 100) "${count / 1000}k" else count.toString() },
            labelFontColor = theme.textColor, labelFontSize = layout.textSize * .72f,
            labelFontFamily = layout.textFontFamily, labelOuterLength = 20f, labelFixPolicy = RadarFixPolicy.NONE,
            sizeMode = RadarSizeMode.NATURAL, naturalPadding = 4f
        )
        val rates = values.map { (name, count) -> name to githubContributionRate(count) }
        return Column().apply {
            modifier = Modifier.width(width).border(border, theme.bolderColor, shape = frame).padding(padding)
            text("Contribution types", textModifier = titleStyle)
            column(Modifier.width(width - cardInsets), horizontalAlignment = HorizontalAlignment.Center) {
                radar(radarTheme, rates)
            }
        }
    }

    private fun monthlyCard(
        days: List<GithubDashboardDay>, width: Float, layout: Heatmap2dRender.Layout,
        theme: Heatmap2dRender.Theme, titleStyle: TextModifier, bodyStyle: TextModifier
    ): Column {
        val months = days.groupBy { YearMonth.from(it.date) }.toSortedMap().map { (month, entries) -> month to entries.sumOf { it.count } }
        // 贡献次数是非负整数；刻度不多于最大计数，零活动仍使用0至1的有效坐标域。
        val maximum = maxOf(1, months.maxOfOrNull { it.second } ?: 0)
        val chartTheme = LineChartTheme(
            width = width - cardInsets, height = 210f,
            insets = ChartInsets(left = 42f, top = 18f, right = 28f, bottom = 36f),
            palette = ChartPalette(colors),
            axis = AxisTheme(labelTextStyle = ChartTextStyle(layout.textSize * .62f, theme.textColor, layout.textFontFamily), gridColor = theme.bolderColor),
            legend = ChartLegendTheme(position = ChartLegendPosition.NONE),
            yMin = 0f, yMax = maximum.toFloat(), yTickCount = minOf(4, maximum),
            xMin = 0f, xMax = (months.size - 1).coerceAtLeast(1).toFloat(),
            xTickCount = if (months.size > 1) (1..4).filter { (months.size - 1) % it == 0 }.maxOrNull() ?: 1 else 0,
            xLabelFormatter = { value -> months.getOrNull(value.toInt())?.first?.monthValue?.toString() ?: "" },
            yLabelFormatter = { it.toInt().toString() },
            showVerticalGrid = false
        )
        return Column().apply {
            modifier = Modifier.width(width).border(border, theme.bolderColor, shape = frame).padding(padding)
            text("Monthly contributions", textModifier = titleStyle)
            if (months.isEmpty()) text("No daily contribution data reported", modifier = Modifier.padding(top = 10f), textModifier = bodyStyle)
            else lineChart(chartTheme, listOf(LineSeries("Contributions", months.mapIndexed { index, entry -> LinePoint(index.toFloat(), entry.second.toFloat()) }, color = colors[0], showPoints = true)))
        }
    }

    private fun repoStarsCard(
        repositories: List<GithubDashboardRepository>, width: Float, context: MeasureContext,
        theme: Heatmap2dRender.Theme, titleStyle: TextModifier, bodyStyle: TextModifier, smallStyle: TextModifier
    ): Column {
        val sorted = repositories.sortedByDescending { it.stars }
        val ranked = sorted.take(5).map { it.name to it.stars }.toMutableList()
        if (sorted.size > 5) ranked += "Others" to sorted.drop(5).sumOf { it.stars }
        val maximum = ranked.maxOfOrNull { it.second } ?: 0
        val names = ranked.map { (name) ->
            val normal = Text(name, textModifier = bodyStyle).apply { measure(context) }
            if (normal.width <= 200f) normal else Text(name, textModifier = smallStyle).apply { modifier = Modifier.widthIn(max = 200f) }
        }
        val numbers = ranked.map { Text(it.second.toString(), textModifier = bodyStyle) }
        names.forEach { it.measure(context) }
        numbers.forEach { it.measure(context) }
        // 名称及数字必须先测量，条形再使用全部剩余宽度，数值列贴合卡片内容右边缘。
        val barWidth = width - cardInsets - (names.maxOfOrNull { it.width } ?: 0f) - (numbers.maxOfOrNull { it.width } ?: 0f) - 20f
        return Column().apply {
            modifier = Modifier.width(width).border(border, theme.bolderColor, shape = frame).padding(padding)
            text("Public repository stars", textModifier = titleStyle)
            if (ranked.isEmpty()) text("No public repositories reported", modifier = Modifier.padding(top = 10f), textModifier = bodyStyle)
            else table(modifier = Modifier.padding(top = 8f), columnSpacing = 10f, rowSpacing = 7f) {
                ranked.indices.forEach { index ->
                    tableRow {
                        cell(verticalAlignment = VerticalAlignment.Center) { add(names[index]) }
                        cell(verticalAlignment = VerticalAlignment.Center) {
                            box(Modifier.width(barWidth)) {
                                val fill = if (maximum == 0) 0f else barWidth * ranked[index].second / maximum
                                box(Modifier.width(fill).height(10f).clip(Shape.RoundedRect(5f)).background(colors[0]))
                            }
                        }
                        cell(horizontalAlignment = HorizontalAlignment.Right, verticalAlignment = VerticalAlignment.Center) { add(numbers[index]) }
                    }
                }
            }
        }
    }


    private fun compactBytes(value: Float): String = when {
        value >= 1024f * 1024f * 1024f -> "%.2f GB".format(Locale.ROOT, value / (1024f * 1024f * 1024f))
        value >= 1024f * 1024f -> "%.2f MB".format(Locale.ROOT, value / (1024f * 1024f))
        value >= 1024f -> "%.2f KB".format(Locale.ROOT, value / 1024f)
        else -> "${value.toLong()} B"
    }
}
