package app.nagmedar.ui.tvlab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nagmedar.ui.R
import app.nagmedar.ui.components.FilterChip
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.tabMovesFocus
import app.nagmedar.ui.theme.NagTheme
import java.util.Locale

/**
 * A profile in the editor (S-1203): its fields as the form holds them. [isNew], it gets an id from
 * its name when saved; otherwise it keeps [original]'s, an app profile staying one (the user's copy
 * of it).
 */
@Stable
class ProfileDraft(val original: DeviceProfile, val isNew: Boolean) {
    var name by mutableStateOf(if (isNew) "" else original.name)
    var detail by mutableStateOf(if (isNew) "" else original.detail)
    var source by mutableStateOf(original.source)

    /** The threshold in Mbps as typed; empty for none. */
    var peak by mutableStateOf(if (original.peakKbps > 0) Mbps.format(original.peakKbps) else "")
    var containers by mutableStateOf(original.containers)
    var videoCodecs by mutableStateOf(original.videoCodecs)
    var maxWidth by mutableStateOf(original.maxWidth)
    var maxHeight by mutableStateOf(original.maxHeight)
    var maxFps by mutableStateOf(original.maxFps)
    var bitDepth by mutableStateOf(original.maxBitDepth)
    var hdr by mutableStateOf(original.hdr)
    var dvProfiles by mutableStateOf(original.dvProfiles)
    var audio by mutableStateOf(original.audioCodecs)
    var subtitles by mutableStateOf(original.subtitleCodecs)
    var nagmedar by mutableStateOf(original.nagmedar)

    /** The threshold in kbps: 0 for none, null when what is typed is not a number. */
    val peakKbps: Int?
        get() = peak.trim().takeIf { it.isNotEmpty() }?.let(Mbps::parse) ?: if (peak.isBlank()) 0 else null

    val valid: Boolean get() = name.isNotBlank() && peakKbps != null

    /** The profile as edited, under [id]. */
    fun build(id: String): DeviceProfile = DeviceProfile(
        id = id,
        name = name.trim(),
        detail = detail.trim(),
        source = source,
        peakKbps = peakKbps ?: 0,
        containers = containers,
        videoCodecs = videoCodecs,
        maxWidth = maxWidth,
        maxHeight = maxHeight,
        maxFps = maxFps,
        maxBitDepth = bitDepth,
        hdr = hdr,
        dvProfiles = if (HdrFormat.DolbyVision in hdr) dvProfiles else emptySet(),
        audioCodecs = audio,
        subtitleCodecs = subtitles,
        nagmedar = nagmedar,
        builtIn = !isNew && original.builtIn,
    )

    /** Mbps as typed: "100", "62,5", "62.5". */
    object Mbps {
        fun format(kbps: Int): String = if (kbps % 1000 == 0) "${kbps / 1000}" else "%.1f".format(Locale.ROOT, kbps / 1000.0)

