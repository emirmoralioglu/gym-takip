package com.emir.gym.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.emir.gym.GymViewModel
import com.emir.gym.R
import com.emir.gym.assistant.AssistantController
import com.emir.gym.assistant.ProviderId
import com.emir.gym.ui.components.EmptyState
import com.emir.gym.ui.components.PrimaryButton
import com.emir.gym.ui.theme.GymTheme
import com.emir.gym.ui.theme.Radii
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Kisa saglayici adi (ust cubuk etiketi). */
@Composable
fun providerShortName(p: ProviderId): String = stringResource(
    when (p) {
        ProviderId.ANTHROPIC -> R.string.asst_provider_claude
        ProviderId.GEMINI -> R.string.asst_provider_gemini
        ProviderId.OPENAI_COMPAT -> R.string.asst_provider_openai
    }
)

/**
 * Tam ekran sohbet. [onClose]: geri tusu / kapat; [onOpenSettings]: asistan ayarlari;
 * [onGoTab]: eklenen kaydi gormek icin sekmeye gec (sohbet kapanir).
 */
@Composable
fun AssistantScreen(vm: GymViewModel, onClose: () -> Unit, onOpenSettings: () -> Unit, onGoTab: (Int) -> Unit) {
    val ui = vm.assistantUi
    val ctl = ui.ctl
    val config by ctl.settings.config.collectAsState()
    val sending by ctl.sending.collectAsState()
    val cancelling by ctl.cancelling.collectAsState()
    val error by ctl.error.collectAsState()
    val snapshot by ui.snapshot.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val close = { ui.discardAttached(); onClose() }
    BackHandler(onBack = close)

    val hasKey = remember(config.provider, ui.version) { ui.hasKey(config.provider) }
    val configured = hasKey && (config.provider != ProviderId.OPENAI_COMPAT || config.model.isNotBlank()) && config.model.isNotBlank()

    LaunchedEffect(Unit) {
        ui.events.collect { e ->
            val r = snackbar.showSnackbar(e.text, e.actionLabel, duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed && e.goTab != null) { ui.discardAttached(); onGoTab(e.goTab) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { ChatTopBar(config.provider, config.model, sending, onClose = close, onClear = { ui.clearChat() }, onSettings = onOpenSettings) },
        bottomBar = { ChatComposer(ui, config, configured, sending, onOpenSettings) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            ChatBody(ui, ctl, snapshot, configured, sending, cancelling, error, onOpenSettings, { ui.discardAttached(); onGoTab(it) }, Modifier.widthIn(max = MaxContentWidth))
        }
    }
}

@Composable
private fun ChatTopBar(
    provider: ProviderId, model: String, sending: Boolean,
    onClose: () -> Unit, onClear: () -> Unit, onSettings: () -> Unit,
) {
    val c = GymTheme.colors
    var menu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(c.barBackground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ChatIconButton(AssistantIcons.Back, stringResource(R.string.asst_back), onClick = onClose)
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(stringResource(R.string.asst_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(
                    "${providerShortName(provider)} · ${model.ifBlank { stringResource(R.string.asst_no_model) }}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                ChatIconButton(AssistantIcons.More, stringResource(R.string.asst_menu), onClick = { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.asst_clear_chat)) },
                        enabled = !sending,
                        onClick = { menu = false; confirmClear = true },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.asst_open_settings)) },
                        onClick = { menu = false; onSettings() },
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = c.cardBorder)
    }
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.asst_clear_title),
            text = stringResource(R.string.asst_clear_text),
            confirmLabel = stringResource(R.string.asst_clear_confirm),
            onConfirm = { confirmClear = false; onClear() },
            onDismiss = { confirmClear = false },
        )
    }
}

