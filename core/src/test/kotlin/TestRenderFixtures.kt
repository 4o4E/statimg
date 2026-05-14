package top.e404.status.render.test

import org.jetbrains.skia.*
import top.e404.status.render.feature.ColorProvider
import top.e404.status.render.feature.Heatmap2dRender
import top.e404.status.render.feature.Heatmap3dRender
import java.time.LocalDate
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

object TestRenderFixtures {
    val layout2d = Heatmap2dRender.Layout()
    val theme2d = Heatmap2dRender.Theme()

    val layout3d = Heatmap3dRender.Layout(
        imageWidth = 360,
        imageHeight = 260
    )
    val theme3d = Heatmap3dRender.Theme(
        background = 0xff10151f.toInt(),
        colorProvider = ColorProvider.Rainbow()
    )

    fun contributionWeeks(
        start: LocalDate = LocalDate.of(2025, 1, 6),
        weekCount: Int = 8
    ): List<List<Pair<LocalDate, Int?>>> =
        (0 until weekCount).map { week ->
            (0 until 7).map { day ->
                val index = week * 7 + day
                start.plusDays(index.toLong()) to contributionValue(index)
            }
        }

    fun contributionDays(): MutableList<Pair<LocalDate, Int?>> =
        contributionWeeks(weekCount = 12).flatten().toMutableList()

    fun groupedByMonth(): Map<Pair<Int, Int>, List<List<Pair<LocalDate, Int?>>>> =
        contributionWeeks().groupBy { week ->
            week.first().first.let { it.year to it.monthValue }
        }.toSortedMap { left, right ->
            if (left.first != right.first) left.first - right.first else left.second - right.second
        }

    fun manualImage(width: Int = 96, height: Int = 96): Image =
        Surface.makeRasterN32Premul(width, height).use { surface ->
            val canvas = surface.canvas
            canvas.clear(0xffeef3f8.toInt())

            val paint = Paint().apply {
                isAntiAlias = true
                mode = PaintMode.FILL
            }

            paint.color = 0xff4c71f2.toInt()
            canvas.drawCircle(width * 0.5f, height * 0.42f, width * 0.22f, paint)

            paint.color = 0xff2f80ed.toInt()
            canvas.drawOval(
                Rect.makeXYWH(width * 0.25f, height * 0.58f, width * 0.5f, height * 0.28f),
                paint
            )

            paint.color = 0xffffffff.toInt()
            canvas.drawCircle(width * 0.42f, height * 0.38f, width * 0.035f, paint)
            canvas.drawCircle(width * 0.58f, height * 0.38f, width * 0.035f, paint)

            surface.makeImageSnapshot()
        }

    fun assertPngImage(image: Image, minBytes: Int = 256) {
        assertTrue(image.width > 0, "渲染结果宽度必须大于 0")
        assertTrue(image.height > 0, "渲染结果高度必须大于 0")

        val data = assertNotNull(image.encodeToData(), "渲染结果必须能编码为 PNG")
        assertTrue(data.bytes.size > minBytes, "PNG 数据过小，可能没有有效渲染")
    }

    private fun contributionValue(index: Int): Int? =
        when {
            index % 13 == 0 -> null
            index % 5 == 0 -> 0
            else -> (index * 7 % 18) + 1
        }
}