        fun parse(text: String): Int? = text.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 && it < 100_000 }?.let { (it * 1000).toInt() }
    }

    companion object {
        /** The choices of the form, in a profile's keys, and how they are named. */
        val CONTAINERS = listOf("MKV", "MP4", "MOV", "TS", "AVI", "ASF", "FLV").map { it to it }
        val VIDEO_CODECS = listOf(
            "hevc" to "HEVC", "h264" to "AVC", "av1" to "AV1", "vp9" to "VP9", "vp8" to "VP8",
            "mpeg2video" to "MPEG-2", "mpeg4" to "MPEG-4", "vc1" to "VC-1",
        )
        val AUDIO_CODECS = listOf(
            "ac3" to "AC-3", "eac3" to "E-AC-3", "ac4" to "AC-4", "truehd" to "TrueHD", "dts" to "DTS", "dtshd" to "DTS-HD",
            "aac" to "AAC", "mp3" to "MP3", "pcm" to "PCM", "flac" to "FLAC", "opus" to "Opus", "vorbis" to "Vorbis",
        )
        val SUBTITLE_FORMATS = listOf(
            "srt" to "SRT", "ass" to "ASS", "webvtt" to "WebVTT", "smi" to "SAMI", "tx3g" to "TX3G",
            "pgs" to "PGS", "vobsub" to "VobSub", "dvb" to "DVB",
        )

        /** The largest pictures: width, height, name. */
        val SIZES = listOf(Triple(1920, 1080, "1080p"), Triple(2560, 1440, "1440p"), Triple(3840, 2160, "2160p"), Triple(7680, 4320, "4320p"))
        val FRAME_RATES = listOf(30.0, 60.0, 120.0)
        val BIT_DEPTHS = listOf(8, 10, 12)
        val DV_PROFILES = listOf(5, 7, 8)

        /**
         * [selected] with [key] turned [on] among [all]: "*" (every key) goes when one is turned off,
         * leaving the others; the last key cannot go, a profile that plays nothing being no profile.
         */
        fun toggle(selected: Set<String>, key: String, on: Boolean, all: List<String>): Set<String> {
            val current = if (DeviceProfile.ANY in selected) all.toSet() else selected
            val next = if (on) current + key else current - key
            return next.ifEmpty { selected }
        }
    }
}

/** The editor in its dialog. */
@Composable
fun ProfileEditorDialog(
    draft: ProfileDraft,
    /** Whether the profile is kept as the user saved it: it can be deleted, or an app's restored. */
    saved: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onRemove: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ProfileEditorSurface(draft, saved, onSave, onCancel, onShare, onExport, onRemove)
    }
}

