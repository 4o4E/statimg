package top.e404.statimg.fetcher

data class GithubRank(
    val level: String,
    val percentile: Double
)

data class GithubStats(
    val name: String,
    val totalPRs: Int,
    val totalPRsMerged: Int = 0,
    val mergedPRsPercentage: Double = 0.0,
    val totalReviews: Int,
    val totalCommits: Int,
    val totalIssues: Int,
    val totalStars: Int,
    val totalDiscussionsStarted: Int = 0,
    val totalDiscussionsAnswered: Int = 0,
    val contributedTo: Int,
    val followers: Int,
    val rank: GithubRank
)

data class GithubLanguageStat(
    val name: String,
    val color: String?,
    val size: Double,
    val count: Int
)

data class GithubRepoCardData(
    val name: String,
    val nameWithOwner: String,
    val description: String?,
    val primaryLanguageName: String?,
    val primaryLanguageColor: String?,
    val isArchived: Boolean,
    val isTemplate: Boolean,
    val starCount: Int,
    val forkCount: Int
)
