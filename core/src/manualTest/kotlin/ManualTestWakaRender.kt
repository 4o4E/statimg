package top.e404.statimg.manual

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test

class ManualTestWakaRender {

    @Test
    fun testRenderStats() {
        runBlocking(Dispatchers.IO) {
            val bytes = ManualTestConfig.wakatimeRender.renderStats("404E", ManualTestConfig.themes2d)
            File("waka_dashboard.png").writeBytes(bytes)
        }
    }
}
