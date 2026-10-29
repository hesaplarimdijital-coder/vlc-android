package app.nagmedar.ui.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.playback.RadioMetadata
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.TestShell
import app.nagmedar.ui.radio.FakeDirectory
import app.nagmedar.ui.radio.FakeLibrary
import app.nagmedar.ui.radio.RadioController
import app.nagmedar.ui.radio.RadioNow
import app.nagmedar.ui.radio.RadioPage
import app.nagmedar.ui.radio.RadioPlayer
import app.nagmedar.ui.radio.RadioSamples
import app.nagmedar.ui.radio.RadioTab
import app.nagmedar.ui.radio.RecordingSink
import app.nagmedar.ui.radio.inlineClicks
import app.nagmedar.ui.radio.record.FakeControl
import app.nagmedar.ui.radio.record.FakeEnvironment
import app.nagmedar.ui.radio.record.FakeRecordingsData
import app.nagmedar.ui.radio.record.FakeTrackPlayer
import app.nagmedar.ui.radio.record.FolderState
import app.nagmedar.ui.radio.record.LimitAction
import app.nagmedar.ui.radio.record.LiveRecording
import app.nagmedar.ui.radio.record.Phase
import app.nagmedar.ui.radio.record.RadioRecordingSection
import app.nagmedar.ui.radio.record.RecordedTrack
import app.nagmedar.ui.radio.record.RecordingRequest
import app.nagmedar.ui.radio.record.RecordingSamples
import app.nagmedar.ui.radio.record.RecordingSchedule
import app.nagmedar.ui.radio.record.RecordingSettings
import app.nagmedar.ui.radio.record.RecordingsController
import app.nagmedar.ui.radio.record.Repeat
import app.nagmedar.ui.radio.record.ScheduleDraft
import app.nagmedar.ui.radio.record.ScheduleSurface
import app.nagmedar.ui.radio.record.SessionState
import app.nagmedar.ui.radio.record.SpaceIssue
import app.nagmedar.ui.radio.record.StorageLimits
import app.nagmedar.ui.radio.record.TrackTag
import app.nagmedar.ui.radio.record.asStation
import app.nagmedar.ui.shell.Destination
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

