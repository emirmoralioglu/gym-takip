package com.emir.gym.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emir.gym.R
import com.emir.gym.ui.theme.GymTheme
import com.emir.gym.ui.theme.tabular

/**
 * Çizgi grafik kartı: yumuşak çizgi, altında vurgu renginde degrade dolgu.
 * 0 nokta: [emptyText]; 1 nokta: tek nokta ve [onePointText].
 *
 * @param points y değerleri, soldan sağa zaman sırasıyla.
 * @param trailing başlığın sağındaki küçük metin (ör. "Son: 82.5 kg").
 * @param startLabel / [endLabel] x ekseninin uçlarındaki küçük etiketler (ör. tarih).
 */
@Composable
fun LineChartCard(
    title: String,
    points: List<Float>,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    startLabel: String? = null,
    endLabel: String? = null,
    emptyText: String = stringResource(R.string.ui_chart_empty),
    onePointText: String = stringResource(R.string.ui_chart_one_point),
    chartHeight: Dp = 120.dp,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(R.string.ui_chart_desc, title, points.size)
    AppCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.labelMedium.tabular(), color = scheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            when (points.size) {
                0 -> ChartMessage(emptyText)
                else -> {
                    LineChart(points, Modifier.fillMaxSize())
                    if (points.size == 1) {
                        Box(Modifier.fillMaxSize().padding(bottom = 4.dp), contentAlignment = Alignment.BottomCenter) {
                            ChartMessage(onePointText)
                        }
                    }
                }
            }
        }
        if (points.size >= 2 && (startLabel != null || endLabel != null)) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(startLabel ?: "", style = MaterialTheme.typography.labelSmall.tabular(), color = scheme.onSurfaceVariant)
                Text(endLabel ?: "", style = MaterialTheme.typography.labelSmall.tabular(), color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChartMessage(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/** Kartsız çizgi grafik; [LineChartCard] içinde kullanılır, tek başına da çizilebilir. */
@Composable
fun LineChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = GymTheme.colors.chartLine,
    fillTop: Color = GymTheme.colors.chartFillTop,
    gridColor: Color = GymTheme.colors.chartGrid,
    dotColor: Color = GymTheme.colors.accent,
) {
    val bg = MaterialTheme.colorScheme.surfaceContainerLowest
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val dot = 4.dp.toPx()
        val pad = dot + 2.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
        for (i in 0..2) {
            val y = pad + (h - 2 * pad) * i / 2f
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        }
        if (points.isEmpty()) return@Canvas
        if (points.size == 1) {
            val center = Offset(w / 2, h / 2 - 8.dp.toPx())
            drawCircle(dotColor, radius = dot + 2.dp.toPx(), center = center)
            drawCircle(bg, radius = dot - 1.dp.toPx(), center = center)
            return@Canvas
        }
        val min = points.min()
        val max = points.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = (w - 2 * pad) / (points.size - 1)
        val pts = points.mapIndexed { i, v ->
            val norm = if (max == min) 0.5f else (v - min) / range
            Offset(pad + i * stepX, pad + (h - 2 * pad) * (1f - norm))
        }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val p0 = pts[i - 1]
                val p1 = pts[i]
                val mx = (p0.x + p1.x) / 2
                cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(pts.last().x, h)
            lineTo(pts.first().x, h)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(fillTop, fillTop.copy(alpha = 0f)), startY = 0f, endY = h))
        drawPath(line, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = pts.last()
        drawCircle(dotColor, radius = dot + 1.5.dp.toPx(), center = last)
        drawCircle(bg, radius = dot - 1.5.dp.toPx(), center = last)
    }
}

@PreviewLightDark
@Composable
private fun LineChartCardPreview() = PreviewBox {
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Önizleme için yer tutucu değerler; gerçek veri değildir.
        LineChartCard(title = "Gelişim", points = listOf(1f, 3f, 2f, 4f, 3f, 5f), startLabel = "–", endLabel = "–")
        LineChartCard(title = "Gelişim", points = listOf(1f))
        LineChartCard(title = "Gelişim", points = emptyList())
    }
}
