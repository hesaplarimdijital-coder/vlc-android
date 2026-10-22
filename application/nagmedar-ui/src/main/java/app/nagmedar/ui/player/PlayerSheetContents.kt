package app.nagmedar.ui.player

import android.content.pm.ActivityInfo
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.nagmedar.ui.R
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.player.navigate.PlayerNavigation
import app.nagmedar.ui.player.panel.PanelTab
import kotlin.math.abs

/** A sheet's title and items. */
internal class SheetContent(val title: String, val entries: List<MenuEntry>)

/** The screen's orientations the touch layout offers: the sensor's, or one held. */
internal enum class ScreenOrientation(@StringRes val label: Int, val value: Int) {
    Auto(R.string.nag_orientation_auto, -1),
    Landscape(R.string.nag_orientation_landscape, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE),
    Portrait(R.string.nag_orientation_portrait, ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT),
    ReverseLandscape(R.string.nag_orientation_reverse, ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE),
    ;

    companion object {
        /** The orientation the player holds ([locked]) at [value], as the sheet ticks it. */
        fun of(locked: Boolean, value: Int): ScreenOrientation? = when {
            !locked -> Auto
            value == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE -> ReverseLandscape
            value == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || value == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> Landscape
            value == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT || value == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT ||
                value == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT -> Portrait
            else -> null
        }
    }
}

/**
 * The items of [sheet], where the classic overlay had its option sheet, its tracks dialog, its
 * speed dialog, its picture sizes and its orientations: each acts on the player, opens the panel on
 * a tab or one of the app's windows over the film.
 */
@Composable
internal fun sheetContent(
    sheet: PlayerSheet,
    state: PlayerChromeState,
    source: ActivityPlayerSource,
    sheets: PlayerSheets,
    touch: TouchActions,
    navigation: PlayerNavigation,
    canRotate: Boolean,
): SheetContent = when (sheet) {
    PlayerSheet.More -> SheetContent(stringResource(R.string.nag_player_menu), moreEntries(state, source, sheets, touch, navigation, canRotate))
    PlayerSheet.Audio -> SheetContent(stringResource(R.string.nag_player_audio_track), audioEntries(state, source, touch))
    PlayerSheet.Speed -> {
        val locale = currentLocale()
        SheetContent(
            stringResource(R.string.nag_player_speed),
            playbackRates.map { rate -> MenuEntry.Item(rateLabel(rate, locale), { source.setRate(rate) }, checked = abs(state.rate - rate) < 0.01f) },
        )
    }
    PlayerSheet.Picture -> {
        val resources = LocalContext.current.resources
        val current = source.pictureSize()
        SheetContent(
            stringResource(R.string.nag_player_picture),
            pictureSizes.map { scale -> MenuEntry.Item(pictureSizeLabel(resources, scale), { source.setPictureSize(scale) }, checked = scale == current) } +
                listOf(
                    MenuEntry.Separator,
                    // the crop, the rotation, deinterlacing and the rest: the effects tab
                    MenuEntry.Item(stringResource(R.string.nag_sc_effects), { touch.openPanel(PanelTab.Effects) }, icon = NagIcons.Effects),
                ),
        )
    }
    PlayerSheet.Orientation -> {
        val current = source.orientation()
        SheetContent(
            stringResource(R.string.nag_player_orientation),
            ScreenOrientation.entries.map { orientation ->
                MenuEntry.Item(stringResource(orientation.label), { source.setOrientation(orientation) }, checked = orientation == current)
            },
        )
    }
    PlayerSheet.Chapters -> {
        val playing = state.chapterAt(state.timeMs)
        SheetContent(
            stringResource(R.string.nag_player_chapters),
            state.chapters.mapIndexed { index, chapter ->
                val name = if (chapter.name.isBlank()) {
                    stringResource(R.string.nag_player_chapter, index + 1)
                } else {
                    stringResource(R.string.nag_player_chapter_named, index + 1, chapter.name)
                }
                MenuEntry.Item(name, { source.seekTo(chapter.startMs) }, shortcut = playerClock(chapter.startMs), checked = index == playing)
            },
        )
    }
}

