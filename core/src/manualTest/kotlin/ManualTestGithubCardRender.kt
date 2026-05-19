package top.e404.statimg.manual

import top.e404.statimg.fetcher.GithubLanguageStat
import top.e404.statimg.fetcher.GithubRepoCardData
import top.e404.statimg.fetcher.GithubStats
import top.e404.statimg.platform.GithubCardRender
import top.e404.statimg.platform.GithubLanguagesRenderOptions
import top.e404.statimg.platform.GithubRankCalculator
import top.e404.statimg.platform.GithubRepoRenderOptions
import top.e404.statimg.platform.GithubStatsRenderOptions
import top.e404.tavolo.util.bytes
import java.io.File
import kotlin.test.Test

class ManualTestGithubCardRender {
    @Test
    fun renderGithubCardBatch() {
        write(
            "github_stats_default.png",
            GithubCardRender.renderStats(
                stats = statsFixture(),
                layout = ManualTestConfig.config.layout2d,
                theme = ManualTestConfig.themes2d,
                options = GithubStatsRenderOptions()
            ).bytes()
        )

        write(
            "github_stats_icons_percentile.png",
            GithubCardRender.renderStats(
                stats = statsFixture(),
                layout = ManualTestConfig.config.layout2d,
                theme = ManualTestConfig.themes2d,
                options = GithubStatsRenderOptions(
                    show = setOf("reviews", "prs_merged", "prs_merged_percentage", "discussions_started", "discussions_answered"),
                    showIcons = true,
                    includeAllCommits = true,
                    rankIcon = GithubStatsRenderOptions.RankIcon.PERCENTILE,
                    numberPrecision = 2
                )
            ).bytes()
        )

        write(
            "github_stats_no_rank.png",
            GithubCardRender.renderStats(
                stats = statsFixture(),
                layout = ManualTestConfig.config.layout2d,
                theme = ManualTestConfig.themes2d,
                options = GithubStatsRenderOptions(
                    hideRank = true,
                    hide = setOf("contribs")
                )
            ).bytes()
        )

        write(
            "github_stats_long_custom_title.png",
            GithubCardRender.renderStats(
                stats = statsFixture(),
                layout = ManualTestConfig.config.layout2d,
                theme = ManualTestConfig.themes2d,
                options = GithubStatsRenderOptions(
                    showIcons = true,
                    numberFormat = GithubStatsRenderOptions.NumberFormat.LONG,
                    customTitle = "GitHub Activity Overview"
                )
            ).bytes()
        )

        for (layout in GithubLanguagesRenderOptions.Layout.entries) {
            write(
                "github_top_langs_${layout.name.lowercase()}.png",
                GithubCardRender.renderTopLanguages(
                    topLangs = languagesFixture(),
                    layoutConfig = ManualTestConfig.config.layout2d,
                    theme = ManualTestConfig.themes2d,
                    options = GithubLanguagesRenderOptions(layout = layout)
                ).bytes()
            )
        }

        write(
            "github_top_langs_compact_bytes.png",
            GithubCardRender.renderTopLanguages(
                topLangs = languagesFixture(),
                layoutConfig = ManualTestConfig.config.layout2d,
                theme = ManualTestConfig.themes2d,
                options = GithubLanguagesRenderOptions(
                    layout = GithubLanguagesRenderOptions.Layout.COMPACT,
                    statsFormat = GithubLanguagesRenderOptions.StatsFormat.BYTES,
                    langsCount = 8
                )
            ).bytes()
        )

        write(
            "github_top_langs_normal_hide_progress.png",
            GithubCardRender.renderTopLanguages(
                topLangs = languagesFixture(),
                layoutConfig = ManualTestConfig.config.layout2d,
                theme = ManualTestConfig.themes2d,
                options = GithubLanguagesRenderOptions(
                    layout = GithubLanguagesRenderOptions.Layout.NORMAL,
                    hideProgress = true
                )
            ).bytes()
        )

        write(
            "github_top_langs_donut_bytes.png",
            GithubCardRender.renderTopLanguages(
                topLangs = languagesFixture(),
                layoutConfig = ManualTestConfig.config.layout2d,
                theme = ManualTestConfig.themes2d,
                options = GithubLanguagesRenderOptions(
                    layout = GithubLanguagesRenderOptions.Layout.DONUT,
                    statsFormat = GithubLanguagesRenderOptions.StatsFormat.BYTES
                )
            ).bytes()
        )

        write(
            "github_top_langs_empty.png",
            GithubCardRender.renderTopLanguages(
                topLangs = languagesFixture(),
                layoutConfig = ManualTestConfig.config.layout2d,
                theme = ManualTestConfig.themes2d,
                options = GithubLanguagesRenderOptions(
                    hide = languagesFixture().map { it.name }.toSet()
                )
            ).bytes()
        )

        write(
            "github_repo_default.png",
            GithubCardRender.renderRepo(
                repo = repoFixture(),
                layout = ManualTestConfig.config.layout2d,
                theme = ManualTestConfig.themes2d
            ).bytes()
        )

        write(
            "github_repo_owner_archived.png",
            GithubCardRender.renderRepo(
                repo = repoFixture().copy(isArchived = true),
                layout = ManualTestConfig.config.layout2d,
                theme = ManualTestConfig.themes2d,
                options = GithubRepoRenderOptions(showOwner = true, descriptionLinesCount = 2)
            ).bytes()
        )

        write(
            "github_repo_template_minimal.png",
            GithubCardRender.renderRepo(
                repo = repoFixture().copy(
                    description = null,
                    primaryLanguageName = null,
                    primaryLanguageColor = null,
                    isTemplate = true
                ),
                layout = ManualTestConfig.config.layout2d,
                theme = ManualTestConfig.themes2d,
                options = GithubRepoRenderOptions(descriptionLinesCount = 1)
            ).bytes()
        )
    }

