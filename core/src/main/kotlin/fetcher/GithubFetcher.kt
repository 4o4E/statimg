package top.e404.statimg.fetcher

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import top.e404.statimg.IConfig
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.pow

class GithubFetcher(val config: IConfig) {
    private companion object {
        const val URL = "https://api.github.com/graphql"
        const val MAX_REPOS_ONE_QUERY = 100
    }

    private val client inline get() = config.client
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 获取用户在指定时间段内的提交数量
     */
    suspend fun fetchCommitCount(username: String, end: LocalDateTime): GhResp<GhUserResp> {
        val query = $$"""
            query userContributions($username: String!, $from: DateTime!, $to: DateTime!) {
                user(login: $username) {
                    contributionsCollection(from: $from, to: $to) {
                        contributionCalendar {
                            weeks {
                                contributionDays {
                                    contributionCount
                                    date
                                }
                            }
                        }
                    }
                }
            }
        """.replace(Regex("\\s{2,}"), " ")
        return client.post(URL) {
            header("Authorization", "bearer ${config.githubToken}")
            setBody(Json.encodeToString(buildJsonObject {
                put("query", JsonPrimitive(query))
                put("variables", buildJsonObject {
                    put("username", username)
                    put("from", end.minusYears(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    put("to", end.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                })
            }))
        }.bodyAsText().let { Json.decodeFromString<GhResp<GhUserResp>>(it) }
    }

    /**
     * 获取用户提交详情
     */
    suspend fun fetchDetail(userName: String, maxRepos: Int): GhUser? {
        val res1 = fetchDetailFirst(userName)
        val user = res1.data.user ?: return null
        val repos = user.repositories!!
        val nodes = repos.nodes.toMutableList()
        if (nodes.size == MAX_REPOS_ONE_QUERY) {
            val edges = repos.edges
            var cursor = edges.last().cursor
            while (nodes.size < maxRepos) {
                val res2 = fetchDetailNext(userName, cursor)
                val repos2 = res2.data.user?.repositories ?: break
                nodes.addAll(repos2.nodes)
                if (repos2.nodes.size != MAX_REPOS_ONE_QUERY) {
                    break
                }
                cursor = repos2.edges.last().cursor
            }
        }
        return user

    }

    private suspend fun fetchDetailFirst(username: String): GhResp<GhUserResp> {
        val query = $$"""
            query($login: String!) {
                user(login: $login) {
                    contributionsCollection {
                        contributionCalendar {
                            isHalloween
                            totalContributions
                            weeks {
                                contributionDays {
                                    contributionCount
                                    contributionLevel
                                    date
                                }
                            }
                        }
                        commitContributionsByRepository(maxRepositories: $${MAX_REPOS_ONE_QUERY}) {
                            repository {
                                primaryLanguage {
                                    name
                                    color
                                }
                            }
                            contributions {
                                totalCount
                            }
                        }
                        totalCommitContributions
                        totalIssueContributions
                        totalPullRequestContributions
                        totalPullRequestReviewContributions
                        totalRepositoryContributions
                    }
                    repositories(first: $${MAX_REPOS_ONE_QUERY}, ownerAffiliations: OWNER) {
                        edges {
                            cursor
                        }
                        nodes {
                            forkCount
                            stargazerCount
                        }
                    }
                }
            }
        """.replace(Regex("\\s{2,}"), " ")
        return client.post(URL) {
            header("Authorization", "bearer ${config.githubToken}")
            setBody(Json.encodeToString(buildJsonObject {
                put("query", JsonPrimitive(query))
                put("variables", buildJsonObject {
                    put("login", username)
                })
            }))
        }.bodyAsText().let { Json.decodeFromString<GhResp<GhUserResp>>(it) }
    }

    private suspend fun fetchDetailNext(username: String, cursor: String): GhResp<GhUserResp> {
        val query = $$"""
            query($login: String!, $cursor: String!) {
                user(login: $login) {
                    repositories(after: $cursor, first: $${MAX_REPOS_ONE_QUERY}, ownerAffiliations: OWNER) {
                        edges {
                            cursor
                        }
                        nodes {
                            forkCount
                            stargazerCount
                        }
                    }
                }
            }
        """.replace(Regex("\\s{2,}"), " ")
        return client.post(URL) {
            header("Authorization", "bearer ${config.githubToken}")
            setBody(Json.encodeToString(buildJsonObject {
                put("query", JsonPrimitive(query))
                put("variables", buildJsonObject {
                    put("login", username)
                    put("cursor", cursor)
                })
            }))
        }.bodyAsText().let { Json.decodeFromString<GhResp<GhUserResp>>(it) }
    }

    suspend fun fetchStats(
        username: String,
        includeAllCommits: Boolean = false,
        excludeRepo: List<String> = emptyList(),
        includeMergedPullRequests: Boolean = false,
        includeDiscussions: Boolean = false,
        includeDiscussionsAnswers: Boolean = false,
        commitsYear: Int? = null
    ): GithubStats {
        require(username.isNotBlank()) { "username is required" }
        val query = $$"""
            query userInfo(
                $login: String!,
                $includeMergedPullRequests: Boolean!,
                $includeDiscussions: Boolean!,
                $includeDiscussionsAnswers: Boolean!,
                $startTime: DateTime = null
            ) {
                user(login: $login) {
                    name
                    login
                    commits: contributionsCollection(from: $startTime) {
                        totalCommitContributions
                    }
                    reviews: contributionsCollection {
                        totalPullRequestReviewContributions
                    }
                    repositoriesContributedTo(first: 1, contributionTypes: [COMMIT, ISSUE, PULL_REQUEST, REPOSITORY]) {
                        totalCount
                    }
                    pullRequests(first: 1) {
                        totalCount
                    }
                    mergedPullRequests: pullRequests(states: MERGED) @include(if: $includeMergedPullRequests) {
                        totalCount
                    }
                    openIssues: issues(states: OPEN) {
                        totalCount
                    }
                    closedIssues: issues(states: CLOSED) {
                        totalCount
                    }
                    followers {
                        totalCount
                    }
                    repositoryDiscussions @include(if: $includeDiscussions) {
                        totalCount
                    }
                    repositoryDiscussionComments(onlyAnswers: true) @include(if: $includeDiscussionsAnswers) {
                        totalCount
                    }
                    repositories(first: 100, ownerAffiliations: OWNER, orderBy: {direction: DESC, field: STARGAZERS}) {
                        totalCount
                        nodes {
                            name
                            stargazers {
                                totalCount
                            }
                        }
                    }
                }
            }
        """.replace(Regex("\\s{2,}"), " ")
        val root = graphql(query, buildJsonObject {
            put("login", username)
            put("includeMergedPullRequests", includeMergedPullRequests)
            put("includeDiscussions", includeDiscussions)
            put("includeDiscussionsAnswers", includeDiscussionsAnswers)
            if (commitsYear != null) put("startTime", "${commitsYear}-01-01T00:00:00Z")
            else put("startTime", JsonNull)
        })
        val user = root["data"]?.jsonObject?.get("user")?.jsonObjectOrNull()
            ?: error("Could not fetch user.")
        val commits = if (includeAllCommits) {
            fetchTotalCommits(username)
        } else {
            user["commits"]!!.jsonObject["totalCommitContributions"]!!.jsonPrimitive.int
        }
        val totalPRs = user["pullRequests"]!!.jsonObject["totalCount"]!!.jsonPrimitive.int
        val totalPRsMerged = user["mergedPullRequests"]?.jsonObjectOrNull()?.get("totalCount")?.jsonPrimitive?.int ?: 0
        val totalIssues = user["openIssues"]!!.jsonObject["totalCount"]!!.jsonPrimitive.int +
            user["closedIssues"]!!.jsonObject["totalCount"]!!.jsonPrimitive.int
        val allExcluded = (excludeRepo + excludeRepositories()).toSet()
        val stars = user["repositories"]!!.jsonObject["nodes"]!!.jsonArray
            .filter { node -> node.jsonObject["name"]!!.jsonPrimitive.content !in allExcluded }
            .sumOf { node -> node.jsonObject["stargazers"]!!.jsonObject["totalCount"]!!.jsonPrimitive.int }
        val followers = user["followers"]!!.jsonObject["totalCount"]!!.jsonPrimitive.int
        val reviews = user["reviews"]!!.jsonObject["totalPullRequestReviewContributions"]!!.jsonPrimitive.int
        val rank = top.e404.statimg.platform.GithubRankCalculator.calculate(
            allCommits = includeAllCommits,
            commits = commits,
            prs = totalPRs,
            issues = totalIssues,
            reviews = reviews,
            stars = stars,
            followers = followers
        )
        return GithubStats(
            name = user["name"]?.jsonPrimitive?.contentOrNull ?: user["login"]!!.jsonPrimitive.content,
            totalPRs = totalPRs,
            totalPRsMerged = totalPRsMerged,
            mergedPRsPercentage = if (totalPRs == 0) 0.0 else totalPRsMerged.toDouble() / totalPRs * 100,
            totalReviews = reviews,
            totalCommits = commits,
            totalIssues = totalIssues,
            totalStars = stars,
            totalDiscussionsStarted = user["repositoryDiscussions"]?.jsonObjectOrNull()?.get("totalCount")?.jsonPrimitive?.int ?: 0,
            totalDiscussionsAnswered = user["repositoryDiscussionComments"]?.jsonObjectOrNull()?.get("totalCount")?.jsonPrimitive?.int ?: 0,
            contributedTo = user["repositoriesContributedTo"]!!.jsonObject["totalCount"]!!.jsonPrimitive.int,
            followers = followers,
            rank = rank
        )
    }

    suspend fun fetchTopLanguages(
        username: String,
        excludeRepo: List<String> = emptyList(),
        sizeWeight: Double = 1.0,
        countWeight: Double = 0.0
    ): List<GithubLanguageStat> {
        require(username.isNotBlank()) { "username is required" }
        val query = $$"""
            query userInfo($login: String!) {
                user(login: $login) {
                    repositories(ownerAffiliations: OWNER, isFork: false, first: 100) {
                        nodes {
                            name
                            languages(first: 10, orderBy: {field: SIZE, direction: DESC}) {
                                edges {
                                    size
                                    node {
                                        color
                                        name
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """.replace(Regex("\\s{2,}"), " ")
        val root = graphql(query, buildJsonObject { put("login", username) })
        val user = root["data"]?.jsonObject?.get("user")?.jsonObjectOrNull()
            ?: error("Could not fetch user.")
        val allExcluded = (excludeRepo + excludeRepositories()).toSet()
        data class MutableLang(val name: String, var color: String?, var size: Double, var count: Int)
        val langs = linkedMapOf<String, MutableLang>()
        user["repositories"]!!.jsonObject["nodes"]!!.jsonArray
            .map { it.jsonObject }
            .filterNot { it["name"]!!.jsonPrimitive.content in allExcluded }
            .forEach { repo ->
                val seenInRepo = mutableSetOf<String>()
                repo["languages"]!!.jsonObject["edges"]!!.jsonArray.forEach { edgeJson ->
                    val edge = edgeJson.jsonObject
                    val node = edge["node"]!!.jsonObject
                    val name = node["name"]!!.jsonPrimitive.content
                    val size = edge["size"]!!.jsonPrimitive.double
                    val lang = langs.getOrPut(name) {
                        MutableLang(name, node["color"]?.jsonPrimitive?.contentOrNull, 0.0, 0)
                    }
                    lang.size += size
                    if (seenInRepo.add(name)) lang.count += 1
                }
            }
        return langs.values
            .map {
                val weighted = it.size.pow(sizeWeight) * it.count.toDouble().pow(countWeight)
                GithubLanguageStat(it.name, it.color, weighted, it.count)
            }
            .sortedByDescending { it.size }
    }

    suspend fun fetchRepo(username: String, repoName: String): GithubRepoCardData {
        require(username.isNotBlank()) { "username is required" }
        require(repoName.isNotBlank()) { "repo is required" }
        val query = $$"""
            fragment RepoInfo on Repository {
                name
                nameWithOwner
                isPrivate
                isArchived
                isTemplate
                stargazers {
                    totalCount
                }
                description
                primaryLanguage {
                    color
                    name
                }
                forkCount
            }
            query getRepo($login: String!, $repo: String!) {
                user(login: $login) {
                    repository(name: $repo) {
                        ...RepoInfo
                    }
                }
                organization(login: $login) {
                    repository(name: $repo) {
                        ...RepoInfo
                    }
                }
            }
        """.replace(Regex("\\s{2,}"), " ")
        val root = graphql(query, buildJsonObject {
            put("login", username)
            put("repo", repoName)
        })
        val data = root["data"]!!.jsonObject
        val repo = data["user"]?.jsonObjectOrNull()?.get("repository")?.jsonObjectOrNull()
            ?: data["organization"]?.jsonObjectOrNull()?.get("repository")?.jsonObjectOrNull()
            ?: error("Repository not found.")
        if (repo["isPrivate"]!!.jsonPrimitive.boolean) error("Repository not found.")
        val primaryLanguage = repo["primaryLanguage"]?.jsonObjectOrNull()
        return GithubRepoCardData(
            name = repo["name"]!!.jsonPrimitive.content,
            nameWithOwner = repo["nameWithOwner"]!!.jsonPrimitive.content,
            description = repo["description"]?.jsonPrimitive?.contentOrNull,
            primaryLanguageName = primaryLanguage?.get("name")?.jsonPrimitive?.contentOrNull,
            primaryLanguageColor = primaryLanguage?.get("color")?.jsonPrimitive?.contentOrNull,
            isArchived = repo["isArchived"]!!.jsonPrimitive.boolean,
            isTemplate = repo["isTemplate"]!!.jsonPrimitive.boolean,
            starCount = repo["stargazers"]!!.jsonObject["totalCount"]!!.jsonPrimitive.int,
            forkCount = repo["forkCount"]!!.jsonPrimitive.int
        )
    }

    private suspend fun graphql(query: String, variables: JsonObject): JsonObject {
        val text = client.post(URL) {
            header("Authorization", "bearer ${config.githubToken}")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(buildJsonObject {
                put("query", JsonPrimitive(query))
                put("variables", variables)
            }))
        }.bodyAsText()
        val root = json.parseToJsonElement(text).jsonObject
        val errors = root["errors"]?.jsonArray
        if (!errors.isNullOrEmpty()) {
            error(errors.joinToString("\n") { it.jsonObject["message"]?.jsonPrimitive?.content ?: it.toString() })
        }
        return root
    }

    private suspend fun fetchTotalCommits(username: String): Int {
        val text = client.get("https://api.github.com/search/commits") {
            parameter("q", "author:$username")
            header("Accept", "application/vnd.github.cloak-preview")
            header("Authorization", "token ${config.githubToken}")
        }.bodyAsText()
        val totalCount = json.parseToJsonElement(text).jsonObject["total_count"]?.jsonPrimitive?.intOrNull
        if (totalCount == null || totalCount == 0) error("Could not fetch total commits.")
        return totalCount
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = if (this is JsonObject) this else null

    private fun excludeRepositories(): List<String> = System.getenv("EXCLUDE_REPO")
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()
}
