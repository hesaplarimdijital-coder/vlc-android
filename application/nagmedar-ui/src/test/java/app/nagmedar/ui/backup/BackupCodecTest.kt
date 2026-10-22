package app.nagmedar.ui.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.data.LatencyProfileEntry
import app.nagmedar.data.LyricsEntry
import app.nagmedar.data.MediaSettings
import app.nagmedar.data.SmartPlaylistEntry
import app.nagmedar.data.TrackStatsEntry
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.music.PlaylistEntry
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.tools.KEY_CURRENT_SETTINGS_VERSION
import org.videolan.tools.KEY_OPEN_SUBTITLES_USER
import org.videolan.tools.NAGMEDAR_SAF_FOLDERS
import org.videolan.tools.NAGMEDAR_WINDOW_BOUNDS
import org.videolan.tools.Settings
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** S-1502: a backup as a file: what goes out comes back, what is not one is said so, what it leaves out. */
@RunWith(AndroidJUnit4::class)
class BackupCodecTest {

    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()) + ByteArray(20) { 1 }
    private val coverName = "0123456789abcdef0123456789abcdef01234567.user"

    private val full = Backup(
        createdAt = 1_760_300_000_000L,
        appVersion = "0.2.0",
        settingsVersion = 17,
        settings = listOf(
            SettingValue("nagmedar_accent", "Amber"),
            SettingValue("nagmedar_crossfade_ms", 4000),
            SettingValue("nagmedar_last_seen", 1_760_000_000_000L),
            SettingValue("nagmedar_pane_width_music", 300.5f),
            SettingValue("nagmedar_wheel_seeks", true),
            SettingValue("nagmedar_sources", setOf("radio", "podcasts")),
        ),
        equalizers = """{"equalizers":[],"currentEqualizerName":"Flat","defaultStates":[]}""",
        shortcuts = mapOf("Stop" to "Q", "Mute" to ""),
        profiles = listOf("""{"schema":1,"id":"sony-bravia-8","name":"Sony Bravia 8"}"""),
        selectedProfile = "sony-bravia-8",
        playlists = listOf(
            BackupPlaylist(
                "Yol",
                listOf(
                    PlaylistEntry("/storage/emulated/0/Music/Cemre Kaya/Gece Yolcusu/01.flac", "Gece Yolcusu", "Cemre Kaya", "Gece Yolcusu", 254_000),
                    PlaylistEntry("https://radyo.example/akis.mp3"),
                ),
            ),
            BackupPlaylist("Boş liste", emptyList()),
        ),
        smartLists = listOf(SmartPlaylistEntry(name = "Sevilenler", rules = "rating|atLeast|4", matchAll = true, sort = "rating:true", limitCount = 50, createdAt = 1L, updatedAt = 2L)),
        stats = listOf(TrackStatsEntry("file:///m/01.flac", rating = 5, playCount = 12, skipCount = 1, firstPlayedAt = 10L, lastPlayedAt = 20L, updatedAt = 20L)),
        lyrics = listOf(LyricsEntry("cemre kaya|gece yolcusu|gece yolcusu|254", "user", synced = true, text = "[00:01.00]Gece", offsetMs = -300L, fetchedAt = 5L)),
        fileSettings = listOf(MediaSettings("file:///film.mkv", audioDelayMs = -120L, subtitleTrack = "3", playbackRate = 1.25f, updatedAt = 9L)),
        covers = listOf(BackupCover(coverName, png)),
        // S-613: a measured profile, one set by hand, and the speaker's reference; a Bluetooth address stays out of a backup
        latency = listOf(
            LatencyProfileEntry(8, "Galaxy Buds2 Pro", delayMs = 180, measured = true, spreadMs = 4, updatedAt = 11L),
            LatencyProfileEntry(29, "Denon AVR-X2800H", delayMs = 90, updatedAt = 12L),
            LatencyProfileEntry(2, "SM-X936B", measured = true, spreadMs = 2, calibrationMs = 12, updatedAt = 13L),
        ),
    )

    private fun bytes(backup: Backup) = ByteArrayOutputStream().also { BackupCodec.write(backup, it) }.toByteArray()

    private fun read(bytes: ByteArray) = BackupCodec.read(ByteArrayInputStream(bytes))

    private fun zip(vararg files: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private fun reason(bytes: ByteArray): BackupException.Reason? = try {
        read(bytes)
        null
    } catch (e: BackupException) {
        e.reason
    }

    @Test
    fun aBackupGoesOutAndComesBack() {
        val back = read(bytes(full))
        assertEquals(full.copy(profiles = null, covers = null), back.copy(profiles = null, covers = null))
        // a profile comes back as its JSON
        assertEquals("sony-bravia-8", JSONObject(back.profiles!!.single()).getString("id"))
        assertEquals(coverName, back.covers!!.single().name)
        assertArrayEquals(png, back.covers!!.single().bytes)
        assertEquals(BackupPart.entries, back.parts)
        assertEquals(2, back.playlistTracks)
        // a profile with an address goes out without it
        val paired = read(bytes(full.copy(latency = listOf(LatencyProfileEntry(8, "Galaxy Buds2 Pro", "AA:BB:CC:DD:EE:FF", delayMs = 180)))))
        assertEquals("", paired.latency!!.single().deviceAddress)
        assertEquals(180, paired.latency!!.single().delayMs)
    }

    @Test
    fun theManifestAloneIsABackupWithoutCovers() {
        val back = read(BackupCodec.manifest(full).toString(2).toByteArray())
        assertEquals(full.settings, back.settings)
        assertEquals(full.playlists, back.playlists)
        assertEquals(emptyList<BackupCover>(), back.covers)
        assertFalse(BackupPart.Covers in back.parts)
    }

    @Test
    fun whatIsNotABackupIsSaidSo() {
        assertEquals(BackupException.Reason.NotABackup, reason("hello".toByteArray()))
        assertEquals(BackupException.Reason.NotABackup, reason(ByteArray(0)))
        // the shortcuts' file (S-402) is not a backup
        assertEquals(BackupException.Reason.NotABackup, reason("""{"type":"nagmedar-shortcuts","version":1,"bindings":{}}""".toByteArray()))
        assertEquals(BackupException.Reason.NotABackup, reason(zip("readme.txt" to "hi".toByteArray())))
        assertEquals(BackupException.Reason.Newer, reason("""{"type":"nagmedar-backup","format":2}""".toByteArray()))
        assertEquals(BackupException.Reason.Damaged, reason("""{"type":"nagmedar-backup","format":1,""".toByteArray()))
        // a file cut short
        assertEquals(BackupException.Reason.Damaged, reason(bytes(full).copyOf(40)))
    }

    @Test
    fun unknownKeysAndBadEntriesArePassedOver() {
        val text = """
            {"type":"nagmedar-backup","format":1,"created_at":5,"future_part":[1,2],
             "settings":[{"key":"a","type":"string","value":"x"},{"key":"b","type":"colour","value":"#fff"},
                         {"type":"int","value":3},{"key":"c","type":"int","value":null}],
             "playlists":[{"name":"","entries":[]},{"name":"Yol","entries":[{"title":"no location"},{"location":"/m/01.flac"}]}],
             "stats":[{"track":"k","rating":9,"plays":-2}]}
        """.trimIndent()
        val back = read(text.toByteArray())
        assertEquals(listOf(SettingValue("a", "x")), back.settings)
        assertEquals(listOf(BackupPlaylist("Yol", listOf(PlaylistEntry("/m/01.flac")))), back.playlists)
        assertEquals(TrackStatsEntry("k", rating = 5), back.stats!!.single())
        assertNull(back.lyrics)
        assertEquals(listOf(BackupPart.Settings, BackupPart.Playlists, BackupPart.Stats), back.parts)
    }

    @Test
    fun onlyTheChosenParts() {
        val some = full.only(setOf(BackupPart.Playlists, BackupPart.Settings))
        assertEquals(listOf(BackupPart.Settings, BackupPart.Playlists), some.parts)
        // the classic equalisers go with the settings, the chosen profile with the profiles
        assertEquals(full.equalizers, some.equalizers)
        assertNull(some.selectedProfile)
        assertNull(some.shortcuts)
        assertNull(some.covers)
    }

    @Test
    fun onlyChosenCoversAreTakenFromTheFile() {
        val listed = full.copy(covers = listOf(BackupCover(coverName, png), BackupCover("../../evil.user", png)))
        val file = zip(
            BackupCodec.MANIFEST to BackupCodec.manifest(listed).toString().toByteArray(),
            "covers/$coverName" to png,
            "covers/../../evil.user" to png,
            "covers/abc.user" to png,
        )
        assertEquals(listOf(coverName), read(file).covers!!.map { it.name })
    }

    @Test
    fun theSettingsLeaveOutThisDevicesAndAnAccountsKeys() {
        val all = mapOf(
            "nagmedar_theme" to "Dark",
            "nagmedar_crossfade_ms" to 4000,
            "nagmedar_sources" to setOf("a", "b"),
            // their own parts
            NagmedarPreferences.KEY_SHORTCUTS to "Stop=Q",
            "nagmedar_tvlab_profile" to "lg-oled-b4-usb",
            // this device's
            NAGMEDAR_SAF_FOLDERS to setOf("content://tree/x"),
            NAGMEDAR_WINDOW_BOUNDS to "0,0,800,600",
            // an account's
            KEY_OPEN_SUBTITLES_USER to """{"token":"secret"}""",
            // the backup's own
            KEY_CURRENT_SETTINGS_VERSION to 17,
            BackupSettings.KEY_LAST_BACKUP to 5L,
            // not a preference's type
            "odd" to listOf(1, 2),
        )
        assertEquals(listOf("nagmedar_crossfade_ms", "nagmedar_sources", "nagmedar_theme"), BackupSettings.collect(all).map { it.key })
        // VLC's own export leaves out the last queue and remote access's keys: so does a backup
        assertTrue(BackupSettings.leftOut().containsAll(Settings.getRestoreBlacklist().toList()))
    }
}
