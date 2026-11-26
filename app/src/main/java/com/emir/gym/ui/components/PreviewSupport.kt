package com.emir.gym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emir.gym.ui.theme.GymTheme

/** Önizlemeler için tema + zemin sarmalayıcısı. */
@Composable
internal fun PreviewBox(content: @Composable () -> Unit) {
    GymTheme {
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
        ) { content() }
    }
}
