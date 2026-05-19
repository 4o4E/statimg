package top.e404.statimg.feature

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import top.e404.tavolo.draw.render3d.*
import top.e404.tavolo.util.Ahsb
import top.e404.tavolo.util.ahsb
import top.e404.statimg.ColorSerializer
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

/**
 * 颜色提供器函数类型，根据值、周和天选择颜色
 */
@Suppress("UNUSED")
@Serializable
sealed interface ColorProvider {
    /**
     * 获取颜色
     * @param days 包含日期和对应值的列表
     * @param value 当前值
     * @param week 当前周
     * @param day 当前天
     * @return 颜色
     */
    fun getColor(days: MutableList<Pair<LocalDate, Int?>>, value: Int, week: Int, day: Int): Int

    /**
     * 线性颜色插值颜色提供器
     *
     * @param from 起始颜色
     * @param to 结束颜色
     * @param zero 零值颜色
     * @param pow 非线性插值的幂指数，默认为1
     */
    @Serializable
    @SerialName("linear")
    data class Linear(
        @Serializable(ColorSerializer::class)
        val from: Int,
        @Serializable(ColorSerializer::class)
        val to: Int,
        @SerialName("zero")
        @Serializable(ColorSerializer::class)
        val zero: Int,
        val pow: Float = 1.0f
    ) : ColorProvider {
        @Transient
        private var f: Ahsb = from.ahsb()

        @Transient
        private var t: Ahsb = to.ahsb()
        private fun getColor(radius: Float): Int {
            val a = (f.a + (t.a - f.a) * radius).toInt()
            val h = f.h + (t.h - f.h) * radius
            val s = f.s + (t.s - f.s) * radius
            val b = f.b + (t.b - f.b) * radius
            return ahsb(a, h, s, b)
        }

        override fun getColor(days: MutableList<Pair<LocalDate, Int?>>, value: Int, week: Int, day: Int): Int {
            if (value == 0) return zero
            val maxValue = days.maxOf { it.second ?: 0 }.toFloat()
            val radius = (value / maxValue).pow(pow)
            return getColor(radius)
        }
    }

    /**
     * 线性彩虹色颜色提供器
     *
     * @param zeroSaturation 零值的饱和度
     * @param zeroBrightness 零值的亮度
     * @param normalSaturation 正常值的饱和度
     * @param normalBrightnessStart 正常值亮度的起始值
     * @param normalBrightnessPow 正常值亮度的变化范围
     * @param pow 非线性插值的幂指数，默认为2.0
     */
    @Serializable
    @SerialName("rainbow")
    data class Rainbow(
        @SerialName("zero_s")
        val zeroSaturation: Float = .5f,
        @SerialName("zero_b")
        val zeroBrightness: Float = .3f,
        @SerialName("normal_s")
        val normalSaturation: Float = .7f,
        @SerialName("normal_b_start")
        val normalBrightnessStart: Float = .5f,
        @SerialName("normal_b_pow")
        val normalBrightnessPow: Float = .3f,
        val pow: Float = 2.0f
    ) : ColorProvider {
        override fun getColor(days: MutableList<Pair<LocalDate, Int?>>, value: Int, week: Int, day: Int): Int {
            val hue = 1 - week / ceil(days.size / 7f)
            if (value == 0) return ahsb(0xff, hue, zeroSaturation, zeroBrightness)
            val maxValue = days.maxOf { it.second ?: 0 }
            val brightnessCurve = (value.toFloat() / maxValue).pow(pow)
            val brightness = normalBrightnessStart + brightnessCurve * normalBrightnessPow
            return ahsb(0xff, hue, normalSaturation, brightness)
        }
    }
}

object Heatmap3dRender {
    private const val MAX_HEIGHT = 10f

    @Serializable
    data class Theme(
        @Serializable(ColorSerializer::class)
        val text: Int = Color.WHITE,
        @SerialName("bg_color")
        @Serializable(ColorSerializer::class)
        val background: Int = 0xff00000f.toInt(),
        @SerialName("color_provider")
        val colorProvider: ColorProvider
    )

    @Serializable
    data class Layout(
        @SerialName("width")
        val imageWidth: Int = 1600,
        @SerialName("height")
        val imageHeight: Int = 1200,
    )

    /**
     * 生成3D热力图
     *
     * @param days 日期和对应值列表
     * @param layout 布局
     * @param theme 主题
     */
    fun render(
        days: MutableList<Pair<LocalDate, Int?>>,
        layout: Layout,
        theme: Theme
    ): Image {
        val weekList = buildList {
            var from = 0
            while (from < days.size) {
                if (from + 7 > days.size) {
                    val size = days.size - from
                    add(days.subList(from, from + size).map { it.second })
                    break
                }
                add(days.subList(from, from + 7).map { it.second })
                from += 7
            }
        }
        val totalWeeks = weekList.size
        val barSize = 1.0f
        val barSpacing = .15f
        // 为每个数据点创建一个长方体Mesh
        val componentMeshes = weekList.flatMapIndexed { wi, weekData ->
            weekData.mapIndexed { di, value ->
                if (value == null) return@mapIndexed null
                val height =
                    if (value == 0) 0.1f
                    else (log10(value / 20f + 1) * 144 + 3) / 16
                val color = theme.colorProvider.getColor(days, value, wi, di)
                val cuboid = createCuboid(Vec3(barSize, height, barSize), color)
                // 将长方体移动到其在网格中的正确位置
                val x = wi * (barSize + barSpacing)
                val y = height / 2f
                val z = di * (barSize + barSpacing)
                Mesh(cuboid.vertices.map { Vertex(it.position + Vec3(x, y, z), it.uv) }, cuboid.faces)
            }
        }.filterNotNull()
        // 将所有小长方体合并成一个大Mesh
        val chartMesh = combineMeshes(componentMeshes)
        // 计算图表中心，用于设置相机目标
        val chartWidth = (totalWeeks - 1) * (barSize + barSpacing) + barSpacing
        val chartDepth = 6 * (barSize + barSpacing) + barSpacing
        val chartCenter = Vec3(chartWidth / 2, 2.5f, chartDepth / 2)
        // 设置相机
        val camera = OrbitCamera(target = chartCenter, yaw = 45f, pitch = 35f, distance = 40f)
        // 渲染
        return renderSceneToImage(
            scene = Scene(listOf(chartMesh)),
            config = RenderConfig(
                width = layout.imageWidth,
                height = layout.imageHeight,
                camera = camera,
                renderFaces = true,
                usePerspective = false,
                backgroundColor = theme.background,
                useBackFaceCulling = false
            )
        )
    }

}
