package com.emir.gym.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emir.gym.R
import com.emir.gym.ui.theme.GymTheme
import com.emir.gym.ui.theme.tabular
import kotlin.math.roundToInt

/**
 * Halka ilerleme göstergesi. [progress] 0..1 aralığına kırpılır.
 * Ortada isteğe bağlı metin gösterilir.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    strokeWidth: Dp = 6.dp,
    color: Color = GymTheme.colors.accent,
    trackColor: Color = GymTheme.colors.track,
    centerText: String? = null,
    centerStyle: TextStyle = MaterialTheme.typography.labelLarge.tabular(),
) {
    val p = progress.coerceIn(0f, 1f)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = strokeWidth.toPx()
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            val topLeft = Offset(sw / 2, sw / 2)
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(sw))
            if (p > 0f) {
                drawArc(color, -90f, 360f * p, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        if (centerText != null) {
            Text(centerText, style = centerStyle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

/**
 * Halkalı ilerleme kartı (ör. hafta ilerlemesi 1/4).
 * Halkanın ortasında yüzde ("%25"), altta etiket ve "done/total" oranı.
 *
 * @param caption oranın yanında gösterilecek ek metin (ör. "antrenman"); null ise yalnızca oran.
 */
@Composable
fun ProgressRingCard(
    done: Int,
    total: Int,
    label: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val safeTotal = total.coerceAtLeast(0)
    val safeDone = done.coerceIn(0, maxOf(safeTotal, 0))
    val progress = if (safeTotal == 0) 0f else safeDone.toFloat() / safeTotal
    val percent = (progress * 100).roundToInt()
    val ratio = stringResource(R.string.ui_ring_ratio, safeDone, safeTotal)
    AppCard(
        modifier = modifier.semantics(mergeDescendants = true) {
            progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
        },
        onClick = onClick,
    ) {
        ProgressRing(
            progress = progress,
            size = 60.dp,
            centerText = "%$percent",
            centerStyle = TextStyle(fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold).tabular(),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            if (caption != null) "$ratio $caption" else ratio,
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@PreviewLightDark
@Composable
private fun ProgressRingCardPreview() = PreviewBox {
    ProgressRingCard(done = 1, total = 4, label = "Hafta ilerlemesi", caption = "antrenman", modifier = Modifier.width(170.dp))
}
