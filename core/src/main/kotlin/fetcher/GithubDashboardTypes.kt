package top.e404.statimg.fetcher

import java.time.LocalDate

data class GithubDashboardData(
    val login: String,
    val name: String,
    val avatarBytes: ByteArray,
    val followers: Int,
    val following: Int,
    val repositories: List<GithubDashboardRepository>,
    val languages: List<GithubDashboardLanguage>,
    val activity: GithubDashboardActivity,
    val pullRequests: GithubDashboardPullRequests,
    val totalIssues: Int,
    val contributedTo: Int
)

data class GithubDashboardRepository(val name: String, val stars: Int, val forks: Int)

data class GithubDashboardLanguage(val name: String, val bytes: Long)

data class GithubDashboardDay(val date: LocalDate, val count: Int)

data class GithubDashboardActivity(
    val from: LocalDate,
    val to: LocalDate,
    val total: Int,
    val days: List<GithubDashboardDay>,
    val commits: Int,
    val issues: Int,
    val pullRequests: Int,
    val reviews: Int,
    val repositories: Int
)

data class GithubDashboardPullRequests(val merged: Int, val closed: Int, val open: Int) {
    val total: Int get() = merged + closed + open
}
