package top.e404.statimg.platform

import org.jetbrains.skia.Color
import top.e404.tavolo.draw.compose.*
import top.e404.tavolo.draw.compose.charts.BarTheme
import top.e404.tavolo.draw.compose.charts.RadarFixPolicy
import top.e404.tavolo.draw.compose.charts.RadarTheme
import top.e404.tavolo.draw.compose.charts.bar
import top.e404.tavolo.draw.compose.charts.radar
import top.e404.tavolo.util.Colors
import top.e404.tavolo.util.bytes
import top.e404.statimg.IConfig
import top.e404.statimg.feature.Heatmap2dRender
import top.e404.statimg.feature.Heatmap3dRender
import top.e404.statimg.fetcher.GhUser
import top.e404.statimg.fetcher.GithubFetcher
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.log10
import kotlin.math.pow

class GithubRender(val config: IConfig) {
    private val fetcher = GithubFetcher(config)

    private fun normalTextStyle(size: Float, color: Int = Color.WHITE) = TextModifier.font(
        fontSize = size,
        textColor = color,
        fontFamily = config.github3d.font.normalFontFamily
    )

    private fun boldTextStyle(size: Float, color: Int = Color.WHITE) = TextModifier.font(
        fontSize = size,
        textColor = color,
        fontFamily = config.github3d.font.boldFontFamily
    )

    private suspend fun fetchDays(
        username: String,
        end: LocalDateTime
    ): Pair<MutableList<Pair<LocalDate, Int?>>, GhUser> {
        val commitInfo = fetcher.fetchCommitCount(username, end = end)
        if (commitInfo.data.user == null) {
            val errors = commitInfo.errors!!.joinToString("\n") {
                it.message
            }
            error("Failed to fetch commit info:\n${errors}")
        }
        val weeks = commitInfo.data.user.contributionsCollection.contributionCalendar.weeks
        val days: MutableList<Pair<LocalDate, Int?>> = weeks.flatMap { week ->
            week.contributionDays.map { day ->
                LocalDate.parse(day.date) to day.contributionCount
            }
        }.toMutableList()
        // 拆分出到第一个周一之前的零散天数
        run {
            val first = days.first().first
            val dayOfWeek = first.dayOfWeek.value // 1-7
            repeat(dayOfWeek - 1) {
                days.add(it, first.minusDays((dayOfWeek - 1 - it).toLong()) to null)
            }
        }
        return days to commitInfo.data.user
    }

    suspend fun renderContribution2d(username: String, end: LocalDateTime, theme: Heatmap2dRender.Theme): ByteArray {
        val (days) = fetchDays(username, end)

        // 需要处理跨年的情况
        val first = days.first().first
        val weekList = days.groupBy {
            // 计算和第一天的差的天数
            ChronoUnit.DAYS.between(first, it.first) / 7
        }
        val byMonth: Map<Pair<Int, Int>, List<List<Pair<LocalDate, Int?>>>> = weekList.values.groupBy {
            it.first().first.run { year to monthValue }
        }.toSortedMap { (y1, m1), (y2, m2) ->
            if (y1 != y2) y1 - y2 else m1 - m2
        }

        val max = days.maxOf { it.second ?: 0 }

        return Heatmap2dRender.renderCommit(
            byMonth,
            max,
            "$username's GitHub Commit from ${end.minusYears(1).toLocalDate()} to ${end.toLocalDate()}",
            config.layout2d,
            theme
        ).bytes()
    }