/** The player's menu: the ways around the film, its windows, the screen, the sharing and the copies of it. */
@Composable
private fun moreEntries(
    state: PlayerChromeState,
    source: ActivityPlayerSource,
    sheets: PlayerSheets,
    touch: TouchActions,
    navigation: PlayerNavigation,
    canRotate: Boolean,
): List<MenuEntry> {
    val resources = LocalContext.current.resources
    fun keys(id: ShortcutId) = ShortcutRegistry.chordLabel(id, resources)
    val ab = stringResource(
        when (state.abState) {
            AbState.Off -> R.string.nag_player_ab_set_a
            AbState.HasA -> R.string.nag_player_ab_set_b
            AbState.Looping -> R.string.nag_player_ab_off
        },
    )
    return listOfNotNull(
        MenuEntry.Item(stringResource(R.string.nag_sc_jump_to_time), { navigation.jumping = true }, shortcut = keys(ShortcutId.JumpToTime), icon = NagIcons.History),
        if (state.chapters.isNotEmpty()) MenuEntry.Item(stringResource(R.string.nag_player_chapters), { sheets.show(PlayerSheet.Chapters) }, icon = NagIcons.Chapters) else null,
        MenuEntry.Item(stringResource(R.string.nag_sc_bookmarks), { touch.openPanel(PanelTab.Bookmarks) }, shortcut = keys(ShortcutId.Bookmarks), icon = NagIcons.Bookmark),
        MenuEntry.Item(stringResource(R.string.nag_sc_add_bookmark), navigation::addBookmark, shortcut = keys(ShortcutId.AddBookmark), icon = NagIcons.Plus),
        MenuEntry.Item(ab, source::abRepeat, icon = NagIcons.Repeat, checked = if (state.abState == AbState.Off) null else true),
        MenuEntry.Separator,
        MenuEntry.Item(stringResource(R.string.nag_sc_sync), { touch.openPanel(PanelTab.Sync) }, shortcut = keys(ShortcutId.Synchronization), icon = NagIcons.Waveform),
        MenuEntry.Item(stringResource(R.string.nag_sc_media_info), { touch.openPanel(PanelTab.Info) }, shortcut = keys(ShortcutId.MediaInfo), icon = NagIcons.Info),
        MenuEntry.Item(stringResource(R.string.nag_sc_equalizer), { source.openWindow(ShortcutId.Equalizer) }, shortcut = keys(ShortcutId.Equalizer), icon = NagIcons.Equalizer),
        MenuEntry.Item(stringResource(R.string.nag_sc_sleep_timer), { source.openWindow(ShortcutId.SleepTimer) }, shortcut = keys(ShortcutId.SleepTimer), icon = NagIcons.Sleep),
        MenuEntry.Separator,
        if (canRotate) MenuEntry.Item(stringResource(R.string.nag_player_orientation), { sheets.show(PlayerSheet.Orientation) }, icon = NagIcons.Rotate) else null,
        MenuEntry.Item(stringResource(R.string.nag_player_lock), touch::lock, icon = NagIcons.Lock),
        MenuEntry.Item(stringResource(R.string.nag_player_audio_only), source::audioOnly, icon = NagIcons.Headphones),
        if (state.canPictureInPicture) MenuEntry.Item(stringResource(R.string.nag_player_pip), source::pictureInPicture, icon = NagIcons.PictureInPicture) else null,
        if (state.canCast) MenuEntry.Item(stringResource(R.string.nag_sc_cast), source::cast, shortcut = keys(ShortcutId.Cast), icon = NagIcons.Cast) else null,
        MenuEntry.Separator,
        MenuEntry.Item(stringResource(R.string.nag_sc_snapshot), source::takeSnapshot, shortcut = keys(ShortcutId.Snapshot), icon = NagIcons.Screenshot),
        MenuEntry.Item(
            stringResource(if (state.recording) R.string.nag_rec_stop else R.string.nag_rec_start),
            source::toggleRecording,
            shortcut = keys(ShortcutId.Record),
            icon = NagIcons.Record,
        ),
        MenuEntry.Item(stringResource(R.string.nag_player_share), source::share, icon = NagIcons.Share),
        MenuEntry.Item(stringResource(R.string.nag_conv_menu), source::convertSave, shortcut = keys(ShortcutId.ConvertSave), icon = NagIcons.Convert),
        MenuEntry.Item(stringResource(R.string.nag_hls_start_menu), source::streamOnNetwork, shortcut = keys(ShortcutId.StreamHls), icon = NagIcons.Streams),
        MenuEntry.Separator,
        MenuEntry.Item(stringResource(R.string.nag_guide_title), { source.openWindow(ShortcutId.UserGuide) }, shortcut = keys(ShortcutId.UserGuide), icon = NagIcons.Info),
        MenuEntry.Item(stringResource(R.string.nag_nav_settings), { source.openWindow(ShortcutId.Settings) }, shortcut = keys(ShortcutId.Settings), icon = NagIcons.Settings),
    )
}

/** The audio tracks, then the way to the delays, the subtitles and the visualisers (S-611). */
@Composable
private fun audioEntries(state: PlayerChromeState, source: ActivityPlayerSource, touch: TouchActions): List<MenuEntry> {
    val tracks = if (state.audioTracks.isEmpty()) {
        listOf(MenuEntry.Item(stringResource(R.string.nag_player_no_audio), {}, enabled = false))
    } else {
        state.audioTracks.map { track -> MenuEntry.Item(track.name, { source.setAudioTrack(track.id) }, checked = track.id == state.audioTrack) }
    }
    return tracks + listOf(
        MenuEntry.Separator,
        MenuEntry.Item(stringResource(R.string.nag_player_subtitles), { touch.openPanel(PanelTab.Subtitles) }, icon = NagIcons.Subtitle),
        MenuEntry.Item(stringResource(R.string.nag_sc_sync), { touch.openPanel(PanelTab.Sync) }, icon = NagIcons.Waveform),
    ) + visualizerEntries(state, source)
}
