package top.e404.status.render.manual

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDateTime
import kotlin.test.Test

class ManualTestGithubRender {

    @Test
    fun testRenderContribution2d() {
        runBlocking(Dispatchers.IO) {
            val bytes = ManualTestConfig.githubRender.renderContribution2d(
                "4o4E",
                LocalDateTime.now(),
                ManualTestConfig.themes2d
            )
            File("github_contribution_2d.png").writeBytes(bytes)
        }
    }

    @Test
    fun testRenderContribution3d() {
        runBlocking(Dispatchers.IO) {
            val bytes = ManualTestConfig.githubRender.renderContribution3d(
                "4o4e",
                ManualTestConfig.themes3d
            )
            File("github_contribution_3d.png").writeBytes(bytes)
        }
    }
}