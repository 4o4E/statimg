package top.e404.status.render.client

import io.ktor.http.Url
import java.time.LocalDate

data class StatusRenderServer(
    val baseUrl: String,
) {
    constructor(baseUrl: Url) : this(baseUrl.toString())
}

data class WakatimeRange(val path: String) {
    companion object {
        val LAST_7_DAYS: WakatimeRange = WakatimeRange("7d")
        val LAST_30_DAYS: WakatimeRange = WakatimeRange("30d")
        val LAST_6_MONTHS: WakatimeRange = WakatimeRange("6m")
        val LAST_YEAR: WakatimeRange = WakatimeRange("year")
        val ALL_TIME: WakatimeRange = WakatimeRange("all")
    }
}

data class WakatimeLanguageRequest(
    val username: String,
    val range: WakatimeRange,
    val theme: String? = null,
    val allLanguages: Boolean? = null,
    val extraQueryParameters: Map<String, String> = emptyMap(),
) {
    fun toQueryParameters(): Map<String, String> =
        linkedMapOf<String, String>().also { parameters ->
            theme?.let { parameters["theme"] = it }
            allLanguages?.let { parameters["all"] = it.toString() }
            parameters.putAll(extraQueryParameters)
        }
}

data class WakatimeEditorRequest(
    val username: String,
    val range: WakatimeRange,
    val theme: String? = null,
    val extraQueryParameters: Map<String, String> = emptyMap(),
) {
    fun toQueryParameters(): Map<String, String> =
        linkedMapOf<String, String>().also { parameters ->
            theme?.let { parameters["theme"] = it }
            parameters.putAll(extraQueryParameters)
        }
}

data class GithubContributionRequest(
    val username: String,
    val end: LocalDate,
    val theme: String? = null,
    val extraQueryParameters: Map<String, String> = emptyMap(),
) {
    fun toQueryParameters(): Map<String, String> =
        linkedMapOf<String, String>().also { parameters ->
            theme?.let { parameters["theme"] = it }
            parameters.putAll(extraQueryParameters)
        }
}

data class GithubContribution3dRequest(
    val username: String,
    val theme: String? = null,
    val extraQueryParameters: Map<String, String> = emptyMap(),
) {
    fun toQueryParameters(): Map<String, String> =
        linkedMapOf<String, String>().also { parameters ->
            theme?.let { parameters["theme"] = it }
            parameters.putAll(extraQueryParameters)
        }
}

enum class GithubStatsRankIcon(val queryValue: String) {
    DEFAULT("default"),
    PERCENTILE("percentile"),
}

enum class GithubNumberFormat(val queryValue: String) {
    SHORT("short"),
    LONG("long"),
}

data class GithubStatsRequest(
    val username: String,
    val theme: String? = null,
    val hide: Set<String> = emptySet(),
    val show: Set<String> = emptySet(),
    val showIcons: Boolean? = null,
    val hideRank: Boolean? = null,
    val includeAllCommits: Boolean? = null,
    val commitsYear: Int? = null,
    val excludeRepo: List<String> = emptyList(),
    val rankIcon: GithubStatsRankIcon? = null,
    val numberFormat: GithubNumberFormat? = null,
    val numberPrecision: Int? = null,
    val customTitle: String? = null,
    val extraQueryParameters: Map<String, String> = emptyMap(),
) {
    fun toQueryParameters(): Map<String, String> =
        linkedMapOf<String, String>().also { parameters ->
            theme?.let { parameters["theme"] = it }
            hide.takeIf { it.isNotEmpty() }?.let { parameters["hide"] = it.joinToString(",") }
            show.takeIf { it.isNotEmpty() }?.let { parameters["show"] = it.joinToString(",") }
            showIcons?.let { parameters["show_icons"] = it.toString() }
            hideRank?.let { parameters["hide_rank"] = it.toString() }
            includeAllCommits?.let { parameters["include_all_commits"] = it.toString() }
            commitsYear?.let { parameters["commits_year"] = it.toString() }
            excludeRepo.takeIf { it.isNotEmpty() }?.let { parameters["exclude_repo"] = it.joinToString(",") }
            rankIcon?.let { parameters["rank_icon"] = it.queryValue }
            numberFormat?.let { parameters["number_format"] = it.queryValue }
            numberPrecision?.let { parameters["number_precision"] = it.toString() }
            customTitle?.let { parameters["custom_title"] = it }
            parameters.putAll(extraQueryParameters)
        }
}

enum class GithubTopLanguagesLayout(val queryValue: String) {
    NORMAL("normal"),
    COMPACT("compact"),
    DONUT("donut"),
    DONUT_VERTICAL("donut-vertical"),
    PIE("pie"),
}

enum class GithubLanguageStatsFormat(val queryValue: String) {
    PERCENTAGES("percentages"),
    BYTES("bytes"),
}

data class GithubTopLanguagesRequest(
    val username: String,
    val theme: String? = null,
    val layout: GithubTopLanguagesLayout? = null,
    val languagesCount: Int? = null,
    val hide: Set<String> = emptySet(),
    val hideProgress: Boolean? = null,
    val excludeRepo: List<String> = emptyList(),
    val sizeWeight: Double? = null,
    val countWeight: Double? = null,
    val statsFormat: GithubLanguageStatsFormat? = null,
    val customTitle: String? = null,
    val extraQueryParameters: Map<String, String> = emptyMap(),
) {
    fun toQueryParameters(): Map<String, String> =
        linkedMapOf<String, String>().also { parameters ->
            theme?.let { parameters["theme"] = it }
            layout?.let { parameters["layout"] = it.queryValue }
            languagesCount?.let { parameters["langs_count"] = it.toString() }
            hide.takeIf { it.isNotEmpty() }?.let { parameters["hide"] = it.joinToString(",") }
            hideProgress?.let { parameters["hide_progress"] = it.toString() }
            excludeRepo.takeIf { it.isNotEmpty() }?.let { parameters["exclude_repo"] = it.joinToString(",") }
            sizeWeight?.let { parameters["size_weight"] = it.toString() }
            countWeight?.let { parameters["count_weight"] = it.toString() }
            statsFormat?.let { parameters["stats_format"] = it.queryValue }
            customTitle?.let { parameters["custom_title"] = it }
            parameters.putAll(extraQueryParameters)
        }
}

data class GithubRepoRequest(
    val owner: String,
    val repo: String,
    val theme: String? = null,
    val showOwner: Boolean? = null,
    val descriptionLinesCount: Int? = null,
    val extraQueryParameters: Map<String, String> = emptyMap(),
) {
    fun toQueryParameters(): Map<String, String> =
        linkedMapOf<String, String>().also { parameters ->
            theme?.let { parameters["theme"] = it }
            showOwner?.let { parameters["show_owner"] = it.toString() }
            descriptionLinesCount?.let { parameters["description_lines_count"] = it.toString() }
            parameters.putAll(extraQueryParameters)
        }
}
