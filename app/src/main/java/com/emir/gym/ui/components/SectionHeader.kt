package com.emir.gym.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.emir.gym.ui.theme.GymTheme
import com.emir.gym.ui.theme.LabelStyles
import com.emir.gym.ui.theme.Radii
import com.emir.gym.ui.theme.upperTr

/**
 * Bölüm başlığı: küçük, büyük harfli, aralıklı gri etiket; sağda isteğe bağlı eylem.
 * Başlık metni Türkçe kurallarıyla büyük harfe çevrilir.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title.upperTr(),
            style = LabelStyles.caps,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false).semantics { heading() },
            maxLines = 1,
        )
        if (actionLabel != null && onAction != null) {
            Box(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(Radii.pill)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = GymTheme.colors.accent)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun SectionHeaderPreview() = PreviewBox {
    SectionHeader(title = "İstatistikler", actionLabel = "Tümü", onAction = {})
}
