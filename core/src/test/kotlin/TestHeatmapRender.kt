package top.e404.statimg.test

import org.junit.jupiter.api.Test
import top.e404.statimg.feature.Heatmap2dRender
import top.e404.statimg.feature.Heatmap3dRender

class TestHeatmapRender {
    @Test
    fun testRender2dHeatmapWithFixedLocalData() {
        val image = Heatmap2dRender.renderCommit(
            byMonth = TestRenderFixtures.groupedByMonth(),
            max = TestRenderFixtures.contributionDays().maxOf { it.second ?: 0 },
            title = "local contribution fixture",
            layout = TestRenderFixtures.layout2d,
            theme = TestRenderFixtures.theme2d
        )

        TestRenderFixtures.assertPngImage(image)
    }

    @Test
    fun testRender3dHeatmapWithFixedLocalData() {
        val image = Heatmap3dRender.render(
            days = TestRenderFixtures.contributionDays(),
            layout = TestRenderFixtures.layout3d,
            theme = TestRenderFixtures.theme3d
        )

        TestRenderFixtures.assertPngImage(image, minBytes = 1024)
    }
}
