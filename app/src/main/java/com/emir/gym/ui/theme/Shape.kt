package com.emir.gym.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val GymShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Bileşenlerin ortak köşe ölçüleri. */
object Radii {
    /** Kart. */
    val card = RoundedCornerShape(22.dp)
    /** Kart içi satır, giriş alanı. */
    val inner = RoundedCornerShape(12.dp)
    /** Hap (düğme, çip, sayaç). */
    val pill = RoundedCornerShape(percent = 50)
}
