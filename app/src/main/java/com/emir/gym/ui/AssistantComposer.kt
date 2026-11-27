package com.emir.gym.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.emir.gym.R
import com.emir.gym.assistant.AssistantConfig
import com.emir.gym.assistant.AssistantEngine
import com.emir.gym.assistant.PreparedImage
import com.emir.gym.ui.theme.GymTheme
import com.emir.gym.ui.theme.Radii
import java.io.File

/** Giris cubugu: gorsel cipleri, kamera/galeri, metin alani, gonder/iptal. Klavyeyle birlikte yukari kayar. */
@Composable
fun ChatComposer(ui: AssistantUiModel, config: AssistantConfig, configured: Boolean, sending: Boolean, onOpenSettings: () -> Unit) {
    val c = GymTheme.colors
    val context = LocalContext.current
    val imagesOk = ComposerRules.supportsImages(config)
    val attached = ui.attached
    val preparing = ui.preparing
    val canSend = ComposerRules.canSend(ui.draft, attached.size, sending, preparing > 0, configured)
    val canAttach = ComposerRules.canAttach(imagesOk, attached.size + preparing, sending, preparing > 0, configured)

    var camPath by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val p = camPath
        camPath = null
        if (p != null) {
            val f = File(p)
            if (ok && f.isFile) ui.addImages(listOf(Uri.fromFile(f)), deleteSource = true) else f.delete()
        }
    }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) ui.addImages(listOf(u)) }
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(ComposerRules.MAX_IMAGES)) { l ->
        ui.addImages(l.take(ComposerRules.remainingSlots(ui.attached.size + ui.preparing)))
    }

    // Onceki oturumdan kalan gecici kamera dosyalari (surec/etkinlik olumu) temizlenir.
    LaunchedEffect(Unit) {
        File(context.cacheDir, "camera").listFiles()?.forEach { if (it.absolutePath != camPath) it.delete() }
    }

    fun reason(): String = when {
        !configured -> context.getString(R.string.asst_need_setup)
        !imagesOk -> context.getString(R.string.asst_images_unsupported)
        sending -> context.getString(R.string.asst_wait_sending)
        preparing > 0 -> context.getString(R.string.asst_wait_preparing)
        else -> context.getString(R.string.asst_image_limit, ComposerRules.MAX_IMAGES)
    }

    fun launchCamera() {
        if (!canAttach) { ui.events.tryEmit(UiEvent(reason())); return }
        try {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val f = File(dir, "cam_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            camPath = f.absolutePath
            camera.launch(uri)
        } catch (e: Exception) {
            camPath = null
            ui.events.tryEmit(UiEvent(context.getString(R.string.asst_camera_failed)))
        }
    }

    fun launchGallery() {
        if (!canAttach) { ui.events.tryEmit(UiEvent(reason())); return }
        try {
            val req = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            if (ComposerRules.remainingSlots(attached.size + preparing) >= 2) pickMany.launch(req) else pickOne.launch(req)
        } catch (e: Exception) {
            ui.events.tryEmit(UiEvent(context.getString(R.string.asst_gallery_failed)))
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(c.barBackground)
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).union(WindowInsets.ime)),
    ) {
        HorizontalDivider(thickness = 1.dp, color = c.cardBorder)
        Column(Modifier.fillMaxWidth().widthIn(max = MaxContentWidth), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (attached.isNotEmpty() || preparing > 0) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    attached.forEach { AttachmentChip(ui, it) }
                    repeat(preparing) {
                        Box(Modifier.size(56.dp).clip(Radii.inner).background(c.track), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.accent)
                        }
                    }
                }
            }
            if (!imagesOk) {
                Text(
                    stringResource(R.string.asst_images_unsupported),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 6.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
                ChatIconButton(AssistantIcons.Camera, stringResource(R.string.asst_camera), onClick = ::launchCamera, dim = !canAttach)
                ChatIconButton(AssistantIcons.Photo, stringResource(R.string.asst_gallery), onClick = ::launchGallery, dim = !canAttach)
                OutlinedTextField(
                    value = ui.draft,
                    onValueChange = { ui.draft = it.take(AssistantEngine.MAX_TEXT) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).padding(horizontal = 2.dp),
                    placeholder = { Text(stringResource(if (configured) R.string.asst_hint else R.string.asst_hint_setup), maxLines = 1) },
                    enabled = configured,
                    shape = Radii.inner,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
                )
                if (sending) {
                    ChatIconButton(AssistantIcons.Stop, stringResource(R.string.asst_cancel), onClick = ui::cancel, filled = true)
                } else {
                    ChatIconButton(AssistantIcons.Send, stringResource(R.string.asst_send), onClick = { ui.send(configured) }, filled = true, dim = !canSend)
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(ui: AssistantUiModel, img: PreparedImage) {
    val c = GymTheme.colors
    val desc = stringResource(R.string.asst_remove_image)
    Box(Modifier.size(56.dp)) {
        ImageThumb(ui.ctl, img.path, 56)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(40.dp)
                .clip(CircleShape)
                .semantics { contentDescription = desc }
                .clickable(role = androidx.compose.ui.semantics.Role.Button) { ui.removeImage(img) },
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).border(1.dp, c.stroke, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(com.emir.gym.ui.components.GymIcons.Close, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(12.dp))
            }
        }
    }
}
