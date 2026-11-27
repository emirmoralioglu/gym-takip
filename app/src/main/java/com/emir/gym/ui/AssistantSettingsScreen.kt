package com.emir.gym.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emir.gym.GymViewModel
import com.emir.gym.R
import com.emir.gym.assistant.AnthropicWire
import com.emir.gym.assistant.ConfigRules
import com.emir.gym.assistant.GeminiWire
import com.emir.gym.assistant.ProviderId
import com.emir.gym.ui.components.AppCard
import com.emir.gym.ui.components.ChipRow
import com.emir.gym.ui.components.PrimaryButton
import com.emir.gym.ui.components.SecondaryButton
import com.emir.gym.ui.components.SectionHeader
import com.emir.gym.ui.theme.GymTheme
import com.emir.gym.ui.theme.Radii

/** Ayarlar sekmesindeki 'Asistan' girisi: durum ozeti + ayar ekranina gecis. */
@Composable
fun AssistantSettingsEntry(vm: GymViewModel, onOpen: () -> Unit) {
    val ui = vm.assistantUi
    val config by ui.ctl.settings.config.collectAsState()
    val hasKey = remember(config.provider, ui.version) { ui.hasKey(config.provider) }
    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.asst_entry_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.asst_entry_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            "${providerShortName(config.provider)} · ${config.model.ifBlank { stringResource(R.string.asst_no_model) }}",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(if (hasKey) R.string.asst_key_present else R.string.asst_key_absent),
            style = MaterialTheme.typography.bodySmall,
            color = if (hasKey) GymTheme.colors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        SecondaryButton(stringResource(R.string.asst_open_settings), onClick = onOpen, modifier = Modifier.fillMaxWidth())
    }
}

