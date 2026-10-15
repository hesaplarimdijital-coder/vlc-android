package app.nagmedar.ui.convert

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.kiloHertz
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.components.MenuButton
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.tabMovesFocus
import app.nagmedar.ui.components.toneColors
import app.nagmedar.ui.player.navigate.DialogCard
import app.nagmedar.ui.player.navigate.DialogFrame
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.tvlab.resources

/** S-1303: the convert window (Ctrl+R), over the app. */
@Composable
fun ConvertDialog(
    media: ConvertMedia?,
    profiles: List<ConvertProfile>,
    selected: ConvertProfile,
    onSelect: (ConvertProfile) -> Unit,
    onPickMedia: () -> Unit,
    onNewProfile: () -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogFrame(onDismiss) { ConvertSurface(media, profiles, selected, onSelect, onPickMedia, onNewProfile, onStart, onDismiss) }
    }
}

/**
 * S-1303: "Dönüştür/Kaydet": the media (picked here, or the one asked for), the profile with what
 * it makes, then Save and start: the system's picker names the new file, the job joins the queue.
 * The source file is never written: a conversion always makes a new one.
 */
@Composable
fun ConvertSurface(
    media: ConvertMedia?,
    profiles: List<ConvertProfile>,
    selected: ConvertProfile,
    onSelect: (ConvertProfile) -> Unit,
    onPickMedia: () -> Unit,
    onNewProfile: () -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val res = resources()
    val colors = NagTheme.colors
    val problems = ConvertProfiles.validate(selected)
    DialogCard(stringResource(R.string.nag_conv_title), Modifier.testTag("convert-dialog"), cardWidth = 520.dp) {
        SettingRow(stringResource(R.string.nag_conv_media), subtitle = media?.name ?: stringResource(R.string.nag_conv_media_none)) {
            NagButton(stringResource(R.string.nag_conv_media_pick), onPickMedia, modifier = Modifier.testTag("convert-pick-media"))
        }
        Text(stringResource(R.string.nag_conv_profile), style = NagTheme.type.bodyStrong, color = colors.text)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val names = profiles.associateWith { profileName(it) }
            MenuButton(
                text = profileName(selected),
                entries = {
                    profiles.map { profile ->
                        MenuEntry.Item(names.getValue(profile), { onSelect(profile) }, checked = profile.id == selected.id, enabled = ConvertProfiles.validate(profile).isEmpty())
                    } + listOf(MenuEntry.Separator, MenuEntry.Item(res.getString(R.string.nag_conv_profile_new), onNewProfile))
                },
                modifier = Modifier.weight(1f, fill = false).testTag("convert-profile-menu"),
            )
        }
        Text(profileSummary(res, selected), style = NagTheme.type.labelSmall, color = colors.textSecondary)
        problems.forEach { Text(stringResource(it.message), style = NagTheme.type.labelSmall, color = toneColors(StateTone.Warning).content) }
        Text(stringResource(R.string.nag_conv_where), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            NagButton(stringResource(R.string.nag_sc_cancel), onDismiss)
            NagButton(
                stringResource(R.string.nag_conv_start),
                onStart,
                primary = true,
                enabled = media != null && problems.isEmpty(),
                modifier = Modifier.testTag("convert-start"),
            )
        }
    }
}

/** S-1303: the profile editor, over the app. */
@Composable
fun ProfileEditorDialog(initial: ConvertProfile, problemsOf: (ConvertProfile) -> List<ProfileProblem>, onSave: (ConvertProfile) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogFrame(onDismiss) { ProfileEditorSurface(initial, problemsOf, onSave, onDismiss) }
    }
}

/**
 * S-1303: a profile edited: its name; the container; the video (none, copied, or an encoder with
 * its bit rate, largest height and frame rate); the audio (none, copied, or an encoder with its
 * bit rate, channels and sample rate); the subtitles. A container change keeps what it can; what
 * breaks the rules is said under it, and Save waits until nothing does.
 */