/**
 * The profile editor (S-1203): its name and description, how the device gets the file and the
 * bitrate it takes; its containers, video codecs, largest picture, frame rate and bit depth; its
 * HDR formats and Dolby Vision profiles; its audio codecs and subtitle formats; and whether it
 * plays with Nağmedar. Shared, exported, deleted (or restored, for an app's profile) from here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditorSurface(
    draft: ProfileDraft,
    saved: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    val res = resources()
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .padding(if (maxWidth < 600.dp) 8.dp else dimens.gapL)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .background(colors.surfaceRaised, shape)
                .border(1.dp, colors.borderStrong, shape)
                .padding(dimens.gapL)
                .testTag("tvlab-editor"),
        ) {
            Text(
                stringResource(if (draft.isNew) R.string.nag_tvlab_new_profile_title else R.string.nag_tvlab_edit_profile),
                style = NagTheme.type.title,
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.padding(top = dimens.gapS))
            HorizontalRule()
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = dimens.gapM),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Field(stringResource(R.string.nag_tvlab_name), draft.name, { draft.name = it }, stringResource(R.string.nag_tvlab_name_hint))
                Field(stringResource(R.string.nag_tvlab_detail), draft.detail, { draft.detail = it }, stringResource(R.string.nag_tvlab_detail_hint))
                Group(stringResource(R.string.nag_tvlab_link)) {
                    SegmentedChoice(
                        options = SourceLink.entries.map { it to sourceLabel(res, it) },
                        selected = draft.source,
                        onSelect = { draft.source = it },
                    )
                }
                Field(
                    stringResource(R.string.nag_tvlab_peak_field),
                    draft.peak,
                    { draft.peak = it },
                    stringResource(R.string.nag_tvlab_peak_hint),
                    number = true,
                    error = draft.peakKbps == null,
                )
                Chips(stringResource(R.string.nag_tvlab_containers), ProfileDraft.CONTAINERS, draft.containers) { draft.containers = it }
                Chips(stringResource(R.string.nag_tvlab_video_codecs), ProfileDraft.VIDEO_CODECS, draft.videoCodecs) { draft.videoCodecs = it }
                Group(stringResource(R.string.nag_tvlab_max_size)) {
                    SegmentedChoice(
                        options = ProfileDraft.SIZES.map { (_, height, name) -> height to name },
                        selected = draft.maxHeight,
                        onSelect = { height ->
                            ProfileDraft.SIZES.first { it.second == height }.let { (width, h, _) ->
                                draft.maxWidth = width
                                draft.maxHeight = h
                            }
                        },
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Group(stringResource(R.string.nag_tvlab_max_fps)) {
                        SegmentedChoice(
                            options = ProfileDraft.FRAME_RATES.map { it to "${it.toInt()} fps" },
                            selected = draft.maxFps,
                            onSelect = { draft.maxFps = it },
                        )
                    }
                    Group(stringResource(R.string.nag_tvlab_bit_depth)) {
                        SegmentedChoice(
                            options = ProfileDraft.BIT_DEPTHS.map { it to "$it bit" },
                            selected = draft.bitDepth,
                            onSelect = { draft.bitDepth = it },
                        )
                    }
                }
                Group(stringResource(R.string.nag_tvlab_hdr)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HdrFormat.entries.forEach { format ->
                            FilterChip(format.label, format in draft.hdr, { on -> draft.hdr = if (on) draft.hdr + format else draft.hdr - format })
                        }
                    }
                }
                if (HdrFormat.DolbyVision in draft.hdr) {
                    Group(stringResource(R.string.nag_tvlab_dv_profiles)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProfileDraft.DV_PROFILES.forEach { profile ->
                                FilterChip(
                                    "P$profile",
                                    profile in draft.dvProfiles,
                                    { on -> draft.dvProfiles = if (on) draft.dvProfiles + profile else draft.dvProfiles - profile },
                                )
                            }
                        }
                    }
                }
                Chips(stringResource(R.string.nag_tvlab_audio_codecs), ProfileDraft.AUDIO_CODECS, draft.audio) { draft.audio = it }
                Chips(stringResource(R.string.nag_tvlab_subtitle_formats), ProfileDraft.SUBTITLE_FORMATS, draft.subtitles) { draft.subtitles = it }
                NagCheckbox(draft.nagmedar, { draft.nagmedar = it }, stringResource(R.string.nag_tvlab_plays_with_nagmedar))
            }
            HorizontalRule()
            Spacer(Modifier.padding(top = dimens.gapM))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!draft.isNew) {
                    NagButton(stringResource(R.string.nag_lab_share), onShare)
                    NagButton(stringResource(R.string.nag_tvlab_export_profile), onExport)
                    if (saved) {
                        NagButton(
                            stringResource(if (draft.original.builtIn) R.string.nag_tvlab_reset_profile else R.string.nag_tvlab_delete_profile),
                            onRemove,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                NagButton(stringResource(R.string.nag_sc_cancel), onCancel)
                NagButton(stringResource(R.string.nag_tvlab_save), onSave, primary = true, enabled = draft.valid)
            }
        }
    }
}

/** A part of the form: its name over its control. */
@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary)
        content()
    }
}

/** A text field with its name; [number] brings the digits' keyboard, [error] a red border. */
@Composable
private fun Field(title: String, value: String, onValue: (String) -> Unit, hint: String, number: Boolean = false, error: Boolean = false) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    val focusManager = LocalFocusManager.current
    Group(title) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.minTarget.coerceAtLeast(40.dp))
                .background(colors.surface, shape)
                .border(1.dp, if (error) colors.danger else colors.borderStrong, shape)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) Text(hint, style = NagTheme.type.body, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                textStyle = NagTheme.type.body.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth().tabMovesFocus(focusManager).semantics { contentDescription = title },
            )
        }
    }
}

/**
 * The keys of a set as chips, "All" first: on, it stands for every key, known here or not, and
 * every chip shows on; turning one off leaves the others.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(title: String, options: List<Pair<String, String>>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    val keys = options.map { it.first }
    val all = DeviceProfile.ANY in selected
    Group(title) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(stringResource(R.string.nag_tvlab_all), all, { on -> onChange(if (on) setOf(DeviceProfile.ANY) else keys.toSet()) })
            options.forEach { (key, label) ->
                FilterChip(label, all || key in selected, { on -> onChange(ProfileDraft.toggle(selected, key, on, keys)) })
            }
        }
    }
}
