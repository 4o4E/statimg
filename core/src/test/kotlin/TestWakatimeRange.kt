package top.e404.statimg.test

import org.junit.jupiter.api.Test
import top.e404.statimg.platform.WakatimeRender
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TestWakatimeRange {
    @Test
    fun testParseSupportedWakatimeRangeAliases() {
        assertEquals(WakatimeRender.FetchRange.LAST_7_DAYS, WakatimeRender.FetchRange.byName("7d"))
        assertEquals(WakatimeRender.FetchRange.LAST_30_DAYS, WakatimeRender.FetchRange.byName("30d"))
        assertEquals(WakatimeRender.FetchRange.LAST_YEAR, WakatimeRender.FetchRange.byName("year"))
        assertEquals(WakatimeRender.FetchRange.ALL_TIME, WakatimeRender.FetchRange.byName("all"))
        assertNull(WakatimeRender.FetchRange.byName("unknown"))
    }
}
