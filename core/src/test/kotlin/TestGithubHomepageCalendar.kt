package top.e404.statimg.fetcher

import kotlin.test.Test
import kotlin.test.assertFailsWith

class TestGithubHomepageCalendar {
    @Test
    fun rejectsMissingZeroContributionTail() {
        val html = truncatedCalendar("2025-11-02", "November 2nd", "2025-11-03", "November 3rd")
        assertFailsWith<IllegalStateException> { parseGithubHomepageCalendar(html) }
    }

    @Test
    fun rejectsMissingZeroContributionHead() {
        val html = truncatedCalendar("2026-11-01", "November 1st", "2026-11-02", "November 2nd")
        assertFailsWith<IllegalStateException> { parseGithubHomepageCalendar(html) }
    }

    /**
     * 保留octocat公开主页的年度声明与真实零贡献日期，截断其他日期。
     * 总数仍为零且剩余日期连续，必须依据页面声明的范围识别缺页。
     * 来源：https://github.com/users/octocat/contributions（2026-11-02）。
     */
    private fun truncatedCalendar(firstDate: String, firstLabel: String, lastDate: String, lastLabel: String): String = """
        <div class="js-yearly-contributions">
          <h2>0 contributions in the last year</h2>
          <div class="js-calendar-graph" data-from="2025-11-02 00:00:00 UTC" data-to="2026-11-02 23:59:59 UTC">
            <table><tbody><tr>
              <td class="ContributionCalendar-day" id="first-day" data-date="$firstDate"></td>
              <td class="ContributionCalendar-day" id="last-day" data-date="$lastDate"></td>
            </tr></tbody></table>
            <tool-tip for="first-day">No contributions on $firstLabel.</tool-tip>
            <tool-tip for="last-day">No contributions on $lastLabel.</tool-tip>
          </div>
        </div>
    """.trimIndent()
}
