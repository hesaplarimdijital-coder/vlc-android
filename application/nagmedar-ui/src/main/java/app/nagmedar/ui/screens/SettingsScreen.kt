package app.nagmedar.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.MusicSection
import app.nagmedar.ui.audio.latency.LatencySection
import app.nagmedar.ui.backup.BackupSection
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.player.subtitles.providers.SubtitleProvidersSettings
import app.nagmedar.ui.player.subtitles.whisper.WhisperSettings
import app.nagmedar.ui.components.SwatchChoice
import app.nagmedar.ui.components.TooltipDelay
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.covers.CoversSection
import app.nagmedar.ui.lab.LabSection
import app.nagmedar.ui.library.LibrarySourcesSection
import app.nagmedar.ui.lyrics.LyricsSection
import app.nagmedar.ui.perf.PerformanceSection
import app.nagmedar.ui.player.record.RecordingSection
import app.nagmedar.ui.radio.record.RadioRecordingSection
import app.nagmedar.ui.player.snapshot.HdrSnapshot
import app.nagmedar.ui.player.snapshot.SnapshotFormat
import app.nagmedar.ui.player.snapshot.defaultSnapshotFolder
import app.nagmedar.ui.player.snapshot.forgetSnapshotFolder
import app.nagmedar.ui.player.snapshot.keepSnapshotFolder
import app.nagmedar.ui.player.snapshot.snapshotFolderLabel
import app.nagmedar.ui.podcast.PodcastSection
import app.nagmedar.ui.remoteaccess.RemoteAccessSection
import app.nagmedar.ui.scrobble.ScrobbleSection
import app.nagmedar.ui.sleep.AlarmSection
import app.nagmedar.ui.theme.Accent
import app.nagmedar.ui.theme.AccentTones
import app.nagmedar.ui.theme.DensityPreference
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.ThemeMode
import app.nagmedar.ui.visual.VisualizerSection
import app.nagmedar.ui.theme.systemAccentSeed
import org.videolan.tools.NAGMEDAR_MEDIA3_SESSION
import org.videolan.tools.NAGMEDAR_MIDDLE_MUTE
import org.videolan.tools.NAGMEDAR_MIDDLE_NONE
import org.videolan.tools.NAGMEDAR_MIDDLE_PLAY_PAUSE
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig

/** Index of the keyboard shortcut section in [SettingsScreen]'s list, for the card's "Edit shortcuts…" (S-1504). */
const val SETTINGS_SHORTCUTS_ITEM = 4