/** Tam ekran asistan ayarlari. */
@Composable
fun AssistantSettingsScreen(vm: GymViewModel, onBack: () -> Unit) {
    val ui = vm.assistantUi
    val ctl = ui.ctl
    val settings = ctl.settings
    val config by settings.config.collectAsState()
    val sending by ctl.sending.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { ui.events.collect { snackbar.showSnackbar(it.text) } }
    // Ekrana her giriste eski test sonucu temizlenir.
    LaunchedEffect(Unit) { ui.clearTest() }

    val provider = config.provider
    val hasKey = remember(provider, ui.version) { ui.hasKey(provider) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(Modifier.fillMaxWidth().background(GymTheme.colors.barBackground).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChatIconButton(AssistantIcons.Back, stringResource(R.string.asst_back), onClick = onBack)
                    Text(
                        stringResource(R.string.asst_settings_title), style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 4.dp), maxLines = 1,
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = GymTheme.colors.cardBorder)
            }
        },
    ) { pad ->
        ScreenColumn(Modifier.padding(pad).imePadding()) {
            SectionHeader(stringResource(R.string.asst_sec_provider))
            ProviderCard(ui, provider)

            SectionHeader(stringResource(R.string.asst_sec_key))
            KeyCard(ui, provider, hasKey)

            SectionHeader(stringResource(R.string.asst_sec_model))
            ModelCard(ui, provider, config.model, config.effort)

            if (provider == ProviderId.OPENAI_COMPAT) {
                SectionHeader(stringResource(R.string.asst_sec_openai))
                OpenAiCard(ui, config.baseUrl, config.supportsImages, config.supportsTools, debuggable)
            }

            SectionHeader(stringResource(R.string.asst_sec_limit))
            LimitCard(ui, provider, sending)

            SectionHeader(stringResource(R.string.asst_sec_auto))
            AutoAddCard(ui, config.autoAdd)

            SectionHeader(stringResource(R.string.asst_sec_test))
            TestCard(ui, hasKey)

            SectionHeader(stringResource(R.string.asst_sec_privacy))
            AppCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.asst_privacy_photos, R.string.asst_privacy_training, R.string.asst_privacy_key).forEach {
                        Row {
                            Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(16.dp))
                            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)

@Composable
private fun Note(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

@Composable
private fun ProviderCard(ui: AssistantUiModel, provider: ProviderId) {
    val names = ProviderId.entries.map { providerShortName(it) }
    AppCard(Modifier.fillMaxWidth()) {
        ChipRow(names, ProviderId.entries.indexOf(provider), onSelect = { ui.ctl.settings.setProvider(ProviderId.entries[it]); ui.bump(); ui.clearTest() })
        Spacer(Modifier.height(8.dp))
        Note(stringResource(R.string.asst_provider_note))
    }
}

@Composable
private fun KeyCard(ui: AssistantUiModel, provider: ProviderId, hasKey: Boolean) {
    val context = LocalContext.current
    // Yazilan deger yalniz bu ekranin bellek durumunda (rememberSaveable DEGIL); kaydedilince ve ekrandan cikinca silinir.
    var draft by remember(provider) { mutableStateOf("") }
    DisposableEffect(provider) { onDispose { draft = "" } }
    val masked = remember(provider, ui.version) { ui.ctl.keys.masked(provider) }
    var confirmDelete by remember { mutableStateOf(false) }

    AppCard(Modifier.fillMaxWidth()) {
        Title(stringResource(R.string.asst_key_title, providerShortName(provider)))
        Spacer(Modifier.height(4.dp))
        if (hasKey && masked != null) {
            Text(
                stringResource(R.string.asst_key_saved_masked, masked),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = GymTheme.colors.accent,
            )
        } else {
            Note(stringResource(R.string.asst_key_none))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.filterNot(Char::isWhitespace).take(512) },
            label = { Text(stringResource(if (hasKey) R.string.asst_key_new else R.string.asst_key_field), maxLines = 1) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            shape = Radii.inner,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.filterNot(Char::isWhitespace).orEmpty()
                if (t.isEmpty()) ui.events.tryEmit(UiEvent(context.getString(R.string.asst_clipboard_empty))) else draft = t.take(512)
            }) { Text(stringResource(R.string.asst_paste)) }
        }
        Spacer(Modifier.height(4.dp))
        PrimaryButton(
            stringResource(R.string.asst_key_save),
            onClick = { val k = draft; if (ui.saveKey(provider, k)) draft = "" },
            enabled = draft.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (hasKey) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(stringResource(R.string.asst_key_delete), onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth())
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.asst_key_delete_title),
            text = stringResource(R.string.asst_key_delete_text),
            confirmLabel = stringResource(R.string.asst_key_delete_confirm),
            onConfirm = { confirmDelete = false; ui.deleteKey(provider) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun ModelCard(ui: AssistantUiModel, provider: ProviderId, model: String, effort: String) {
    val settings = ui.ctl.settings
    val invalid = stringResource(R.string.asst_model_invalid)
    AppCard(Modifier.fillMaxWidth()) {
        if (provider == ProviderId.ANTHROPIC) {
            var open by remember { mutableStateOf(false) }
            Title(stringResource(R.string.asst_model_title))
            Spacer(Modifier.height(8.dp))
            Box {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(Radii.inner)
                        .border(1.dp, GymTheme.colors.stroke, Radii.inner)
                        .clickable(role = Role.DropdownList) { open = true }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(model, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(com.emir.gym.ui.components.GymIcons.ArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    AnthropicWire.MODELS.forEach { m ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(m)
                                    Text(stringResource(tierHint(m)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = { open = false; if (!settings.setModel(provider, m)) ui.events.tryEmit(UiEvent(invalid)); ui.bump() },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Note(stringResource(tierHint(model)))
            Spacer(Modifier.height(14.dp))
            Title(stringResource(R.string.asst_effort_title))
            Spacer(Modifier.height(6.dp))
            val labels = listOf(R.string.asst_effort_low, R.string.asst_effort_medium, R.string.asst_effort_high).map { stringResource(it) }
            ChipRow(labels, AssistantSettingsRules.EFFORT_CHOICES.indexOf(effort), onSelect = { settings.setEffort(AssistantSettingsRules.EFFORT_CHOICES[it]) })
            Spacer(Modifier.height(6.dp))
            Note(stringResource(R.string.asst_effort_note))
        } else {
            val saved = remember(provider, model) { settings.modelOf(provider) }
            var text by remember(provider, saved) { mutableStateOf(saved) }
            val suggestion = if (provider == ProviderId.GEMINI) GeminiWire.DEFAULT_MODEL else null
            Title(stringResource(R.string.asst_model_title))
            Spacer(Modifier.height(8.dp))
            GymField(text, { text = it.take(100) }, stringResource(R.string.asst_model_field))
            Spacer(Modifier.height(4.dp))
            Note(
                if (suggestion != null) stringResource(R.string.asst_model_suggest, suggestion) else stringResource(R.string.asst_model_openai_note),
            )
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                stringResource(R.string.screens_save),
                onClick = { if (!settings.setModel(provider, text)) ui.events.tryEmit(UiEvent(invalid)) else { ui.events.tryEmit(UiEvent(ui.msg(R.string.screens_saved))); ui.bump() } },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun tierHint(model: String): Int = when (AssistantSettingsRules.claudeTier(model)) {
    AssistantSettingsRules.ClaudeTier.OPUS -> R.string.asst_tier_opus
    AssistantSettingsRules.ClaudeTier.SONNET -> R.string.asst_tier_sonnet
    AssistantSettingsRules.ClaudeTier.HAIKU -> R.string.asst_tier_haiku
    null -> R.string.asst_tier_unknown
}

@Composable
private fun OpenAiCard(ui: AssistantUiModel, baseUrl: String, images: Boolean, tools: Boolean, debuggable: Boolean) {
    val settings = ui.ctl.settings
    var text by remember(baseUrl) { mutableStateOf(baseUrl) }
    var error by remember { mutableStateOf<String?>(null) }
    val saved = stringResource(R.string.screens_saved)
    AppCard(Modifier.fillMaxWidth()) {
        Title(stringResource(R.string.asst_url_title))
        Spacer(Modifier.height(8.dp))
        GymField(text, { text = it.take(300); error = null }, stringResource(R.string.asst_url_field), keyboardType = KeyboardType.Uri, isError = error != null)
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(4.dp))
        Note(stringResource(if (debuggable) R.string.asst_url_note_debug else R.string.asst_url_note))
        Spacer(Modifier.height(10.dp))
        SecondaryButton(
            stringResource(R.string.screens_save),
            onClick = {
                val err = AssistantSettingsRules.baseUrlError(text, debuggable)
                if (err != null) error = err else if (settings.setBaseUrl(text)) { ui.events.tryEmit(UiEvent(saved)); ui.bump() } else error = ui.msg(R.string.asst_url_invalid)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        CheckRow(stringResource(R.string.asst_cap_images), images) { settings.setSupportsImages(it) }
        CheckRow(stringResource(R.string.asst_cap_tools), tools) { settings.setSupportsTools(it) }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LimitCard(ui: AssistantUiModel, provider: ProviderId, sending: Boolean) {
    val settings = ui.ctl.settings
    val savedLimit = remember(provider, ui.version) { settings.limitOf(provider) }
    var text by remember(provider, savedLimit) { mutableStateOf(savedLimit?.toString() ?: "") }
    var error by remember { mutableStateOf(false) }
    val saved = stringResource(R.string.screens_saved)
    val remaining = remember(ui.version, sending, provider, savedLimit) { ui.ctl.remainingRequests() }
    val reopens = remember(ui.version, sending, provider, savedLimit) { ui.ctl.reopensAt() }
    AppCard(Modifier.fillMaxWidth()) {
        Title(stringResource(R.string.asst_limit_title))
        Spacer(Modifier.height(8.dp))
        GymField(
            text, { text = it.filter(Char::isDigit).take(5); error = false }, stringResource(R.string.asst_limit_field),
            keyboardType = KeyboardType.Number, suffix = stringResource(R.string.asst_limit_suffix), isError = error,
        )
        if (error) Text(stringResource(R.string.asst_limit_invalid, ConfigRules.minimumUsableLimit(), ConfigRules.MAX_DAILY_LIMIT), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(4.dp))
        Note(stringResource(R.string.asst_limit_note))
        Spacer(Modifier.height(10.dp))
        SecondaryButton(
            stringResource(R.string.screens_save),
            onClick = {
                val ok = when (val r = AssistantSettingsRules.parseLimit(text)) {
                    AssistantSettingsRules.LimitParse.Invalid -> false
                    AssistantSettingsRules.LimitParse.Unlimited -> settings.setDailyLimit(provider, null)
                    is AssistantSettingsRules.LimitParse.Value -> settings.setDailyLimit(provider, r.n)
                }
                if (ok) { error = false; ui.events.tryEmit(UiEvent(saved)); ui.bump() } else error = true
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (savedLimit == null) stringResource(R.string.asst_limit_unlimited)
            else stringResource(R.string.asst_limit_remaining, remaining ?: 0, ConfigRules.effectiveCap(savedLimit) ?: 0),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface,
        )
        reopens?.let {
            Text(stringResource(R.string.asst_reopens, ErrorBands.reopenText(it, System.currentTimeMillis())), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AutoAddCard(ui: AssistantUiModel, autoAdd: Boolean) {
    var warn by remember { mutableStateOf(false) }
    val c = GymTheme.colors
    AppCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(autoAdd, role = Role.Switch) { on ->
                if (on) warn = true else ui.ctl.settings.setAutoAdd(false)
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Title(stringResource(R.string.asst_auto_title))
                Note(stringResource(R.string.asst_auto_note))
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = autoAdd, onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = c.accentFill, checkedThumbColor = c.onAccentFill),
            )
        }
    }
    if (warn) {
        ConfirmDialog(
            title = stringResource(R.string.asst_auto_warn_title),
            text = stringResource(R.string.asst_auto_warn_text),
            confirmLabel = stringResource(R.string.asst_auto_warn_confirm),
            onConfirm = { warn = false; ui.ctl.settings.setAutoAdd(true) },
            onDismiss = { warn = false },
            destructive = true,
        )
    }
}

@Composable
private fun TestCard(ui: AssistantUiModel, hasKey: Boolean) {
    val st = ui.testState
    AppCard(Modifier.fillMaxWidth()) {
        Note(stringResource(R.string.asst_test_note))
        Spacer(Modifier.height(10.dp))
        SecondaryButton(
            stringResource(R.string.asst_test_button),
            onClick = ui::testConnection,
            enabled = hasKey && st != TestState.Running,
            modifier = Modifier.fillMaxWidth(),
        )
        when (st) {
            TestState.Running -> Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = GymTheme.colors.accent)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.asst_test_running), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is TestState.Done -> Text(
                st.message, modifier = Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium,
                color = if (st.ok) GymTheme.colors.accent else MaterialTheme.colorScheme.error,
            )
            TestState.Idle -> {}
        }
    }
}
