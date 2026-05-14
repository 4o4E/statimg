package top.e404.status.render.test

import org.junit.jupiter.api.Test
import top.e404.status.render.fetcher.GithubLanguageStat
import top.e404.status.render.fetcher.GithubRank
import top.e404.status.render.fetcher.GithubRepoCardData
import top.e404.status.render.fetcher.GithubStats
import top.e404.status.render.platform.GithubCardRender
import top.e404.status.render.platform.GithubLanguagesRenderOptions
import top.e404.status.render.platform.GithubRankCalculator
import top.e404.status.render.platform.GithubRepoRenderOptions
import top.e404.status.render.platform.GithubStatsRenderOptions
import kotlin.test.assertEquals

class TestGithubCardRender {
    @Test
    fun testRankCalculatorMatchesUpstreamFixtures() {
        GithubRankCalculator.calculate(
            allCommits = false,
            commits = 0,
            prs = 0,
            issues = 0,
            reviews = 0,
            stars = 0,
            followers = 0
        ).assertRank("C", 100.0)

        GithubRankCalculator.calculate(
            allCommits = false,
            commits = 250,
            prs = 50,
            issues = 25,
            reviews = 10,
            stars = 50,
            followers = 10
        ).assertRank("B+", 46.09375)

        GithubRankCalculator.calculate(
            allCommits = false,
            commits = 1300,
            prs = 1500,
            issues = 4500,
            reviews = 1000,
            stars = 600000,
            followers = 50000
        ).assertRank("S", 0.4578556547153667)
    }

    @Test
    fun testRenderStatsCardWithFixedData() {
        val image = GithubCardRender.renderStats(
            stats = statsFixture(),
            layout = TestRenderFixtures.layout2d,
            theme = TestRenderFixtures.theme2d,
            options = GithubStatsRenderOptions(
                show = setOf("reviews", "prs_merged", "prs_merged_percentage"),
                showIcons = true
            )
        )

        TestRenderFixtures.assertPngImage(image, minBytes = 1024)
    }

    @Test
    fun testRenderTopLanguagesLayoutsWithFixedData() {
        for (layout in GithubLanguagesRenderOptions.Layout.entries) {
            val image = GithubCardRender.renderTopLanguages(
                topLangs = languagesFixture(),
                layoutConfig = TestRenderFixtures.layout2d,
                theme = TestRenderFixtures.theme2d,
                options = GithubLanguagesRenderOptions(layout = layout)
            )

            TestRenderFixtures.assertPngImage(image, minBytes = 1024)
        }
    }

    @Test
    fun testRenderRepoCardWithFixedData() {
        val image = GithubCardRender.renderRepo(
            repo = GithubRepoCardData(
                name = "github-readme-stats-render",
                nameWithOwner = "4o4E/github-readme-stats-render",
                description = "Offline PNG renderer for GitHub readme stats cards and contribution heatmaps.",
                primaryLanguageName = "Kotlin",
                primaryLanguageColor = "#A97BFF",
                isArchived = false,
                isTemplate = false,
                starCount = 1234,
                forkCount = 56
            ),
            layout = TestRenderFixtures.layout2d,
            theme = TestRenderFixtures.theme2d,
            options = GithubRepoRenderOptions(showOwner = true)
        )

        TestRenderFixtures.assertPngImage(image, minBytes = 1024)
    }

    private fun GithubRank.assertRank(level: String, percentile: Double) {
        assertEquals(level, this.level)
        assertEquals(percentile, this.percentile, 0.0000000001)
    }

    private fun statsFixture() = GithubStats(
        name = "Octocat",
        totalPRs = 210,
        totalPRsMerged = 120,
        mergedPRsPercentage = 57.142857,
        totalReviews = 42,
        totalCommits = 1200,
        totalIssues = 98,
        totalStars = 3456,
        contributedTo = 31,
        followers = 88,
        rank = GithubRankCalculator.calculate(
            allCommits = true,
            commits = 1200,
            prs = 210,
            issues = 98,
            reviews = 42,
            stars = 3456,
            followers = 88
        )
    )

    private fun languagesFixture() = listOf(
        GithubLanguageStat("Kotlin", "#A97BFF", 450000.0, 8),
        GithubLanguageStat("TypeScript", "#3178c6", 310000.0, 6),
        GithubLanguageStat("JavaScript", "#f1e05a", 180000.0, 5),
        GithubLanguageStat("HTML", "#e34c26", 90000.0, 3),
        GithubLanguageStat("CSS", "#563d7c", 70000.0, 3),
        GithubLanguageStat("Shell", "#89e051", 20000.0, 2)
    )
}