    suspend fun renderContribution3d(
        username: String,
        theme: Heatmap3dRender.Theme,
    ): ByteArray {
        val user = fetcher.fetchDetail(username, 100) ?: error("cannot find github user $username")
        val weeks = user.contributionsCollection.contributionCalendar.weeks
        val days: MutableList<Pair<LocalDate, Int?>> = weeks.flatMap { week ->
            week.contributionDays.map { day ->
                LocalDate.parse(day.date) to day.contributionCount
            }
        }.toMutableList()
        // 拆分出到第一个周一之前的零散天数
        run {
            val first = days.first().first
            val dayOfWeek = first.dayOfWeek.value // 1-7
            repeat(dayOfWeek - 1) {
                days.add(it, first.minusDays((dayOfWeek - 1 - it).toLong()) to null)
            }
        }

        data class LangInfo(val language: String, val color: String, var contributions: Int) {
            val skikoColor by lazy {
                color.replace("#", "ff").toUInt(16).toInt()
            }
        }

        val contributesLanguage = mutableMapOf<String, LangInfo>()
        user.contributionsCollection.commitContributionsByRepository!!.filter {
            it.repository.primaryLanguage != null
        }.forEach { repo ->
            val language = repo.repository.primaryLanguage?.name ?: "Other"
            val contributions = repo.contributions.totalCount

            contributesLanguage.getOrPut(language) {
                LangInfo(
                    language,
                    repo.repository.primaryLanguage?.color ?: "#444444",
                    0
                )
            }.contributions += contributions
        }
        val start = user.contributionsCollection.contributionCalendar.weeks.first().contributionDays.first().date
        val end = user.contributionsCollection.contributionCalendar.weeks.last().contributionDays.last().date
        var languages = contributesLanguage.entries
            .sortedByDescending { it.value.contributions }
            .map { it.value }
        var other = user.contributionsCollection.totalCommitContributions!! - languages.sumOf { it.contributions }
        // 只保留前五种语言，其他的归为 Other
        if (languages.size > 5) {
            val dropList = languages.subList(5, languages.size)
            other += dropList.sumOf { it.contributions }
            languages = languages.subList(0, 5)
        }
        if (other != 0) {
            languages = languages + LangInfo("Other", "#444444", other)
        }

        val heatmap = Heatmap3dRender.render(days, config.layout3d, theme)
        val w = heatmap.width.toFloat()
        val h = heatmap.height.toFloat()
        return render {
            val boxModifier = Modifier.size(w, h)
            box(boxModifier) {
                box(boxModifier) {
                    image(heatmap)
                }
                box(boxModifier, HorizontalAlignment.Right, VerticalAlignment.Top) {
                    row(Modifier.padding(20f)) {
                        text(
                            "$username / $start / $end",
                            textModifier = normalTextStyle(20f, Colors.GRAY.argb)
                        )
                    }
                }
                // 左下角饼图
                box(
                    boxModifier,
                    HorizontalAlignment.Left,
                    VerticalAlignment.Bottom
                ) {
                    row(Modifier.padding(bottom = 150f, left = 60f), VerticalAlignment.Center) {
                        bar(
                            BarTheme(120f, 60f, strokeColor = theme.background, strokeWidth = 3f),
                            languages.map { it.skikoColor to it.contributions.toFloat() }
                        )
                        column(Modifier.padding(left = 20f)) {
                            for (lang in languages) {
                                row(Modifier.padding(5f), VerticalAlignment.Center) {
                                    box(Modifier.padding(right = 10f).size(25f).background(lang.skikoColor))
                                    text(
                                        lang.language,
                                        textModifier = normalTextStyle(25f)
                                    )
                                }
                            }
                        }
                    }
                }
                // 右上角雷达图
                box(
                    boxModifier,
                    HorizontalAlignment.Right,
                    VerticalAlignment.Top
                ) {
                    val data = listOf(
                        "Commit" to user.contributionsCollection.totalCommitContributions,
                        "Issue" to user.contributionsCollection.totalIssueContributions!!,
                        "PR" to user.contributionsCollection.totalPullRequestContributions!!,
                        "Review" to user.contributionsCollection.totalPullRequestReviewContributions!!,
                        "Repo" to user.contributionsCollection.totalRepositoryContributions!!,
                    ).map { (name, v) ->
                        val scaled = if (v == 0) -.1f else log10(v.toDouble()).toFloat()
                        name to scaled / 4f
                    }
                    row(Modifier.padding(40f), VerticalAlignment.Center) {
                        val radarTheme = RadarTheme(
                            width = 600f,
                            height = 600f,
                            gridCount = 5,
                            fillColor = 0x77ffc837,
                            fillOutlineColor = 0xffffc837.toInt(),
                            fillOutlineWidth = 5f,
                            gridLineColor = 0xFFCCCCCC.toInt(),
                            gridLineWidth = 1f,
                            gridLineStyle = StrokeStyle.Dashed(listOf(5f, 5f)),
                            gridFontColor = 0xFFCCCCCC.toInt(),
                            gridFontSize = 18F,
                            gridFontFamily = config.github3d.font.normalFontFamily,
                            gridFontProvider = {
                                val v = 10.0.pow(it).toInt()
                                if (v > 100) "${v / 1000}k"
                                else v.toString()
                            },
                            labelOuterLength = 40f,
                            labelFixPolicy = RadarFixPolicy.NONE,
                            labelFontSize = 25f,
                            labelFontFamily = config.github3d.font.normalFontFamily
                        )
                        radar(radarTheme, data)
                    }
                }
                // 正下方
                box(
                    boxModifier,
                    HorizontalAlignment.Center,
                    VerticalAlignment.Bottom
                ) {
                    row(Modifier.padding(50f), VerticalAlignment.Bottom) {
                        text(
                            user.contributionsCollection.contributionCalendar.totalContributions.toString(),
                            textModifier = boldTextStyle(40f)
                        )
                        text(
                            "contributions",
                            modifier = Modifier.padding(left = 20f, right = 50f),
                            textModifier = normalTextStyle(30f)
                        )
                        row(Modifier.padding(horizontal = 50f), VerticalAlignment.Center) {
                            icon(IconTheme(40f, color = Color.WHITE), config.github3d.icon.star)
                            text(
                                user.repositories!!.nodes.sumOf { it.stargazerCount }.toString(),
                                modifier = Modifier.padding(left = 20f, right = 50f),
                                textModifier = normalTextStyle(40f)
                            )
                        }
                        row(Modifier.padding(horizontal = 50f), VerticalAlignment.Center) {
                            icon(IconTheme(40f, color = Color.WHITE), config.github3d.icon.fork)
                            text(
                                user.repositories!!.nodes.sumOf { it.forkCount }.toString(),
                                modifier = Modifier.padding(left = 20f, right = 30f),
                                textModifier = normalTextStyle(40f)
                            )
                        }

                    }
                }
            }
        }.bytes()
    }

