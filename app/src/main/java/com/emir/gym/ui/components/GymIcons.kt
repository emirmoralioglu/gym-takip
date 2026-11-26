package com.emir.gym.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Uygulamanın kendi çizgi ikonları (24 birimlik tuval, 2 birim çizgi).
 * Ek ikon kütüphanesi gerektirmez; Icon() ile renklendirilir.
 */
object GymIcons {
    val Check: ImageVector by lazy { line("check", "M5,12.5L9.5,17L19,7.5") }
    val Plus: ImageVector by lazy { line("plus", "M12,5V19M5,12H19") }
    val Close: ImageVector by lazy { line("close", "M6.5,6.5L17.5,17.5M17.5,6.5L6.5,17.5") }
    val Pause: ImageVector by lazy { filled("pause", "M7,5H10V19H7ZM14,5H17V19H14Z") }
    val Play: ImageVector by lazy { filled("play", "M8,5.5V18.5L19,12Z") }
    val ArrowUp: ImageVector by lazy { line("arrow_up", "M12,19V5M6,11L12,5L18,11") }
    val ArrowDown: ImageVector by lazy { line("arrow_down", "M12,5V19M6,13L12,19L18,13") }
    val ChevronRight: ImageVector by lazy { line("chevron_right", "M9.5,6L15.5,12L9.5,18") }

    /** Günlük sekmesi: takvim yaprağı. */
    val Today: ImageVector by lazy {
        line("today", "M6,4.5H18A2,2 0,0 1,20,6.5V18A2,2 0,0 1,18,20H6A2,2 0,0 1,4,18V6.5A2,2 0,0 1,6,4.5ZM4,9.5H20M8.5,3V6M15.5,3V6M9,14H12")
    }

    /** Haftalık sekmesi: sütunlar. */
    val Week: ImageVector by lazy { line("week", "M5,19V13M9.67,19V8M14.33,19V11M19,19V5") }

    /** İlerleme sekmesi: yükselen çizgi. */
    val Trend: ImageVector by lazy { line("trend", "M3.5,17L9,11.5L13,15.5L20.5,8M15,8H20.5V13.5") }

    /** Ayarlar sekmesi: sürgüler. */
    val Sliders: ImageVector by lazy { line("sliders", "M4,7H12M17,7H20M14.5,4.5V9.5M4,17H6.5M11.5,17H20M9,14.5V19.5") }

    /** Dambıl. */
    val Dumbbell: ImageVector by lazy { line("dumbbell", "M7,7V17M17,7V17M3.5,9.5V14.5M20.5,9.5V14.5M7,12H17") }

    /** Öğün: tabak ve çatal. */
    val Meal: ImageVector by lazy {
        line("meal", "M14,12A5,5 0,1 1,4,12A5,5 0,1 1,14,12ZM18,4V20M16,4V8.5A2,2 0,0 0,20,8.5V4")
    }

    /** Rekor: kupa. */
    val Trophy: ImageVector by lazy {
        line("trophy", "M8,4H16V10A4,4 0,0 1,8,10ZM8,6H5A3,3 0,0 0,8,11M16,6H19A3,3 0,0 1,16,11M12,14V18M8.5,20H15.5")
    }

    private fun base(name: String) = ImageVector.Builder(
        name = "gym_$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )

    private fun line(name: String, d: String): ImageVector = base(name).addPath(
        pathData = addPathNodes(d),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

    private fun filled(name: String, d: String): ImageVector = base(name).addPath(
        pathData = addPathNodes(d),
        fill = SolidColor(Color.Black),
    ).build()
}