@Composable
fun SettingsScreen(
    prefs: NagmedarPreferences,
    onOpenClassicSettings: () -> Unit,
    /** S-209: about and licences. */
    onOpenAbout: () -> Unit,
    onShowFirstRun: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    /** Takes back a reset, a preset or an import of the shortcuts (S-402). */
    undo: UndoBarState? = null,
    /** S-906: the music engine's equaliser. */
    onOpenEqualizer: (() -> Unit)? = null,
    /** S-1504: the user guide and the shortcut card. */
    onOpenGuide: (() -> Unit)? = null,
    onOpenShortcutCard: (() -> Unit)? = null,
) {
    val dimens = NagTheme.dimens
    LazyColumn(
        state = listState,
        // the macrobenchmarks scroll it by this tag (S-1404)
        modifier = Modifier.fillMaxSize().testTag("settings"),
        contentPadding = PaddingValues(bottom = dimens.gapXl),
        horizontalAlignment = Alignment.Start,
    ) {
        item { PageHeader(title = stringResource(R.string.nag_nav_settings)) }
        // S-1504: the guide inside the app, and every key on one card
        item { HelpSection(onOpenGuide, onOpenShortcutCard) }
        item { AppearanceSection(prefs) }
        item { InputSection(prefs) }
        item { ShortcutEditor(prefs.shortcutOverrides, prefs::updateShortcutOverrides, undo) }
        // the folders each section of the library shows
        item { LibrarySourcesSection(prefs) }
        item { SnapshotSection(prefs) }
        // S-607: what Shift+R records, and where
        item { RecordingSection(prefs) }
        // S-707: where subtitles come from
        item { SubtitleProvidersSettings() }
        // S-709: the subtitles from speech: the model, the language, the models on the device
        item { WhisperSettings() }
        // S-901: the engine behind the local music library
        item { MusicSection(onOpenEqualizer = onOpenEqualizer) }
        // S-613: what each output device plays late, and the measurement with the microphone
        item { LatencySection() }
        // S-1008: what is played, sent to Last.fm and ListenBrainz
        item { ScrobbleSection() }
        // S-611: the visualisers, with their battery warning
        item { VisualizerSection(prefs) }
        // S-1007: waking to music
        item { AlarmSection() }
        // S-1107: where radio recordings go, which tracks are kept and how much room they may take
        item { RadioRecordingSection(prefs) }
        // S-1003: where lyrics come from
        item { LyricsSection() }
        // S-1006: where album covers come from
        item { CoversSection() }
        // S-1103, S-1104: refreshing the podcasts, their downloads and the skip buttons
        item { PodcastSection() }
        // S-1304: the web page that browses and plays from another device
        item { RemoteAccessSection() }
        // S-1502: the app's settings, lists and statistics in one file, and back
        item { BackupSection() }
        item {
            SettingsSection(stringResource(R.string.nag_settings_start)) {
                SettingRow(stringResource(R.string.nag_settings_launcher)) {
                    SegmentedChoice(
                        options = listOf(
                            true to stringResource(R.string.nagmedar_shell_title),
                            false to stringResource(R.string.nag_classic_interface),
                        ),
                        selected = prefs.launchNewUi,
                        onSelect = prefs::updateLaunchNewUi,
                    )
                }
                Spacer(Modifier.height(dimens.gapS))
                NagButton(stringResource(R.string.nag_settings_first_run_again), onShowFirstRun)
            }
        }
        item {
            SettingsSection(stringResource(R.string.nag_settings_about)) {
                Text(
                    stringResource(R.string.nag_settings_version, BuildConfig.VLC_VERSION_NAME, BuildConfig.LIBVLC_VERSION),
                    style = NagTheme.type.body,
                    color = NagTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(dimens.gapM))
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                    NagButton(stringResource(R.string.nag_about_open), onOpenAbout, primary = true)
                    NagButton(stringResource(R.string.nag_settings_classic_settings), onOpenClassicSettings)
                }
            }
        }
        item {
            LabSection(section = { title, content -> SettingsSection(title, content) }) { MediaSessionSwitch() }
        }
        // S-1404: the start-ups and playbacks this device measured
        item { PerformanceSection(section = { title, content -> SettingsSection(title, content) }) }
    }
}

/** Help (S-1504): the user guide and the shortcut card, both on the device; nothing when the screen opens neither. */
@Composable
internal fun HelpSection(onOpenGuide: (() -> Unit)?, onOpenShortcutCard: (() -> Unit)?) {
    if (onOpenGuide == null && onOpenShortcutCard == null) return
    SettingsSection(stringResource(R.string.nag_menu_help)) {
        Text(stringResource(R.string.nag_help_settings_hint), style = NagTheme.type.body, color = NagTheme.colors.textSecondary)
        Spacer(Modifier.height(NagTheme.dimens.gapM))
        Row(horizontalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapS)) {
            if (onOpenGuide != null) NagButton(stringResource(R.string.nag_guide_title), onOpenGuide, primary = true, icon = NagIcons.Info)
            if (onOpenShortcutCard != null) NagButton(stringResource(R.string.nag_shortcut_card_title), onOpenShortcutCard, icon = NagIcons.Keyboard)
        }
    }
}

/**
 * Input (S-201, S-404, S-408, S-409): density, the tooltip delay, the player's window on desktops,
 * and what the mouse does in the player.
 */
