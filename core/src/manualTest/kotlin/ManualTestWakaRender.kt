package top.e404.status.render.manual

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import top.e404.status.render.platform.WakatimeRender
import java.io.File
import kotlin.test.Test

class ManualTestWakaRender {

    @Test
    fun testRenderLang() {
        runBlocking(Dispatchers.IO) {
            val bytes = ManualTestConfig.wakatimeRender.renderLang("404E", WakatimeRender.FetchRange.LAST_6_MONTHS, false, ManualTestConfig.themes2d)
            File("waka_lang.png").writeBytes(bytes)
        }
    }

    @Test
    fun testRenderEditor() {
        runBlocking(Dispatchers.IO) {
            val bytes = ManualTestConfig.wakatimeRender.renderEditor("404E", WakatimeRender.FetchRange.LAST_6_MONTHS, ManualTestConfig.themes2d)
            File("waka_editor.png").writeBytes(bytes)
        }
    }
}