/** 44 dp yuvarlak ikon dugmesi; [dim] gorunumu soluklastirir ama dokunmayi acik tutar (neden anlatilir). */
@Composable
fun ChatIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, filled: Boolean = false, dim: Boolean = false, tint: androidx.compose.ui.graphics.Color? = null,
) {
    val c = GymTheme.colors
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (filled && !dim) c.accentFill else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon, contentDescription = description,
                tint = tint ?: if (filled && !dim) c.onAccentFill else scheme.onSurface.copy(alpha = if (dim) 0.38f else 1f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatBody(
    ui: AssistantUiModel,
    ctl: AssistantController,
    snapshot: ChatSnapshot?,
    configured: Boolean,
    sending: Boolean,
    cancelling: Boolean,
    error: com.emir.gym.assistant.AssistantError?,
    onOpenSettings: () -> Unit,
    onGoTab: (Int) -> Unit,
    modifier: Modifier,
) {
    if (snapshot == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = GymTheme.colors.accent) }
        return
    }
    val items = remember(snapshot) { ChatItems.build(snapshot.messages, snapshot.actions) }
    val shown = remember(items, snapshot, sending) { ChatItems.withRetry(items, snapshot.messages, sending) }
    val listState = rememberLazyListState()
    val resumable = ChatItems.resumable(snapshot.messages)

    // Yeni mesaj / kart / gosterge gelince en alta kay.
    LaunchedEffect(shown.size, shown.lastOrNull()?.key, sending) {
        val n = shown.size + (if (sending) 1 else 0)
        if (n > 0) {
            // Once son ogeye atla (yuksekligi bilinmeyen ogelerde animasyon yarida kalabilir), sonra gercek dibe in.
            listState.scrollToItem(n - 1)
            listState.scrollBy(1_000_000f)
        }
    }

    Column(modifier.fillMaxSize()) {
        if (shown.isEmpty() && !sending) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (!configured) UnconfiguredCard(onOpenSettings) else EmptyChat(onPick = { ui.draft = it })
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown, key = { it.key }) { item ->
                    when (item) {
                        is ChatItem.User -> UserBubble(item, ctl)
                        is ChatItem.Assistant -> AssistantBubble(item.text)
                        is ChatItem.Card -> ActionCardView(item.card, ui, onGoTab)
                        is ChatItem.Retry -> RetryRow(item.toolStep) { ui.retry() }
                    }
                }
                if (sending) item(key = "typing") { TypingRow(cancelling, onCancel = ui::cancel) }
            }
        }
        if (error != null) {
            val band = ErrorBands.of(error, resumable, ctl.reopensAt())
            ErrorBandView(
                band,
                onRetry = { ctl.clearError(); ui.retry() },
                onSettings = { ctl.clearError(); onOpenSettings() },
                onDismiss = ctl::clearError,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyChat(onPick: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        EmptyState(
            title = stringResource(R.string.asst_empty_title),
            message = stringResource(R.string.asst_empty_text),
            icon = AssistantIcons.Chat,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.asst_chip_meal, R.string.asst_chip_plan, R.string.asst_chip_protein, R.string.asst_chip_water).forEach { id ->
                val t = stringResource(id)
                SuggestionChip(t) { onPick(t) }
            }
        }
    }
}

@Composable
private fun SuggestionChip(label: String, onClick: () -> Unit) {
    val c = GymTheme.colors
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(Radii.pill)
            .border(1.dp, c.stroke, Radii.pill)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface) }
}

@Composable
private fun UnconfiguredCard(onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        com.emir.gym.ui.components.AppCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.asst_unconfigured_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.asst_unconfigured_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(14.dp))
            PrimaryButton(stringResource(R.string.asst_open_settings), onClick = onOpenSettings, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ---- Baloncuklar ----

@Composable
private fun UserBubble(item: ChatItem.User, ctl: AssistantController) {
    val c = GymTheme.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                .background(c.accentFill)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (item.imagePaths.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.imagePaths.forEach { ImageThumb(ctl, it, 72) }
                }
            }
            if (item.text.isNotBlank()) {
                Text(item.text, style = MaterialTheme.typography.bodyLarge, color = c.onAccentFill)
            }
        }
    }
}

@Composable
private fun AssistantBubble(text: String) {
    val c = GymTheme.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Column(
            Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
                .background(Brush.verticalGradient(listOf(c.cardTop, c.cardBottom)))
                .border(1.dp, c.cardBorder, RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            SelectionContainer { FormattedText(text) }
        }
    }
}

