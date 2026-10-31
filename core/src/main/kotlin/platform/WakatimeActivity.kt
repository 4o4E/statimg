package top.e404.statimg.platform

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

internal data class WakatimeActivity(
    val range: String,
    val byMonth: Map<Pair<Int, Int>, List<List<Pair<LocalDate, Int?>>>>
)

private const val CALCULATING_RETRY_LIMIT = 6
private const val CALCULATING_RETRY_DELAY_MILLIS = 2_000L

/** 读取首页独立公开的活动分档；不使用认证信息，也不将分档值解释为精确时长。 */
internal suspend fun fetchWakatimeActivity(client: HttpClient, user: String): WakatimeActivity? {
    val url = URLBuilder("https://wakatime.com/api/v1/users").apply {
        appendPathSegments(user, "insights", "days", "public", encodeSlash = true)
    }.build()
    repeat(CALCULATING_RETRY_LIMIT + 1) { attempt ->
        val response = client.get(url)
        if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) return null

        if (!response.status.isSuccess()) error("WakaTime 活动接口 HTTP ${response.status.value}")
        val body = response.bodyAsText()
        val data = if (response.status == HttpStatusCode.Accepted) null
            else Json.parseToJsonElement(body).jsonObject.getValue("data").jsonObject
        val ready = data != null && (
            data.getValue("is_up_to_date").jsonPrimitive.boolean ||
                data["is_up_to_date_pending_future"]?.jsonPrimitive?.boolean == true ||
                data["is_cached"]?.jsonPrimitive?.boolean == true
            )
        if (!ready) {
            if (attempt < CALCULATING_RETRY_LIMIT) {
                delay(CALCULATING_RETRY_DELAY_MILLIS)
                return@repeat
            }
            error("WakaTime 活动数据在 $CALCULATING_RETRY_LIMIT 次刷新后仍未完成计算")
        }

        val completed = checkNotNull(data)
        val range = completed.getValue("human_readable_range").jsonPrimitive.content
        val days = completed.getValue("days").jsonArray
        if (days.isEmpty()) return null
        val records = days.map { element ->
            val day = element.jsonObject
            val date = LocalDate.parse(day.getValue("date").jsonPrimitive.content)
            val total = day.getValue("total").jsonPrimitive.int
            date to total
        }
        return buildWakatimeActivity(range, records)
    }
    error("WakaTime 活动数据请求未能完成")
}

/** 保留公开日期与强度，按周一至周日组织日历；缺失日期不冒充零活动。 */
internal fun buildWakatimeActivity(
    range: String,
    days: List<Pair<LocalDate, Int>>
): WakatimeActivity? {
    if (days.isEmpty()) return null
    val values = days.associate { (date, total) -> date to levelForWakatimeTotal(total) }
    val first = values.keys.minOrNull()!!
    val last = values.keys.maxOrNull()!!
    val calendarStart = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val calendarEnd = last.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val weeks = LinkedHashMap<Pair<Int, Int>, MutableList<List<Pair<LocalDate, Int?>>>>()
    var monday = calendarStart
    while (!monday.isAfter(calendarEnd)) {
        val week = (0L..6L).map { offset ->
            val date = monday.plusDays(offset)
            date to values[date]
        }
        weeks.getOrPut(monday.year to monday.monthValue) { mutableListOf() }.add(week)
        monday = monday.plusWeeks(1)
    }
    return WakatimeActivity(range, weeks)
}

/** 沿用 WakaTime 首页六档边界；该值只决定颜色，不能累加为时长。 */
internal fun levelForWakatimeTotal(total: Int): Int = when {
    total < 3_600 -> 0
    total <= 10_800 -> 1
    total <= 28_800 -> 2
    total <= 36_000 -> 3
    total <= 43_200 -> 4
    else -> 5
}