@Composable
internal fun InputSection(prefs: NagmedarPreferences) {
    SettingsSection(stringResource(R.string.nag_settings_input)) {
        SettingRow(stringResource(R.string.nag_settings_density)) {
            SegmentedChoice(
                options = listOf(
                    DensityPreference.Auto to stringResource(R.string.nag_theme_system),
                    DensityPreference.Touch to stringResource(R.string.nag_density_touch),
                    DensityPreference.Pointer to stringResource(R.string.nag_density_pointer),
                ),
                selected = prefs.density,
                onSelect = prefs::updateDensity,
            )
        }
        Text(stringResource(R.string.nag_density_auto), style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary)
        SettingRow(stringResource(R.string.nag_settings_tooltips), subtitle = stringResource(R.string.nag_settings_tooltips_hint)) {
            SegmentedChoice(
                options = listOf(
                    TooltipDelay.Off to stringResource(R.string.nag_tooltips_off),
                    TooltipDelay.Short to stringResource(R.string.nag_tooltips_short),
                    TooltipDelay.Normal to stringResource(R.string.nag_tooltips_normal),
                    TooltipDelay.Long to stringResource(R.string.nag_tooltips_long),
                ),
                selected = prefs.tooltipDelay,
                onSelect = prefs::updateTooltipDelay,
            )
        }
        // S-409: the player's window on desktops
        SettingRow(stringResource(R.string.nag_settings_player_fullscreen), subtitle = stringResource(R.string.nag_settings_player_fullscreen_hint)) {
            SegmentedChoice(
                options = listOf(true to stringResource(R.string.nag_choice_yes), false to stringResource(R.string.nag_choice_no)),
                selected = prefs.playerFullscreen,
                onSelect = prefs::updatePlayerFullscreen,
            )
        }
        // S-408: the mouse in the video player
        SettingRow(stringResource(R.string.nag_settings_wheel), subtitle = stringResource(R.string.nag_settings_wheel_hint)) {
            SegmentedChoice(
                options = listOf(false to stringResource(R.string.nag_wheel_volume), true to stringResource(R.string.nag_wheel_seek)),
                selected = prefs.wheelSeeks,
                onSelect = prefs::updateWheelSeeks,
            )
        }
        SettingRow(stringResource(R.string.nag_settings_middle_click), subtitle = stringResource(R.string.nag_settings_middle_click_hint)) {
            SegmentedChoice(
                options = listOf(
                    NAGMEDAR_MIDDLE_PLAY_PAUSE to stringResource(R.string.nag_middle_play_pause),
                    NAGMEDAR_MIDDLE_MUTE to stringResource(R.string.nag_middle_mute),
                    NAGMEDAR_MIDDLE_NONE to stringResource(R.string.nag_middle_none),
                ),
                selected = prefs.middleClick,
                onSelect = prefs::updateMiddleClick,
            )
        }
    }
}

/**
 * Theme and accent (S-204). A choice applies at once, system bars included; the wallpaper's
 * colour is offered first on Android 12 and later.
 */
@Composable
internal fun AppearanceSection(prefs: NagmedarPreferences) {
    SettingsSection(stringResource(R.string.nag_settings_appearance)) {
        SettingRow(stringResource(R.string.nag_settings_theme)) {
            SegmentedChoice(
                options = listOf(
                    ThemeMode.System to stringResource(R.string.nag_theme_system),
                    ThemeMode.Light to stringResource(R.string.nag_theme_light),
                    ThemeMode.Dark to stringResource(R.string.nag_theme_dark),
                    ThemeMode.Oled to stringResource(R.string.nag_theme_oled),
                ),
                selected = prefs.themeMode,
                onSelect = prefs::updateThemeMode,
            )
        }
        val seed = systemAccentSeed()
        val wallpaper = remember(seed) { seed?.let { AccentTones.fromSeed(it).base } }
        SettingRow(
            stringResource(R.string.nag_settings_accent),
            subtitle = if (wallpaper != null) stringResource(R.string.nag_accent_system_hint) else null,
        ) {
            SwatchChoice(
                options = buildList {
                    if (wallpaper != null) add(Triple(Accent.System, wallpaper, stringResource(R.string.nag_accent_system)))
                    add(Triple(Accent.Blue, Accent.Blue.base, stringResource(R.string.nag_accent_blue)))
                    add(Triple(Accent.Amber, Accent.Amber.base, stringResource(R.string.nag_accent_amber)))
                    add(Triple(Accent.Green, Accent.Green.base, stringResource(R.string.nag_accent_green)))
                    add(Triple(Accent.Coral, Accent.Coral.base, stringResource(R.string.nag_accent_coral)))
                },
                // without a wallpaper colour the system accent is blue
                selected = if (wallpaper == null && prefs.accent == Accent.System) Accent.Blue else prefs.accent,
                onSelect = prefs::updateAccent,
            )
        }
    }
}

