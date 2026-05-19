package top.e404.statimg.test

import org.junit.jupiter.api.Test
import top.e404.tavolo.draw.compose.Modifier
import top.e404.tavolo.draw.compose.image
import top.e404.tavolo.draw.compose.render
import top.e404.tavolo.draw.compose.sizeIn

class TestManualImageRender {
    @Test
    fun testRenderManuallyDrawnImageWithoutNetworkDependency() {
        val image = render {
            image(
                image = TestRenderFixtures.manualImage(),
                modifier = Modifier.sizeIn(maxWidth = 48f, maxHeight = 48f)
            )
        }

        TestRenderFixtures.assertPngImage(image)
    }
}