    suspend fun renderStatsCard(
        username: String,
        theme: Heatmap2dRender.Theme,
        options: GithubStatsRenderOptions = GithubStatsRenderOptions(),
        excludeRepo: List<String> = emptyList()
    ): ByteArray {
        val show = options.show
        val stats = fetcher.fetchStats(
            username = username,
            includeAllCommits = options.includeAllCommits,
            excludeRepo = excludeRepo,
            includeMergedPullRequests = "prs_merged" in show || "prs_merged_percentage" in show,
            includeDiscussions = "discussions_started" in show,
            includeDiscussionsAnswers = "discussions_answered" in show,
            commitsYear = options.commitsYear
        )
        return GithubCardRender.renderStats(stats, config.layout2d, theme, options).bytes()
    }

    suspend fun renderTopLanguagesCard(
        username: String,
        theme: Heatmap2dRender.Theme,
        options: GithubLanguagesRenderOptions = GithubLanguagesRenderOptions(),
        excludeRepo: List<String> = emptyList(),
        sizeWeight: Double = 1.0,
        countWeight: Double = 0.0
    ): ByteArray {
        val topLangs = fetcher.fetchTopLanguages(username, excludeRepo, sizeWeight, countWeight)
        return GithubCardRender.renderTopLanguages(topLangs, config.layout2d, theme, options).bytes()
    }

    suspend fun renderRepoCard(
        username: String,
        repo: String,
        theme: Heatmap2dRender.Theme,
        options: GithubRepoRenderOptions = GithubRepoRenderOptions()
    ): ByteArray {
        val repoData = fetcher.fetchRepo(username, repo)
        return GithubCardRender.renderRepo(repoData, config.layout2d, theme, options).bytes()
    }
}
