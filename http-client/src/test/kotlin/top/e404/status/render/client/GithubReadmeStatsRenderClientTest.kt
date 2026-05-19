package top.e404.status.render.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.ClientEngineClosedException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GithubReadmeStatsRenderClientTest {
    @Test
    fun `github stats builds typed query`() = runBlocking {
        var requestedUrl = ""
        val client = testClient { request ->
            requestedUrl = request.url.toString()
            respondPng()
        }

        val bytes = client.githubStats(
            GithubStatsRequest(
                username = "octocat",
                theme = "tokyonight",
                hide = linkedSetOf("issues", "contribs"),
                show = linkedSetOf("reviews", "prs_merged"),
                showIcons = true,
                hideRank = true,
                includeAllCommits = true,
                commitsYear = 2026,
                excludeRepo = listOf("demo", "test"),
                rankIcon = GithubStatsRankIcon.PERCENTILE,
                numberFormat = GithubNumberFormat.LONG,
                numberPrecision = 2,
                customTitle = "Activity"
            )
        )

        assertEquals(listOf<Byte>(0, 1, 2, 3), bytes.toList())
        assertTrue(requestedUrl.startsWith("http://render.test/api/github/stats/octocat?"))
        assertTrue(requestedUrl.contains("theme=tokyonight"))
        assertTrue(requestedUrl.contains("hide=issues%2Ccontribs"))
        assertTrue(requestedUrl.contains("show=reviews%2Cprs_merged"))
        assertTrue(requestedUrl.contains("show_icons=true"))
        assertTrue(requestedUrl.contains("hide_rank=true"))
        assertTrue(requestedUrl.contains("include_all_commits=true"))
        assertTrue(requestedUrl.contains("commits_year=2026"))
        assertTrue(requestedUrl.contains("exclude_repo=demo%2Ctest"))
        assertTrue(requestedUrl.contains("rank_icon=percentile"))
        assertTrue(requestedUrl.contains("number_format=long"))
        assertTrue(requestedUrl.contains("number_precision=2"))
        assertTrue(requestedUrl.contains("custom_title=Activity"))
    }

    @Test
    fun `top languages builds layout query`() = runBlocking {
        var requestedUrl = ""
        val client = testClient { request ->
            requestedUrl = request.url.toString()
            respondPng()
        }

        client.githubTopLanguages(
            GithubTopLanguagesRequest(
                username = "octocat",
                layout = GithubTopLanguagesLayout.DONUT_VERTICAL,
                languagesCount = 8,
                hide = linkedSetOf("html", "css"),
                hideProgress = true,
                sizeWeight = 0.7,
                countWeight = 0.3,
                statsFormat = GithubLanguageStatsFormat.BYTES,
                customTitle = "Languages"
            )
        )

        assertTrue(requestedUrl.startsWith("http://render.test/api/github/top-langs/octocat?"))
        assertTrue(requestedUrl.contains("layout=donut-vertical"))
        assertTrue(requestedUrl.contains("langs_count=8"))
        assertTrue(requestedUrl.contains("hide=html%2Ccss"))
        assertTrue(requestedUrl.contains("hide_progress=true"))
        assertTrue(requestedUrl.contains("size_weight=0.7"))
        assertTrue(requestedUrl.contains("count_weight=0.3"))
        assertTrue(requestedUrl.contains("stats_format=bytes"))
        assertTrue(requestedUrl.contains("custom_title=Languages"))
    }

    @Test
    fun `wakatime and contribution endpoints build paths`() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val client = testClient { request ->
            requestedUrls += request.url.toString()
            respondPng()
        }

        client.wakatimeLanguage("404E", WakatimeRange.LAST_7_DAYS, theme = "dark", allLanguages = true)
        client.wakatimeEditor("404E", WakatimeRange.LAST_30_DAYS, theme = "vue")
        client.githubContribution("octocat", LocalDate.of(2026, 5, 17), theme = "dark")
        client.githubContribution3d("octocat", theme = "rainbow")
        client.githubRepo("octocat", "Hello-World", theme = "dark")

        assertEquals("http://render.test/api/wakatime/lang/404E/7d?theme=dark&all=true", requestedUrls[0])
        assertEquals("http://render.test/api/wakatime/editor/404E/30d?theme=vue", requestedUrls[1])
        assertEquals("http://render.test/api/github/contribution/octocat/2026-05-17?theme=dark", requestedUrls[2])
        assertEquals("http://render.test/api/github/contribution3d/octocat?theme=rainbow", requestedUrls[3])
        assertEquals("http://render.test/api/github/repo/octocat/Hello-World?theme=dark", requestedUrls[4])
    }

    @Test
    fun `themes parse json array`() = runBlocking {
        val client = testClient {
            respond(
                content = """["default","tokyonight"]""",
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        assertEquals(listOf("default", "tokyonight"), client.githubThemes())
    }

    @Test
    fun `supports ktor url base`() = runBlocking {
        var requestedUrl = ""
        val httpClient = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requestedUrl = request.url.toString()
                    respondPng()
                }
            }
        }
        val client = GithubReadmeStatsRenderClient(Url("http://render.test/api"), httpClient)

        client.githubRepo("4o4E", "github-readme-stats-render")

        assertEquals("http://render.test/api/github/repo/4o4E/github-readme-stats-render", requestedUrl)
    }

    @Test
    fun `http errors expose status and response text`() = runBlocking {
        val client = testClient {
            respond("bad request", status = HttpStatusCode.BadRequest)
        }

        val error = assertFailsWith<StatusRenderHttpException> {
            client.githubStats("bad")
        }

        assertEquals(HttpStatusCode.BadRequest, error.status)
        assertEquals("bad request", error.responseText)
    }

    @Test
    fun `close closes owned default http client`() = runBlocking {
        val client = GithubReadmeStatsRenderClient("http://render.test/api")

        client.close()

        assertFailsWith<ClientEngineClosedException> {
            client.githubThemes()
        }
    }

    @Test
    fun `close keeps external http client alive`() = runBlocking {
        val httpClient = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond("""["default"]""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }
        val client = GithubReadmeStatsRenderClient("http://render.test/api", httpClient)

        client.close()

        assertEquals(listOf("default"), client.githubThemes())
        httpClient.close()
    }

    private fun testClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): GithubReadmeStatsRenderClient {
        val httpClient = HttpClient(MockEngine) {
            engine {
                addHandler(handler)
            }
        }
        return GithubReadmeStatsRenderClient("http://render.test/api", httpClient)
    }

    private fun MockRequestHandleScope.respondPng(): HttpResponseData =
        respond(
            content = ByteArray(4) { it.toByte() },
            headers = headersOf(HttpHeaders.ContentType, "image/png")
        )
}