    private fun write(name: String, bytes: ByteArray) {
        File(name).writeBytes(bytes)
    }

    private fun statsFixture() = GithubStats(
        name = "Octocat",
        totalPRs = 248,
        totalPRsMerged = 161,
        mergedPRsPercentage = 64.919,
        totalReviews = 57,
        totalCommits = 1824,
        totalIssues = 113,
        totalStars = 12876,
        totalDiscussionsStarted = 19,
        totalDiscussionsAnswered = 31,
        contributedTo = 42,
        followers = 734,
        rank = GithubRankCalculator.calculate(
            allCommits = true,
            commits = 1824,
            prs = 248,
            issues = 113,
            reviews = 57,
            stars = 12876,
            followers = 734
        )
    )

    private fun languagesFixture() = listOf(
        GithubLanguageStat("Kotlin", "#A97BFF", 520000.0, 9),
        GithubLanguageStat("TypeScript", "#3178c6", 418000.0, 8),
        GithubLanguageStat("JavaScript", "#f1e05a", 260000.0, 7),
        GithubLanguageStat("Java", "#b07219", 120000.0, 4),
        GithubLanguageStat("HTML", "#e34c26", 98000.0, 3),
        GithubLanguageStat("CSS", "#563d7c", 82000.0, 3),
        GithubLanguageStat("Shell", "#89e051", 36000.0, 2),
        GithubLanguageStat("Dockerfile", "#384d54", 17000.0, 1)
    )

    private fun repoFixture() = GithubRepoCardData(
        name = "statimg",
        nameWithOwner = "4o4E/statimg",
        description = "Offline PNG renderer for GitHub readme stats cards, language charts, repository cards, and contribution heatmaps.",
        primaryLanguageName = "Kotlin",
        primaryLanguageColor = "#A97BFF",
        isArchived = false,
        isTemplate = false,
        starCount = 12876,
        forkCount = 342
    )
}
