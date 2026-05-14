package top.e404.status.render.plugin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.e404.status.render.platform.WakatimeRender
import top.e404.status.render.config.ServerConfig
import top.e404.status.render.feature.Heatmap2dRender
import top.e404.status.render.platform.GithubLanguagesRenderOptions
import top.e404.status.render.platform.GithubRepoRenderOptions
import top.e404.status.render.platform.GithubRender
import top.e404.status.render.platform.GithubStatsRenderOptions
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

fun Application.routing(wakatimeRender: WakatimeRender, githubRender: GithubRender) = routing {
    get("/wakatime/themes") {
        call.respondText(Json.encodeToString(ServerConfig.config.themes2d.keys), ContentType.Application.Json)
    }

    get("/github/themes") {
        call.respondText(Json.encodeToString(ServerConfig.config.themes2d.keys), ContentType.Application.Json)
    }

    get("/wakatime/{type}/{username}/{range}") {
        val username = call.parameters["username"]!!
        val range = WakatimeRender.FetchRange.byName(call.parameters["range"]!!)
        if (range == null) {
            call.respond(HttpStatusCode.BadRequest, "invalid range")
            return@get
        }
        val theme = call.request.queryParameters["theme"]?.let { ServerConfig.config.themes2d[it] } ?: Heatmap2dRender.Theme.default
        try {
            when (val type = call.parameters["type"]!!.lowercase()) {
                "lang" -> {
                    val allLang = call.request.queryParameters["all"]?.toBoolean() == true
                    call.respondBytes(wakatimeRender.renderLang(username, range, allLang, theme), ContentType.Image.PNG)
                }

                "editor" -> call.respondBytes(wakatimeRender.renderEditor(username, range, theme), ContentType.Image.PNG)

                else -> call.respondText("unknown request type: $type", ContentType.Text.Plain, HttpStatusCode.BadRequest)
            }
        } catch (e: Exception) {
            call.respondText(e.message ?: "", ContentType.Text.Plain, HttpStatusCode.BadRequest)
        }
    }

    get("/github/contribution/{username}/{end}") {
        val username = call.parameters["username"]!!
        val end = try {
            call.parameters["end"]!!.let { LocalDate.parse(it) }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "invalid end date: ${e.message}")
            return@get
        }
        val theme = call.request.queryParameters["theme"]?.let {
            ServerConfig.config.themes2d[it]
        } ?: Heatmap2dRender.Theme.default
        try {
            call.respondBytes(githubRender.renderContribution2d(username, LocalDateTime.of(end, LocalTime.MIN), theme), ContentType.Image.PNG)
        } catch (e: Exception) {
            call.respondText(e.message ?: "", ContentType.Text.Plain, HttpStatusCode.BadRequest)
        }
    }

    get("/github/contribution3d/{username}") {
        val username = call.parameters["username"]!!
        try {
            val theme3d = call.request.queryParameters["theme"]?.let { theme ->
                ServerConfig.config.themes3d[theme]
            } ?: ServerConfig.config.themes3d["rainbow"]!!
            call.respondBytes(githubRender.renderContribution3d(username, theme3d), ContentType.Image.PNG)
        } catch (e: Exception) {
            call.respondText(e.message ?: "", ContentType.Text.Plain, HttpStatusCode.BadRequest)
        }
    }

    get("/github/stats/{username}") {
        val username = call.parameters["username"]!!
        val theme = call.githubCardTheme()
        val params = call.request.queryParameters
        val options = GithubStatsRenderOptions(
            hide = params["hide"].csvSet(),
            show = params["show"].csvSet(),
            showIcons = params["show_icons"].toBooleanParam(),
            hideRank = params["hide_rank"].toBooleanParam(),
            includeAllCommits = params["include_all_commits"].toBooleanParam(),
            commitsYear = params["commits_year"]?.toIntOrNull(),
            rankIcon = when (params["rank_icon"]?.lowercase()) {
                "percentile" -> GithubStatsRenderOptions.RankIcon.PERCENTILE
                else -> GithubStatsRenderOptions.RankIcon.DEFAULT
            },
            numberFormat = when (params["number_format"]?.lowercase()) {
                "long" -> GithubStatsRenderOptions.NumberFormat.LONG
                else -> GithubStatsRenderOptions.NumberFormat.SHORT
            },
            numberPrecision = params["number_precision"]?.toIntOrNull(),
            customTitle = params["custom_title"]
        )
        try {
            call.respondBytes(
                githubRender.renderStatsCard(
                    username,
                    theme,
                    options,
                    excludeRepo = params["exclude_repo"].csvList()
                ),
                ContentType.Image.PNG
            )
        } catch (e: Exception) {
            call.respondText(e.message ?: "", ContentType.Text.Plain, HttpStatusCode.BadRequest)
        }
    }

    get("/github/top-langs/{username}") {
        val username = call.parameters["username"]!!
        val theme = call.githubCardTheme()
        val params = call.request.queryParameters
        val options = GithubLanguagesRenderOptions(
            layout = when (params["layout"]?.lowercase()) {
                "compact" -> GithubLanguagesRenderOptions.Layout.COMPACT
                "donut" -> GithubLanguagesRenderOptions.Layout.DONUT
                "donut-vertical", "donut_vertical" -> GithubLanguagesRenderOptions.Layout.DONUT_VERTICAL
                "pie" -> GithubLanguagesRenderOptions.Layout.PIE
                else -> GithubLanguagesRenderOptions.Layout.NORMAL
            },
            langsCount = params["langs_count"]?.toIntOrNull(),
            hide = params["hide"].csvSet(),
            hideProgress = params["hide_progress"].toBooleanParam(),
            statsFormat = when (params["stats_format"]?.lowercase()) {
                "bytes" -> GithubLanguagesRenderOptions.StatsFormat.BYTES
                else -> GithubLanguagesRenderOptions.StatsFormat.PERCENTAGES
            },
            customTitle = params["custom_title"]
        )
        try {
            call.respondBytes(
                githubRender.renderTopLanguagesCard(
                    username,
                    theme,
                    options,
                    excludeRepo = params["exclude_repo"].csvList(),
                    sizeWeight = params["size_weight"]?.toDoubleOrNull() ?: 1.0,
                    countWeight = params["count_weight"]?.toDoubleOrNull() ?: 0.0
                ),
                ContentType.Image.PNG
            )
        } catch (e: Exception) {
            call.respondText(e.message ?: "", ContentType.Text.Plain, HttpStatusCode.BadRequest)
        }
    }

    get("/github/repo/{owner}/{repo}") {
        val owner = call.parameters["owner"]!!
        val repo = call.parameters["repo"]!!
        val theme = call.githubCardTheme()
        val params = call.request.queryParameters
        val options = GithubRepoRenderOptions(
            showOwner = params["show_owner"].toBooleanParam(),
            descriptionLinesCount = params["description_lines_count"]?.toIntOrNull()
        )
        try {
            call.respondBytes(githubRender.renderRepoCard(owner, repo, theme, options), ContentType.Image.PNG)
        } catch (e: Exception) {
            call.respondText(e.message ?: "", ContentType.Text.Plain, HttpStatusCode.BadRequest)
        }
    }
}

private fun ApplicationCall.githubCardTheme(): Heatmap2dRender.Theme =
    request.queryParameters["theme"]?.let { ServerConfig.config.themes2d[it] } ?: Heatmap2dRender.Theme.default

private fun String?.toBooleanParam(): Boolean = this?.equals("true", ignoreCase = true) == true

private fun String?.csvList(): List<String> =
    this?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()

private fun String?.csvSet(): Set<String> = csvList().toSet()
