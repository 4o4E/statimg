package top.e404.statimg.fetcher

import kotlinx.serialization.Serializable

@Serializable
data class GhResp<T : Any>(
    val data: T,
    val errors: List<GhError>? = null
)

@Serializable
data class GhError(
    val message: String
)

@Serializable
data class GhUserResp(
    val user: GhUser? = null
)

@Serializable
data class GhUser(
    val contributionsCollection: GhContributionsCollection,
    val repositories: GhRepository? = null,
)

@Serializable
data class GhContributionsCollection(
    val contributionCalendar: GhContributionCalendar,
    val commitContributionsByRepository: List<GhCommitContributionsByRepository>? = null,
    val totalCommitContributions: Int? = null,
    val totalIssueContributions: Int? = null,
    val totalPullRequestContributions: Int? = null,
    val totalPullRequestReviewContributions: Int? = null,
    val totalRepositoryContributions: Int? = null,
)

@Serializable
data class GhContributionCalendar(
    val isHalloween: Boolean? = null,
    val totalContributions: Int? = null,
    val weeks: List<GhWeek>
)

@Serializable
data class GhWeek(
    val contributionDays: List<GhContributionDay>
)

@Serializable
data class GhContributionDay(
    val contributionCount: Int,
    val contributionLevel: String? = null,
    val date: String
)

@Serializable
data class GhCommitContributionsByRepository(
    val repository: GhRepositoryInfo,
    val contributions: GhContributions
)

@Serializable
data class GhRepositoryInfo(
    val primaryLanguage: GhLanguage? = null
)

@Serializable
data class GhLanguage(
    val name: String? = null,
    val color: String? = null,
)

@Serializable
data class GhContributions(
    val totalCount: Int
)

@Serializable
data class GhRepository(
    val edges: List<GhRepoEdge>,
    val nodes: List<GhRepoNode>
)

@Serializable
data class GhRepoEdge(
    val cursor: String
)

@Serializable
data class GhRepoNode(
    val forkCount: Int,
    val stargazerCount: Int
)