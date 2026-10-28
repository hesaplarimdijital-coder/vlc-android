package app.nagmedar.ui.screens

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
import androidx.compose.foundation.lazy.items
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
import app.nagmedar.ui.prefs.NagmedarLink
import app.nagmedar.ui.components.SwatchChoice
import app.nagmedar.ui.components.TooltipDelay
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.covers.CoversSection
import app.nagmedar.ui.lab.LabSection
import app.nagmedar.ui.library.LibrarySourcesSection
import app.nagmedar.ui.lyrics.LyricsSection
import app.nagmedar.ui.perf.PerformanceSection
import app.nagmedar.ui.radio.record.RadioRecordingSection
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
import app.nagmedar.ui.transcribe.WhisperSettings
import org.videolan.tools.NAGMEDAR_MEDIA3_SESSION
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig

/**
 * The sections of [SettingsScreen], in the order its list shows them: the list is made of these, each
 * under its [key], so a link to one (the shortcut card's "Edit shortcuts…", "Klasör ekle", "All
 * settings"' lines) scrolls to [index], wherever sections are added or moved.
 */
enum class SettingsItem(val key: String) {
    Header("header"),
    Help("help"),
    Appearance("appearance"),
    Input("input"),
    Shortcuts("shortcuts"),
    Library("library"),
    Whisper("whisper"),
    Music("music"),
    Latency("latency"),
    Scrobble("scrobble"),
    Visualizer("visualizer"),
    Alarm("alarm"),
    RadioRecording("radio_recording"),
    Lyrics("lyrics"),
    Covers("covers"),
    Podcasts("podcasts"),
    RemoteAccess("remote_access"),
    Backup("backup"),
    Start("start"),
    About("about"),
    Lab("lab"),
    Performance("performance"),
    ;

    /** Its place in the list. */
    val index: Int get() = entries.indexOf(this)
}

/** The section of [SettingsScreen] a line of "All settings" leads to; null for a page of its own (the equaliser, About). */
val NagmedarLink.settingsItem: SettingsItem?
    get() = when (this) {
        NagmedarLink.Library -> SettingsItem.Library
        NagmedarLink.Appearance -> SettingsItem.Appearance
        NagmedarLink.RemoteAccess -> SettingsItem.RemoteAccess
        NagmedarLink.Backup -> SettingsItem.Backup
        NagmedarLink.Equalizer, NagmedarLink.About -> null
    }

@Composable
fun SettingsScreen(
    prefs: NagmedarPreferences,
    /** VLC's preference pages, drawn by Nağmedar ("All settings"). */
    onOpenAllSettings: () -> Unit,
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
        items(SettingsItem.entries, key = { it.key }) { entry ->
            when (entry) {
                SettingsItem.Header -> PageHeader(title = stringResource(R.string.nag_nav_settings))
                // S-1504: the guide inside the app, and every key on one card
                SettingsItem.Help -> HelpSection(onOpenGuide, onOpenShortcutCard)
                SettingsItem.Appearance -> AppearanceSection(prefs)
                SettingsItem.Input -> InputSection(prefs)
                SettingsItem.Shortcuts -> ShortcutEditor(prefs.shortcutOverrides, prefs::updateShortcutOverrides, undo)
                // the folders each section of the library shows
                SettingsItem.Library -> LibrarySourcesSection(prefs)
                // S-709: transcription from speech: the model, the language, the models on the device
                SettingsItem.Whisper -> WhisperSettings()
                // S-901: the engine behind the local music library
                SettingsItem.Music -> MusicSection(onOpenEqualizer = onOpenEqualizer)
                // S-613: what each output device plays late, and the measurement with the microphone
                SettingsItem.Latency -> LatencySection()
                // S-1008: what is played, sent to Last.fm and ListenBrainz
                SettingsItem.Scrobble -> ScrobbleSection()
                // S-611: the visualisers, with their battery warning
                SettingsItem.Visualizer -> VisualizerSection(prefs)
                // S-1007: waking to music
                SettingsItem.Alarm -> AlarmSection()
                // S-1107: where radio recordings go, which tracks are kept and how much room they may take
                SettingsItem.RadioRecording -> RadioRecordingSection(prefs)
                // S-1003: where lyrics come from
                SettingsItem.Lyrics -> LyricsSection()
                // S-1006: where album covers come from
                SettingsItem.Covers -> CoversSection()
                // S-1103, S-1104: refreshing the podcasts, their downloads and the skip buttons
                SettingsItem.Podcasts -> PodcastSection()
                // S-1304: the web page that browses and plays from another device
                SettingsItem.RemoteAccess -> RemoteAccessSection()
                // S-1502: the app's settings, lists and statistics in one file, and back
                SettingsItem.Backup -> BackupSection()
                SettingsItem.Start -> SettingsSection(stringResource(R.string.nag_settings_start)) {
                    NagButton(stringResource(R.string.nag_settings_first_run_again), onShowFirstRun)
                }
                SettingsItem.About -> SettingsSection(stringResource(R.string.nag_settings_about)) {
                    Text(
                        stringResource(R.string.nag_settings_version, BuildConfig.VLC_VERSION_NAME, BuildConfig.LIBVLC_VERSION),
                        style = NagTheme.type.body,
                        color = NagTheme.colors.textSecondary,
                    )
                    Spacer(Modifier.height(dimens.gapM))
                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                        NagButton(stringResource(R.string.nag_about_open), onOpenAbout, primary = true)
                        NagButton(stringResource(R.string.nag_prefs_title), onOpenAllSettings)
                    }
                }
                SettingsItem.Lab -> LabSection(section = { title, content -> SettingsSection(title, content) }) { MediaSessionSwitch() }
                // S-1404: the start-ups and playbacks this device measured
                SettingsItem.Performance -> PerformanceSection(section = { title, content -> SettingsSection(title, content) })
            }
        }
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

/** Input (S-201, S-404): density and the tooltip delay. */
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