/**
 * S-207, for approval: the Kayıtlar (Recordings) tab of the radio screen in DeX, on a tablet and
 * on a phone, in both themes and in English: a recording going on, what was recorded (partial
 * tracks, a file that is gone), the scheduled recordings, the warnings about room, the folder and
 * the alarm permission, the empty tab, and the window that schedules a recording; and
 * Settings › Radio.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordingsScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.of("Europe/Istanbul")
    private var previous: TimeZone? = null

    @Before
    fun clock() {
        previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
    }

    @After
    fun restore() {
        previous?.let { TimeZone.setDefault(it) }
    }

    private fun capture(name: String, theme: ThemeMode, input: InputMode, content: @Composable () -> Unit) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) { content() }
            }
        }
        compose.waitForIdle()
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    // 14 October 2026, 09:20 in Istanbul
    private val at = ZonedDateTime.of(2026, 10, 14, 9, 20, 0, 0, zone).toInstant().toEpochMilli()
    private val minute = 60_000L
    private val sabah = RecordingSamples.sabah
    private val caz = RecordingSamples.caz
    private val haber = RecordingSamples.haber

    private fun clockAt(hour: Int, minute: Int, daysAgo: Long = 0) =
        ZonedDateTime.of(2026, 10, 14, hour, minute, 0, 0, zone).minusDays(daysAgo).toInstant().toEpochMilli()

    private val morning = clockAt(7, 30)
    private val night = clockAt(22, 0, daysAgo = 1)
    private val noon = clockAt(18, 0, daysAgo = 3)

    private val tracks: List<RecordedTrack> = listOf(
        RecordingSamples.track(1, sabah, "morning", morning, 0, "Sezen Aksu", "Hadi Bakalım", minutes = 2, complete = false),
        RecordingSamples.track(2, sabah, "morning", morning, 2, "Tarkan", "Hepsi Senin Mi", minutes = 4),
        RecordingSamples.track(3, sabah, "morning", morning, 6, "Barış Manço", "Gesi Bağları", minutes = 3),
        RecordingSamples.track(4, sabah, "morning", morning, 9, "Ajda Pekkan", "Bambaşka Biri", minutes = 5, complete = false),
        RecordingSamples.track(5, caz, "night", night, 0, "Miles Davis", "So What", minutes = 9),
        RecordingSamples.track(6, caz, "night", night, 9, "Bill Evans", "Waltz for Debby", minutes = 7),
        RecordingSamples.track(7, caz, "night", night, 16, "Nina Simone", "Feeling Good", minutes = 3, format = "aac"),
        RecordingSamples.track(8, haber, "noon", noon, 0, null, null, minutes = 58, name = "Haber Hattı 11-10-2026 18-00", sizeMb = 52),
    )

    private val schedules: List<RecordingSchedule> = listOf(
        RecordingSchedule.of(sabah, java.time.LocalDate.of(2026, 10, 14), 7, 30, 90, Repeat.Weekdays, zone, at, id = 1),
        RecordingSchedule.of(caz, java.time.LocalDate.of(2026, 10, 14), 22, 0, 120, Repeat.Weekly, zone, at, id = 2).copy(weekday = 5),
        RecordingSchedule.of(haber, java.time.LocalDate.of(2026, 10, 15), 18, 0, 60, Repeat.Once, zone, at, id = 3),
        RecordingSchedule.of(sabah, java.time.LocalDate.of(2026, 10, 14), 6, 0, 30, Repeat.Daily, zone, at, id = 4, enabled = false),
    )

    private fun live(start: Int = 23, saved: Int = 5, warning: SpaceIssue? = null, many: Boolean = false): List<LiveRecording> {
        val first = LiveRecording(
            RecordingRequest("live-1", sabah, endAt = at + 40 * minute, scheduleId = 1),
            SessionState(
                phase = Phase.Recording, startedAt = at - start * minute, endAt = at + 40 * minute,
                track = TrackTag("Tarkan", "Hepsi Senin Mi"), trackStartedAt = at - 2 * minute,
                tracksSaved = saved, bytes = 38L * 1024 * 1024, splits = true, warning = warning,
            ),
        )
        if (!many) return listOf(first)
        val second = LiveRecording(
            RecordingRequest("live-2", caz),
            SessionState(phase = Phase.Reconnecting, startedAt = at - 6 * minute, splits = true, tracksSaved = 1, bytes = 7L * 1024 * 1024),
        )
        return listOf(first, second)
    }

    private fun air(station: app.nagmedar.ui.radio.Station, nowPlaying: String?) = RadioNow(station, RadioMetadata.display(station.name, nowPlaying), playing = true)

    private class Scene(
        val tracks: List<RecordedTrack> = emptyList(),
        val schedules: List<RecordingSchedule> = emptyList(),
        val live: List<LiveRecording> = emptyList(),
        val gone: Set<String> = emptySet(),
        val free: Long = 38L * StorageLimits.GB,
        val folder: FolderState = FolderState.Default,
        val exact: Boolean = true,
        val limits: StorageLimits = StorageLimits(),
    )

    @Composable
    private fun Recordings(scene: Scene, onAir: RadioNow? = null, expandAll: Boolean = true, overlay: (@Composable (RecordingsController, List<app.nagmedar.ui.radio.Station>) -> Unit)? = null) {
        val scope = rememberCoroutineScope()
        val stations = listOf(RadioSamples.jazz, RadioSamples.news, RadioSamples.classical)
        val recordings = remember {
            val control = FakeControl().apply { live.value = scene.live }
            val environment = FakeEnvironment().apply { free = scene.free; place = scene.folder; exact = scene.exact }
            RecordingsController(
                FakeRecordingsData(scene.tracks, scene.schedules, scene.gone), control, { RecordingSettings(limits = scene.limits) },
                environment, FakeTrackPlayer(), scope, { at }, { zone }, Dispatchers.Unconfined,
            )
        }
        val radio = remember {
            val directory = FakeDirectory().apply { onSearch = { RadioSamples.all } }
            val library = FakeLibrary().also { library -> runBlocking { stations.forEach { library.favourite(it, true) } } }
            RadioController(directory, library, RadioPlayer(directory, library, RecordingSink(), clickScope = inlineClicks()), scope, debounceMs = 0, playingNow = flowOf(onAir))
                .also { it.select(RadioTab.Recordings) }
        }
        if (expandAll) {
            LaunchedEffect(recordings) {
                val groups = snapshotFlow { recordings.groups }.first { it.isNotEmpty() }
                groups.forEach { group ->
                    if (RecordingsController.stationKey(group) !in recordings.open) recordings.toggle(RecordingsController.stationKey(group))
                    group.sessions.forEach { session ->
                        if (RecordingsController.sessionKey(session) !in recordings.open) recordings.toggle(RecordingsController.sessionKey(session))
                    }
                }
            }
        }
        RadioPage(radio, now = { at }, recordings = recordings, recordingsLive = false)
        overlay?.invoke(recordings, stations)
    }

    private fun shot(name: String, width: WidthClass, theme: ThemeMode, input: InputMode, scene: Scene, onAir: RadioNow? = null, expandAll: Boolean = true) {
        capture(name, theme, input) {
            TestShell(width, startDestination = Destination.Radio) { _, _ -> Recordings(scene, onAir, expandAll) }
        }
    }

    private fun editor(name: String, width: WidthClass, theme: ThemeMode, input: InputMode, draft: ScheduleDraft, scene: Scene) {
        capture(name, theme, input) {
            TestShell(width, startDestination = Destination.Radio) { _, _ ->
                Recordings(scene, expandAll = false) { controller, stations ->
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).padding(8.dp)) {
                        app.nagmedar.ui.components.DialogFrame({}) { ScheduleSurface(controller, draft, stations, zone) }
                    }
                }
            }
        }
    }

    private companion object {
        const val DEX = "w1440dp-h900dp-mdpi"
        const val TABLET = "w1000dp-h800dp-mdpi"
        const val PHONE = "w411dp-h891dp-xhdpi"
    }

    // --- a recording going on

    @Test @Config(qualifiers = "tr-$DEX-keysexposed-qwerty")
    fun dexRecording() = shot(
        "recordings-dex-dark-recording", WidthClass.Large, ThemeMode.Dark, InputMode.Pointer,
        Scene(tracks, schedules, live(), gone = setOf(tracks[6].fileUri)), air(sabah.asStation(), "Tarkan - Hepsi Senin Mi"),
    )

    @Test @Config(qualifiers = "tr-$TABLET")
    fun tabletRecordingTwoStations() = shot(
        "recordings-tablet-light-recording", WidthClass.Expanded, ThemeMode.Light, InputMode.Touch,
        Scene(tracks, schedules, live(many = true)), expandAll = false,
    )

    @Test @Config(qualifiers = "tr-$PHONE")
    fun phoneRecording() = shot(
        "recordings-phone-dark-recording", WidthClass.Compact, ThemeMode.Dark, InputMode.Touch,
        Scene(tracks.take(4), emptyList(), live()), air(sabah.asStation(), "Tarkan - Hepsi Senin Mi"),
    )

    @Test @Config(qualifiers = "en-$PHONE")
    fun phoneRecordingEnglish() = shot(
        "recordings-phone-light-recording-en", WidthClass.Compact, ThemeMode.Light, InputMode.Touch,
        Scene(tracks.take(4), schedules.take(2), live()), expandAll = false,
    )

    // --- what was recorded

    @Test @Config(qualifiers = "tr-$DEX-keysexposed-qwerty")
    fun dexDone() = shot(
        "recordings-dex-light-done", WidthClass.Large, ThemeMode.Light, InputMode.Pointer,
        Scene(tracks, gone = setOf(tracks[6].fileUri)),
    )

    @Test @Config(qualifiers = "en-$TABLET")
    fun tabletDoneEnglish() = shot(
        "recordings-tablet-dark-done-en", WidthClass.Expanded, ThemeMode.Dark, InputMode.Touch,
        Scene(tracks, limits = StorageLimits(maxTotalBytes = 2L * StorageLimits.GB)),
    )

    @Test @Config(qualifiers = "tr-$PHONE")
    fun phoneDone() = shot(
        "recordings-phone-light-done", WidthClass.Compact, ThemeMode.Light, InputMode.Touch,
        Scene(tracks),
    )

    // --- scheduled

    @Test @Config(qualifiers = "tr-$TABLET")
    fun tabletScheduled() = shot(
        "recordings-tablet-light-scheduled", WidthClass.Expanded, ThemeMode.Light, InputMode.Touch,
        Scene(emptyList(), schedules + RecordingSchedule.of(caz, java.time.LocalDate.of(2026, 10, 14), 22, 30, 60, Repeat.Daily, zone, at, id = 5)),
    )

    @Test @Config(qualifiers = "tr-$PHONE")
    fun phoneScheduled() = shot(
        "recordings-phone-dark-scheduled", WidthClass.Compact, ThemeMode.Dark, InputMode.Touch,
        Scene(emptyList(), schedules.take(3), exact = false),
    )

    // --- room

    @Test @Config(qualifiers = "tr-$DEX-keysexposed-qwerty")
    fun dexNearLimit() = shot(
        "recordings-dex-dark-limit", WidthClass.Large, ThemeMode.Dark, InputMode.Pointer,
        Scene(tracks, emptyList(), live(warning = SpaceIssue.LimitReached), limits = StorageLimits(maxTotalBytes = 150L * StorageLimits.MB)),
        air(sabah.asStation(), "Tarkan - Hepsi Senin Mi"),
    )

    @Test @Config(qualifiers = "tr-$PHONE")
    fun phoneLowSpace() = shot(
        "recordings-phone-light-limit", WidthClass.Compact, ThemeMode.Light, InputMode.Touch,
        Scene(tracks.take(4), free = 700L * StorageLimits.MB, limits = StorageLimits(action = LimitAction.PruneOldest)),
    )

    @Test @Config(qualifiers = "en-$TABLET")
    fun tabletAtLimitEnglish() = shot(
        "recordings-tablet-dark-limit-en", WidthClass.Expanded, ThemeMode.Dark, InputMode.Touch,
        Scene(tracks, free = 300L * StorageLimits.MB, limits = StorageLimits()),
    )

    // --- nothing, or nowhere to write

    @Test @Config(qualifiers = "tr-$PHONE")
    fun phoneEmpty() = shot("recordings-phone-dark-empty", WidthClass.Compact, ThemeMode.Dark, InputMode.Touch, Scene())

    @Test @Config(qualifiers = "en-$DEX-keysexposed-qwerty")
    fun dexEmptyEnglish() = shot("recordings-dex-light-empty-en", WidthClass.Large, ThemeMode.Light, InputMode.Pointer, Scene())

    @Test @Config(qualifiers = "tr-$TABLET")
    fun tabletFolderMissing() = shot(
        "recordings-tablet-light-folder-missing", WidthClass.Expanded, ThemeMode.Light, InputMode.Touch,
        Scene(tracks.take(4), folder = FolderState.Missing("SD kart/Kayıtlar")),
    )

    @Test @Config(qualifiers = "tr-$PHONE")
    fun phoneFolderDenied() = shot(
        "recordings-phone-dark-folder-denied", WidthClass.Compact, ThemeMode.Dark, InputMode.Touch,
        Scene(emptyList(), schedules.take(1), folder = FolderState.NoPermission("Müzik/Radyo kayıtları")),
    )

    // --- the window that schedules

    private val newDraft = ScheduleDraft(station = caz, hour = 22, minute = 0, durationMinutes = 90, repeat = Repeat.Weekly, weekday = 5)

    @Test @Config(qualifiers = "tr-$TABLET")
    fun tabletEditor() = editor("recordings-tablet-dark-editor", WidthClass.Expanded, ThemeMode.Dark, InputMode.Touch, newDraft, Scene(tracks, schedules.take(2)))

    @Test @Config(qualifiers = "en-$PHONE")
    fun phoneEditorEnglish() = editor("recordings-phone-light-editor-en", WidthClass.Compact, ThemeMode.Light, InputMode.Touch, ScheduleDraft(), Scene(tracks, schedules.take(2)))

    @Test @Config(qualifiers = "tr-$DEX-keysexposed-qwerty")
    fun dexEditorMeetingAnother() = editor(
        "recordings-dex-light-editor-conflict", WidthClass.Large, ThemeMode.Light, InputMode.Pointer,
        newDraft.copy(station = haber, hour = 22, minute = 30, repeat = Repeat.Daily), Scene(tracks, schedules.take(2)),
    )

    // --- Settings › Radio

    private fun settings(name: String, theme: ThemeMode, input: InputMode, chosen: Boolean) {
        val prefs = NagmedarPreferences(compose.activity.getSharedPreferences("radio-recording-shot-$name", Context.MODE_PRIVATE))
        if (chosen) {
            prefs.updateRadioRecordingFolder("content://com.android.externalstorage.documents/tree/1234-ABCD%3ARadyo%20Kay%C4%B1tlar%C4%B1")
            prefs.updateRadioRecording(prefs.radioRecording.copy(keepShort = true, padBeforeSeconds = 2, padAfterSeconds = 3, limits = StorageLimits(maxTotalBytes = 5L * StorageLimits.GB, action = LimitAction.PruneOldest)))
        }
        capture(name, theme, input) { Box(Modifier.fillMaxSize().padding(top = 16.dp)) { RadioRecordingSection(prefs) } }
    }

    @Test @Config(qualifiers = "tr-w1280dp-h900dp-mdpi-keysexposed-qwerty")
    fun settingsDex() = settings("settings-radio-recording-dex-dark", ThemeMode.Dark, InputMode.Pointer, chosen = true)

    @Test @Config(qualifiers = "tr-w800dp-h900dp-mdpi")
    fun settingsTablet() = settings("settings-radio-recording-tablet-light", ThemeMode.Light, InputMode.Touch, chosen = false)

    @Test @Config(qualifiers = "tr-w390dp-h1100dp-hdpi")
    fun settingsPhone() = settings("settings-radio-recording-phone-dark", ThemeMode.Dark, InputMode.Touch, chosen = false)

    @Test @Config(qualifiers = "en-w800dp-h900dp-mdpi")
    fun settingsTabletEnglish() = settings("settings-radio-recording-tablet-light-en", ThemeMode.Light, InputMode.Touch, chosen = true)
}
