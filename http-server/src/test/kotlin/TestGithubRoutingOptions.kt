package top.e404.status.render.test

import io.ktor.http.Parameters
import org.junit.jupiter.api.Test
import top.e404.status.render.platform.GithubLanguagesRenderOptions
import top.e404.status.render.platform.GithubStatsRenderOptions
import top.e404.status.render.plugin.githubLanguagesOptions
import top.e404.status.render.plugin.githubRepoOptions
import top.e404.status.render.plugin.githubStatsOptions
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestGithubRoutingOptions {
    @Test
    fun testGithubStatsQueryOptions() {
        val options = Parameters.build {
            append("hide", "issues, contribs")
            append("show", "reviews,prs_merged,prs_merged_percentage")
            append("show_icons", "true")
            append("hide_rank", "true")
            append("include_all_commits", "true")
            append("commits_year", "2026")
            append("rank_icon", "percentile")
            append("number_format", "long")
            append("number_precision", "2")
            append("custom_title", "Activity")
        }.githubStatsOptions()

        assertEquals(setOf("issues", "contribs"), options.hide)
        assertEquals(setOf("reviews", "prs_merged", "prs_merged_percentage"), options.show)
        assertTrue(options.showIcons)
        assertTrue(options.hideRank)
        assertTrue(options.includeAllCommits)
        assertEquals(2026, options.commitsYear)
        assertEquals(GithubStatsRenderOptions.RankIcon.PERCENTILE, options.rankIcon)
        assertEquals(GithubStatsRenderOptions.NumberFormat.LONG, options.numberFormat)
        assertEquals(2, options.numberPrecision)
        assertEquals("Activity", options.customTitle)
    }

    @Test
    fun testGithubLanguagesQueryOptions() {
        val options = Parameters.build {
            append("layout", "donut_vertical")
            append("langs_count", "8")
            append("hide", "html, css")
            append("hide_progress", "true")
            append("stats_format", "bytes")
            append("custom_title", "Languages")
        }.githubLanguagesOptions()

        assertEquals(GithubLanguagesRenderOptions.Layout.DONUT_VERTICAL, options.layout)
        assertEquals(8, options.langsCount)
        assertEquals(setOf("html", "css"), options.hide)
        assertTrue(options.hideProgress)
        assertEquals(GithubLanguagesRenderOptions.StatsFormat.BYTES, options.statsFormat)
        assertEquals("Languages", options.customTitle)
    }

    @Test
    fun testGithubLanguagesDefaultOptions() {
        val options = Parameters.Empty.githubLanguagesOptions()

        assertEquals(GithubLanguagesRenderOptions.Layout.NORMAL, options.layout)
        assertEquals(null, options.langsCount)
        assertEquals(emptySet(), options.hide)
        assertFalse(options.hideProgress)
        assertEquals(GithubLanguagesRenderOptions.StatsFormat.PERCENTAGES, options.statsFormat)
    }

    @Test
    fun testGithubRepoQueryOptions() {
        val options = Parameters.build {
            append("show_owner", "true")
            append("description_lines_count", "2")
        }.githubRepoOptions()

        assertTrue(options.showOwner)
        assertEquals(2, options.descriptionLinesCount)
    }
}
