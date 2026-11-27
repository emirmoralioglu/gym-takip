package com.emir.gym.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Asistan ekranlarinin ikonlari (GymIcons ile ayni cizgi dili; ui/components'a dokunmadan). */
object AssistantIcons {
    val Chat: ImageVector by lazy { line("chat", "M4,5.5H20V16.5H11L6.5,20.5V16.5H4Z") }
    val Camera: ImageVector by lazy { line("camera", "M4,8H7.5L9,5.5H15L16.5,8H20V19H4ZM12,16.2A3.2,3.2 0 1,0 12,9.8A3.2,3.2 0 1,0 12,16.2Z") }
    val Photo: ImageVector by lazy { line("photo", "M4,5H20V19H4ZM4,16L9,11L13,15L15.5,12.5L20,17M8.5,9.5H8.6") }
    val Send: ImageVector by lazy { line("send", "M12,19V5M6,11L12,5L18,11") }
    val Stop: ImageVector by lazy { filled("stop", "M7,7H17V17H7Z") }
    val Back: ImageVector by lazy { line("back", "M19,12H5M11,6L5,12L11,18") }
    val More: ImageVector by lazy { filled("more", "M12,6.5A1.6,1.6 0 1,1 12,3.3A1.6,1.6 0 1,1 12,6.5ZM12,13.6A1.6,1.6 0 1,1 12,10.4A1.6,1.6 0 1,1 12,13.6ZM12,20.7A1.6,1.6 0 1,1 12,17.5A1.6,1.6 0 1,1 12,20.7Z") }
    val Warning: ImageVector by lazy { line("warning", "M12,4L21,19H3ZM12,10V14M12,16.8H12.1") }

    private fun base(name: String) = ImageVector.Builder(
        name = "assistant_$name", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
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
