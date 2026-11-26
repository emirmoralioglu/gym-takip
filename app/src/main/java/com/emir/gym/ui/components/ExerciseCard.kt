package com.emir.gym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.emir.gym.R
import com.emir.gym.ui.theme.GymTheme
import com.emir.gym.ui.theme.LabelStyles
import com.emir.gym.ui.theme.Radii
import com.emir.gym.ui.theme.tabular
import com.emir.gym.ui.theme.upperTr

/**
 * Egzersiz kartı: ad, kas grubu, plan (ör. 3×8) ve yapılan set ilerlemesi.
 * Set satırları gibi ek içerik [content] ile kartın altına eklenir.
 *
 * @param doneSets tamamlanan set sayısı; plan aşılırsa çubuk dolu kalır, metin gerçek sayıyı gösterir.
 */
@Composable
fun ExerciseCard(
    name: String,
    muscleGroup: String?,
    plannedSets: Int,
    plannedReps: Int,
    doneSets: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = GymTheme.colors
    val scheme = MaterialTheme.colorScheme
    val complete = plannedSets > 0 && doneSets >= plannedSets
    AppCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!muscleGroup.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        muscleGroup.upperTr(),
                        style = LabelStyles.caps,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .clip(Radii.pill)
                    .border(1.dp, c.cardBorder, Radii.pill)
                    .background(scheme.surfaceContainerHighest.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    stringResource(R.string.ui_plan_format, plannedSets, plannedReps),
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    color = scheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SetSegments(planned = plannedSets, done = doneSets, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.ui_sets_progress, doneSets, plannedSets),
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = if (complete) c.accent else scheme.onSurfaceVariant,
            )
        }
        if (content != null) {
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** Plan kadar parça; tamamlananlar vurgu rengiyle dolu. */
@Composable
fun SetSegments(planned: Int, done: Int, modifier: Modifier = Modifier) {
    val c = GymTheme.colors
    val count = planned.coerceIn(1, 12)
    Row(modifier.height(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(Radii.pill)
                    .background(if (i < done) c.accent else c.track),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ExerciseCardPreview() = PreviewBox {
    val done = remember { mutableStateListOf(true, false, false) }
    val doneCount by remember { androidx.compose.runtime.derivedStateOf { done.count { it } } }
    ExerciseCard(
        name = "Lat pulldown",
        muscleGroup = "Sırt",
        plannedSets = 3,
        plannedReps = 8,
        doneSets = doneCount,
    ) {
        SetRowHeader()
        done.forEachIndexed { i, d ->
            SetRow(i + 1, "", {}, "", {}, d, { done[i] = it }, kgHint = "–", repsHint = "8")
        }
    }
}
