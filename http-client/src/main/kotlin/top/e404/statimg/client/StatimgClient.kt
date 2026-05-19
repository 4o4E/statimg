package top.e404.statimg.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import kotlinx.serialization.decodeFromString
import java.time.LocalDate

class StatimgClient private constructor(
    private val server: StatimgServer,
    private val httpClient: HttpClient,
    private val closeHttpClient: Boolean,
) : AutoCloseable {
    constructor(server: StatimgServer) : this(server, defaultHttpClient(), closeHttpClient = true)

    constructor(server: StatimgServer, httpClient: HttpClient) : this(server, httpClient, closeHttpClient = false)

    constructor(baseUrl: String) : this(StatimgServer(baseUrl))

    constructor(baseUrl: String, httpClient: HttpClient) : this(StatimgServer(baseUrl), httpClient)

    constructor(baseUrl: Url) : this(StatimgServer(baseUrl))

    constructor(baseUrl: Url, httpClient: HttpClient) : this(StatimgServer(baseUrl), httpClient)

    suspend fun wakatimeThemes(): List<String> = getJson(listOf("wakatime", "themes"))

    suspend fun githubThemes(): List<String> = getJson(listOf("github", "themes"))

    suspend fun wakatimeLanguage(request: WakatimeLanguageRequest): ByteArray =
        getBytes(
            pathSegments = listOf("wakatime", "lang", request.username, request.range.path),
            queryParameters = request.toQueryParameters()
        )

    suspend fun wakatimeLanguage(
        username: String,
        range: WakatimeRange,
        theme: String? = null,
        allLanguages: Boolean? = null,
    ): ByteArray =
        wakatimeLanguage(WakatimeLanguageRequest(username, range, theme, allLanguages))

    suspend fun wakatimeEditor(request: WakatimeEditorRequest): ByteArray =
        getBytes(
            pathSegments = listOf("wakatime", "editor", request.username, request.range.path),
            queryParameters = request.toQueryParameters()
        )

    suspend fun wakatimeEditor(
        username: String,
        range: WakatimeRange,
        theme: String? = null,
    ): ByteArray =
        wakatimeEditor(WakatimeEditorRequest(username, range, theme))

    suspend fun githubContribution(request: GithubContributionRequest): ByteArray =
        getBytes(
            pathSegments = listOf("github", "contribution", request.username, request.end.toString()),
            queryParameters = request.toQueryParameters()
        )

    suspend fun githubContribution(
        username: String,
        end: LocalDate,
        theme: String? = null,
    ): ByteArray =
        githubContribution(GithubContributionRequest(username, end, theme))

    suspend fun githubContribution3d(request: GithubContribution3dRequest): ByteArray =
        getBytes(
            pathSegments = listOf("github", "contribution3d", request.username),
            queryParameters = request.toQueryParameters()
        )

    suspend fun githubContribution3d(
        username: String,
        theme: String? = null,
    ): ByteArray =
        githubContribution3d(GithubContribution3dRequest(username, theme))

    suspend fun githubStats(request: GithubStatsRequest): ByteArray =
        getBytes(
            pathSegments = listOf("github", "stats", request.username),
            queryParameters = request.toQueryParameters()
        )

    suspend fun githubStats(username: String, theme: String? = null): ByteArray =
        githubStats(GithubStatsRequest(username, theme))

    suspend fun githubTopLanguages(request: GithubTopLanguagesRequest): ByteArray =
        getBytes(
            pathSegments = listOf("github", "top-langs", request.username),
            queryParameters = request.toQueryParameters()
        )

    suspend fun githubTopLanguages(username: String, theme: String? = null): ByteArray =
        githubTopLanguages(GithubTopLanguagesRequest(username, theme))

    suspend fun githubRepo(request: GithubRepoRequest): ByteArray =
        getBytes(
            pathSegments = listOf("github", "repo", request.owner, request.repo),
            queryParameters = request.toQueryParameters()
        )

    suspend fun githubRepo(owner: String, repo: String, theme: String? = null): ByteArray =
        githubRepo(GithubRepoRequest(owner, repo, theme))

    override fun close() {
        // 只关闭本模块创建的默认客户端，避免影响下游复用的 HttpClient。
        if (closeHttpClient) httpClient.close()
    }

    private suspend inline fun <reified T> getJson(
        pathSegments: List<String>,
        queryParameters: Map<String, String> = emptyMap(),
    ): T {
        val response = httpClient.get(buildUrl(pathSegments, queryParameters))
        if (!response.status.isSuccess()) throw response.toException()
        return ClientJson.instance.decodeFromString(response.bodyAsText())
    }

    private suspend fun getBytes(
        pathSegments: List<String>,
        queryParameters: Map<String, String> = emptyMap(),
    ): ByteArray {
        val response = httpClient.get(buildUrl(pathSegments, queryParameters))
        if (!response.status.isSuccess()) throw response.toException()
        return response.readBytes()
    }

    private fun buildUrl(
        pathSegments: List<String>,
        queryParameters: Map<String, String> = emptyMap(),
    ): String =
        URLBuilder(Url(server.baseUrl)).apply {
            appendPathSegments(pathSegments)
            queryParameters.forEach { (name, value) ->
                parameters.append(name, value)
            }
        }.buildString()

    private suspend fun HttpResponse.toException(): StatimgHttpException =
        StatimgHttpException(status, bodyAsText())

    companion object {
        fun defaultHttpClient(): HttpClient = HttpClient(OkHttp)
    }
}

class StatimgHttpException(
    val status: HttpStatusCode,
    val responseText: String,
) : RuntimeException("statimg request failed: ${status.value} ${status.description}; $responseText")
