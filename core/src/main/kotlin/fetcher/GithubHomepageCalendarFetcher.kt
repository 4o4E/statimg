package top.e404.statimg.fetcher

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import org.jsoup.Jsoup
import java.time.LocalDate

private val noContributionsPattern = Regex("No contributions on .+\\.")
private val dayContributionsPattern = Regex("([\\d,]+) contributions? on .+\\.")
private val totalContributionsPattern = Regex("([\\d,]+) contributions? in the last year")
private val contributionNumberPattern = Regex("\\d{1,3}(,\\d{3})*|\\d+")

internal data class GithubHomepageCalendar(
    val from: LocalDate,
    val to: LocalDate,
    val total: Int,
    val days: List<GithubDashboardDay>
)

/**
 * 读取 GitHub 主页公开贡献日历，遵循用户公开的贡献可见性设置。
 */
internal suspend fun fetchGithubHomepageCalendar(
    client: HttpClient,
    username: String
): GithubHomepageCalendar {
    val url = URLBuilder("https://github.com").apply {
        appendPathSegments("users", username, "contributions", encodeSlash = true)
    }.build()
    val response = client.get(url) {
        expectSuccess = false
        header(HttpHeaders.AcceptLanguage, "en-US")
    }
    check(response.status.isSuccess()) {
        "GitHub contribution calendar request failed with HTTP ${response.status.value}"
    }

    return parseGithubHomepageCalendar(response.bodyAsText())
}

internal fun parseGithubHomepageCalendar(html: String): GithubHomepageCalendar {
    val document = Jsoup.parse(html)
    val graph = document.selectFirst(".js-calendar-graph[data-from][data-to]")
        ?: error("GitHub contribution calendar date range is missing")
    val from = LocalDate.parse(graph.attr("data-from").substringBefore(' '))
    val to = LocalDate.parse(graph.attr("data-to").substringBefore(' '))
    val cells = document.select("td.ContributionCalendar-day[data-date]")
    check(cells.isNotEmpty()) { "GitHub contribution calendar is missing" }

    val calendarCellIds = HashSet<String>(cells.size)
    for (cell in cells) {
        val id = cell.id()
        check(id.isNotBlank() && calendarCellIds.add(id)) {
            "GitHub contribution calendar contains an invalid cell id"
        }
    }
    val tooltipsByCellId = HashMap<String, String>(cells.size)
    for (tooltip in document.select("tool-tip[for]")) {
        val id = tooltip.attr("for")
        if (id in calendarCellIds) {
            check(tooltipsByCellId.put(id, tooltip.text()) == null) {
                "GitHub contribution calendar has duplicate tooltip references"
            }
        }
    }

    val days = ArrayList<GithubDashboardDay>(cells.size)
    for (cell in cells) {
        val date = LocalDate.parse(cell.attr("data-date"))
        val id = cell.id()
        val tooltip = tooltipsByCellId[id]
            ?: error("GitHub contribution calendar cell is missing its tooltip")
        val count = parseDayCount(tooltip)
        days.add(GithubDashboardDay(date, count))
    }

    days.sortBy { it.date }
    // 零贡献日期也必须覆盖主页声明的完整范围，不能由剩余单元格推断年度边界。
    check(days.first().date == from && days.last().date == to) {
        "GitHub contribution calendar does not cover its declared date range"
    }
    var previousDay = days.first().date.toEpochDay()
    for (index in 1 until days.size) {
        val currentDay = days[index].date.toEpochDay()
        check(currentDay == previousDay + 1) {
            "GitHub contribution calendar dates are not continuous"
        }
        previousDay = currentDay
    }

    val headingCounts = document.select("h2").mapNotNull { parseTotal(it.text()) }
    check(headingCounts.size == 1) { "GitHub contribution calendar total is missing or ambiguous" }
    val total = headingCounts.single()
    check(days.sumOf { it.count } == total) {
        "GitHub contribution calendar daily counts do not match its total"
    }

    return GithubHomepageCalendar(
        from = from,
        to = to,
        total = total,
        days = days
    )
}

private fun parseDayCount(tooltip: String): Int {
    if (noContributionsPattern.matches(tooltip)) return 0

    val match = dayContributionsPattern.matchEntire(tooltip)
        ?: error("GitHub contribution calendar contains an invalid day tooltip")
    return match.groupValues[1].parseContributionNumber()
}

private fun parseTotal(heading: String): Int? {
    val match = totalContributionsPattern.matchEntire(heading) ?: return null
    return match.groupValues[1].parseContributionNumber()
}

private fun String.parseContributionNumber(): Int {
    check(contributionNumberPattern.matches(this)) {
        "GitHub contribution calendar contains an invalid contribution count"
    }
    return replace(",", "").toIntOrNull()
        ?: error("GitHub contribution calendar contribution count is out of range")
}