@Composable
fun ProfileEditorSurface(initial: ConvertProfile, problemsOf: (ConvertProfile) -> List<ProfileProblem>, onSave: (ConvertProfile) -> Unit, onDismiss: () -> Unit) {
    val res = resources()
    val colors = NagTheme.colors
    val focusManager = LocalFocusManager.current
    var profile by remember { mutableStateOf(initial) }
    val problems = problemsOf(profile)
    val none = stringResource(R.string.nag_conv_none)
    val copy = stringResource(R.string.nag_conv_copy)
    val source = stringResource(R.string.nag_conv_as_source)
    DialogCard(
        stringResource(if (initial.id > 0) R.string.nag_conv_profile_edit else R.string.nag_conv_profile_new),
        // a margin over and under: on a short window the settings scroll, the summary and the buttons stay
        Modifier.testTag("convert-profile-editor").padding(vertical = 24.dp),
        cardWidth = 600.dp,
    ) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val nameLabel = stringResource(R.string.nag_conv_profile_name)
            Text(nameLabel, style = NagTheme.type.bodyStrong, color = colors.text)
            BasicTextField(
                value = profile.name,
                onValueChange = { profile = profile.copy(name = it.take(NAME_MAX)) },
                singleLine = true,
                textStyle = NagTheme.type.body.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.borderStrong, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .semantics { contentDescription = nameLabel }
                    .tabMovesFocus(focusManager)
                    .testTag("convert-profile-name"),
            )
            SettingRow(stringResource(R.string.nag_conv_container)) {
                MenuButton(
                    profile.container.label,
                    {
                        ConvertContainer.entries.map { container ->
                            MenuEntry.Item(container.label, { profile = ConvertProfiles.fitTo(profile, container) }, checked = container == profile.container)
                        }
                    },
                    modifier = Modifier.testTag("convert-container"),
                )
            }
            if (profile.container.holdsVideo) {
                val video = profile.video
                SettingRow(stringResource(R.string.nag_conv_video)) {
                    MenuButton(
                        video?.let { if (it.codec == VideoCodec.Copy) copy else it.codec.label } ?: none,
                        {
                            listOf(MenuEntry.Item(none, { profile = profile.copy(video = null) }, checked = video == null)) +
                                profile.container.video.map { codec ->
                                    MenuEntry.Item(
                                        if (codec == VideoCodec.Copy) copy else codec.label,
                                        { profile = profile.copy(video = (video ?: VideoSettings(codec)).copy(codec = codec)) },
                                        checked = video?.codec == codec,
                                        enabled = EncoderSupport.available(codec),
                                    )
                                }
                        },
                        modifier = Modifier.testTag("convert-video-codec"),
                    )
                }
                if (video != null && video.codec != VideoCodec.Copy) {
                    NumberRow(stringResource(R.string.nag_conv_bitrate), video.bitrateKbps, "kbps", "convert-video-kbps") { profile = profile.copy(video = video.copy(bitrateKbps = it)) }
                    SettingRow(stringResource(R.string.nag_conv_height)) {
                        MenuButton(
                            video.maxHeight?.let { res.getString(R.string.nag_conv_sum_height, it) } ?: source,
                            {
                                listOf(MenuEntry.Item(source, { profile = profile.copy(video = video.copy(maxHeight = null)) }, checked = video.maxHeight == null)) +
                                    ConvertProfiles.HEIGHTS.map { h ->
                                        MenuEntry.Item(res.getString(R.string.nag_conv_sum_height, h), { profile = profile.copy(video = video.copy(maxHeight = h)) }, checked = video.maxHeight == h)
                                    }
                            },
                        )
                    }
                    SettingRow(stringResource(R.string.nag_conv_fps)) {
                        // menus rather than segments: a phone's dialog is narrower than five choices
                        val fpsLabel = { fps: Int? -> fps?.let { res.getString(R.string.nag_vis_fps_value, it) } ?: source }
                        MenuButton(
                            fpsLabel(video.fps),
                            {
                                listOf<Int?>(null, 24, 25, 30, 60).map { fps ->
                                    MenuEntry.Item(fpsLabel(fps), { profile = profile.copy(video = video.copy(fps = fps)) }, checked = video.fps == fps)
                                }
                            },
                            modifier = Modifier.testTag("convert-fps"),
                        )
                    }
                }
            }
            val audio = profile.audio
            SettingRow(stringResource(R.string.nag_conv_audio)) {
                MenuButton(
                    audio?.let { if (it.codec == AudioCodec.Copy) copy else it.codec.label } ?: none,
                    {
                        val off = if (profile.container.holdsVideo) listOf(MenuEntry.Item(none, { profile = profile.copy(audio = null) }, checked = audio == null)) else emptyList()
                        off + profile.container.audio.map { codec ->
                            MenuEntry.Item(
                                if (codec == AudioCodec.Copy) copy else codec.label,
                                { profile = profile.copy(audio = (audio ?: AudioSettings(codec)).copy(codec = codec)) },
                                checked = audio?.codec == codec,
                            )
                        }
                    },
                    modifier = Modifier.testTag("convert-audio-codec"),
                )
            }
            if (audio != null && audio.codec != AudioCodec.Copy) {
                if (!audio.codec.lossless) {
                    NumberRow(stringResource(R.string.nag_conv_bitrate), audio.bitrateKbps, "kbps", "convert-audio-kbps") { profile = profile.copy(audio = audio.copy(bitrateKbps = it)) }
                }
                SettingRow(stringResource(R.string.nag_conv_channels)) {
                    SegmentedChoice(
                        options = listOf(1, 2, 6).filter { it <= audio.codec.maxChannels }.map { it to channelsLabel(res, it) },
                        selected = audio.channels,
                        onSelect = { profile = profile.copy(audio = audio.copy(channels = it)) },
                    )
                }
                if (audio.codec != AudioCodec.Opus) {
                    SettingRow(stringResource(R.string.nag_conv_rate)) {
                        val rateLabel = { rate: Int? -> rate?.let { kiloHertzLabel(res, it) } ?: source }
                        MenuButton(
                            rateLabel(audio.sampleRate),
                            {
                                (listOf<Int?>(null) + ConvertProfiles.SAMPLE_RATES).map { rate ->
                                    MenuEntry.Item(rateLabel(rate), { profile = profile.copy(audio = audio.copy(sampleRate = rate)) }, checked = audio.sampleRate == rate)
                                }
                            },
                            modifier = Modifier.testTag("convert-rate"),
                        )
                    }
                }
            }
            if (profile.container.holdsVideo) {
                SettingRow(stringResource(R.string.nag_conv_subtitles)) {
                    MenuButton(
                        res.getString(profile.subtitles.label),
                        {
                            SubtitleMode.entries.map { mode ->
                                MenuEntry.Item(res.getString(mode.label), { profile = profile.copy(subtitles = mode) }, checked = profile.subtitles == mode)
                            }
                        },
                        modifier = Modifier.testTag("convert-subtitles"),
                    )
                }
            }
        }
        // what the profile makes, and why it cannot be saved yet: always in sight, by the buttons
        Text(profileSummary(res, profile), style = NagTheme.type.labelSmall, color = colors.textSecondary)
        problems.forEach {
            Text(stringResource(it.message), style = NagTheme.type.labelSmall, color = toneColors(StateTone.Warning).content, modifier = Modifier.testTag("convert-profile-problem"))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            NagButton(stringResource(R.string.nag_sc_cancel), onDismiss)
            NagButton(
                stringResource(R.string.nag_conv_profile_save),
                { onSave(profile.copy(name = profile.name.trim())) },
                primary = true,
                enabled = problems.isEmpty(),
                modifier = Modifier.testTag("convert-profile-save"),
            )
        }
    }
}