/**
 * Screenshots (S-606): their format, whether the subtitles go in, what HDR films' pictures
 * become, and their folder, Pictures/Nağmedar unless one is chosen. The player's screenshot
 * button has the same choices in its menu.
 */
@Composable
internal fun SnapshotSection(prefs: NagmedarPreferences) {
    val context = LocalContext.current
    val settings = prefs.snapshot
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) keepSnapshotFolder(context, prefs, uri)
    }
    SettingsSection(stringResource(R.string.nag_snap_settings)) {
        // WebP is lossless from Android 10
        val lossless = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        SettingRow(stringResource(R.string.nag_snap_format), subtitle = stringResource(if (lossless) R.string.nag_snap_format_hint else R.string.nag_snap_format_hint_old)) {
            SegmentedChoice(
                options = SnapshotFormat.entries.map { it to it.label },
                selected = settings.format,
                onSelect = { prefs.updateSnapshot(settings.copy(format = it)) },
            )
        }
        SettingRow(stringResource(R.string.nag_snap_subtitles), subtitle = stringResource(R.string.nag_snap_subtitles_hint)) {
            SegmentedChoice(
                options = listOf(true to stringResource(R.string.nag_snap_with), false to stringResource(R.string.nag_snap_without)),
                selected = settings.subtitles,
                onSelect = { prefs.updateSnapshot(settings.copy(subtitles = it)) },
            )
        }
        // HDR is kept from Android 14, which copies a picture with its PQ or HLG coding
        val keepHdr = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        SettingRow(
            stringResource(R.string.nag_snap_hdr),
            subtitle = stringResource(if (keepHdr) R.string.nag_snap_hdr_hint else R.string.nag_snap_hdr_unavailable),
        ) {
            if (keepHdr) {
                SegmentedChoice(
                    options = listOf(
                        HdrSnapshot.ToneMap to stringResource(R.string.nag_snap_tone_map),
                        HdrSnapshot.Keep to stringResource(R.string.nag_snap_keep_hdr),
                    ),
                    selected = settings.hdr,
                    onSelect = { prefs.updateSnapshot(settings.copy(hdr = it)) },
                )
            }
        }
        val folder = snapshotFolderLabel(settings.folder, defaultSnapshotFolder(stringResource(R.string.nagmedar_shell_title)))
        SettingRow(stringResource(R.string.nag_snap_folder), subtitle = folder) {
            Row(horizontalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapS)) {
                if (settings.folder != null) NagButton(stringResource(R.string.nag_snap_default_folder), { forgetSnapshotFolder(context, prefs) })
                NagButton(stringResource(R.string.nag_snap_change_folder), { runCatching { picker.launch(null) } })
            }
        }
    }
}

/** S-502: which media session the playback service publishes (Settings > Lab). */
@Composable
private fun MediaSessionSwitch() {
    val context = LocalContext.current
    val vlcSettings = remember(context) { Settings.getInstance(context) }
    var media3Session by remember { mutableStateOf(vlcSettings.getBoolean(NAGMEDAR_MEDIA3_SESSION, false)) }
    SettingRow("S-502 · " + stringResource(R.string.nag_lab_media3), subtitle = stringResource(R.string.nag_lab_media3_desc)) {
        SegmentedChoice(
            options = listOf(false to stringResource(R.string.nag_lab_media3_legacy), true to stringResource(R.string.nag_lab_media3_on)),
            selected = media3Session,
            onSelect = { enabled ->
                media3Session = enabled
                vlcSettings.edit().putBoolean(NAGMEDAR_MEDIA3_SESSION, enabled).apply()
            },
        )
    }
}

@Composable
internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    Box(Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding, vertical = dimens.gapS)) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .background(colors.surface, shape)
                .border(1.dp, colors.border, shape)
                .padding(dimens.gapL),
        ) {
            Text(title, style = NagTheme.type.title, color = colors.text, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(dimens.gapS))
            HorizontalRule()
            Spacer(Modifier.height(dimens.gapS))
            content()
        }
    }
}