@Composable
fun FormattedText(text: String) {
    val lines = remember(text) { ChatFormat.parse(text) }
    val color = MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        lines.forEach { l ->
            when (l) {
                ChatFormat.Line.Gap -> Spacer(Modifier.size(4.dp))
                is ChatFormat.Line.Para -> Text(annotated(l.spans), style = MaterialTheme.typography.bodyLarge, color = color)
                is ChatFormat.Line.Bullet -> Row {
                    Text("•", style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.width(18.dp))
                    Text(annotated(l.spans), style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun annotated(spans: List<ChatFormat.Span>): AnnotatedString = buildAnnotatedString {
    spans.forEach { s ->
        if (s.bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.text) } else append(s.text)
    }
}

/** Kucuk gorsel onizleme; dosya yoksa 'Gorsel bu cihazda yok' yer tutucusu. */
@Composable
fun ImageThumb(ctl: AssistantController, path: String, sizeDp: Int) {
    val c = GymTheme.colors
    val state by produceState<ThumbState>(ThumbState.Loading, path) {
        value = withContext(Dispatchers.IO) {
            val f = ctl.previewFile(path) ?: return@withContext ThumbState.Missing
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
            android.graphics.BitmapFactory.decodeFile(f.absolutePath, opts)?.let { ThumbState.Ready(it.asImageBitmap()) } ?: ThumbState.Missing
        }
    }
    val missingText = stringResource(R.string.asst_image_missing)
    val desc = stringResource(R.string.asst_image_desc)
    Box(
        Modifier.size(sizeDp.dp).clip(Radii.inner).background(c.track).semantics { contentDescription = if (state is ThumbState.Missing) missingText else desc },
        contentAlignment = Alignment.Center,
    ) {
        when (val s = state) {
            is ThumbState.Ready -> Image(s.bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            ThumbState.Missing -> Text(missingText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp), maxLines = 4, overflow = TextOverflow.Ellipsis)
            ThumbState.Loading -> {}
        }
    }
}

private sealed interface ThumbState {
    object Loading : ThumbState
    object Missing : ThumbState
    data class Ready(val bitmap: androidx.compose.ui.graphics.ImageBitmap) : ThumbState
}

@Composable
private fun TypingRow(cancelling: Boolean, onCancel: () -> Unit) {
    val c = GymTheme.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(c.cardTop, c.cardBottom)))
                .border(1.dp, c.cardBorder, RoundedCornerShape(18.dp))
                .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.accent)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    stringResource(if (cancelling) R.string.asst_cancelling else R.string.asst_typing),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                )
                if (cancelling) {
                    Text(stringResource(R.string.asst_cancelling_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!cancelling) TextButton(onClick = onCancel) { Text(stringResource(R.string.asst_cancel)) }
        }
    }
}

@Composable
private fun RetryRow(toolStep: Boolean, onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Icon(AssistantIcons.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
        TextButton(onClick = onRetry) {
            Text(
                stringResource(if (toolStep) R.string.asst_retry_tool else R.string.asst_retry_unsent),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ErrorBandView(band: ErrorBand, onRetry: () -> Unit, onSettings: () -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(scheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(AssistantIcons.Warning, null, tint = scheme.onErrorContainer, modifier = Modifier.padding(top = 2.dp).size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(band.message, style = MaterialTheme.typography.bodyMedium, color = scheme.onErrorContainer)
                band.reopensAt?.let {
                    Text(
                        stringResource(R.string.asst_reopens, ErrorBands.reopenText(it, System.currentTimeMillis())),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = scheme.onErrorContainer,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (band.canRetry) TextButton(onClick = onRetry) { Text(stringResource(R.string.asst_retry), color = scheme.onErrorContainer) }
            if (band.toSettings) TextButton(onClick = onSettings) { Text(stringResource(R.string.asst_go_settings), color = scheme.onErrorContainer) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.asst_dismiss), color = scheme.onErrorContainer) }
        }
    }
}

/** Alt cubugun ustundeki kucuk 'Asistan' dugmesi. */
@Composable
fun AssistantFab(onClick: () -> Unit) {
    val c = GymTheme.colors
    val label = stringResource(R.string.asst_fab)
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clip(Radii.pill)
            .background(c.accentFill)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AssistantIcons.Chat, contentDescription = null, tint = c.onAccentFill, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.onAccentFill, maxLines = 1)
    }
}