/** A number typed in, with its unit: digits only; what is not a number keeps the one before. */
@Composable
private fun NumberRow(title: String, value: Int, unit: String, tag: String, onChange: (Int) -> Unit) {
    val colors = NagTheme.colors
    var text by remember(value) { mutableStateOf(value.toString()) }
    SettingRow(title) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicTextField(
                value = text,
                onValueChange = { typed ->
                    val digits = typed.filter(Char::isDigit).take(6)
                    text = digits
                    digits.toIntOrNull()?.let(onChange)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = NagTheme.type.mono.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .size(width = 96.dp, height = 40.dp)
                    .border(1.dp, colors.borderStrong, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .semantics { contentDescription = title }
                    .testTag(tag),
            )
            Text(unit, style = NagTheme.type.label, color = colors.textSecondary)
        }
    }
}

private fun channelsLabel(res: android.content.res.Resources, channels: Int): String = when (channels) {
    1 -> res.getString(R.string.nag_conv_mono)
    2 -> res.getString(R.string.nag_conv_stereo)
    else -> "5.1"
}

private fun kiloHertzLabel(res: android.content.res.Resources, rate: Int): String = kiloHertz(rate, res.locale())

/** The longest name a profile takes. */
private const val NAME_MAX = 60

/** S-1303: the HLS sheet ("Ağda yayınla", Ctrl+S), over the app. */
@Composable
fun HlsDialog(
    media: ConvertMedia?,
    state: HlsState,
    quality: HlsQuality,
    onQuality: (HlsQuality) -> Unit,
    onPickMedia: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogFrame(onDismiss) { HlsSurface(media, state, quality, onQuality, onPickMedia, onStart, onStop, onCopy, onDismiss) }
    }
}

/**
 * S-1303: the HLS sheet: the media and the quality, Start; on the air the address with its QR
 * code to open on another device of the same network, Copy and Stop. Only on Wi-Fi or Ethernet:
 * off a local network it says so.
 */
@Composable
fun HlsSurface(
    media: ConvertMedia?,
    state: HlsState,
    quality: HlsQuality,
    onQuality: (HlsQuality) -> Unit,
    onPickMedia: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NagTheme.colors
    DialogCard(stringResource(R.string.nag_hls_title), Modifier.testTag("hls-dialog"), cardWidth = 560.dp) {
        when (state) {
            is HlsState.Live, is HlsState.Starting -> HlsBody(state, onStart = onStart, onStop = onStop, onCopy = onCopy, onDismiss = {})
            else -> {
                SettingRow(stringResource(R.string.nag_conv_media), subtitle = media?.name ?: stringResource(R.string.nag_conv_media_none)) {
                    NagButton(stringResource(R.string.nag_conv_media_pick), onPickMedia)
                }
                val res = resources()
                SettingRow(stringResource(R.string.nag_hls_quality)) {
                    MenuButton(
                        res.getString(quality.label),
                        { HlsQuality.entries.map { q -> MenuEntry.Item(res.getString(q.label), { onQuality(q) }, checked = q == quality) } },
                        modifier = Modifier.testTag("hls-quality"),
                    )
                }
                Text(stringResource(R.string.nag_hls_idle), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                (state as? HlsState.Failed)?.let {
                    Text(stringResource(it.reason.message), style = NagTheme.type.label, color = toneColors(StateTone.Error).content, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            NagButton(stringResource(R.string.nag_hls_close), onDismiss)
            if (state is HlsState.Idle || state is HlsState.Failed) {
                NagButton(stringResource(R.string.nag_hls_start), onStart, primary = true, enabled = media != null, modifier = Modifier.testTag("hls-start"))
            }
        }
    }
}
