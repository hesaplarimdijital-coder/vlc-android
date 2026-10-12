package app.nagmedar.ui.backup

import app.nagmedar.data.LyricsEntry
import app.nagmedar.data.MediaSettings
import app.nagmedar.data.SmartPlaylistEntry
import app.nagmedar.data.TrackStatsEntry
import app.nagmedar.ui.covers.CoverStore
import app.nagmedar.ui.music.PlaylistEntry
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** S-1502: the parts of a backup, in the order they are shown; each is restored on its own. */
enum class BackupPart { Settings, Shortcuts, Profiles, Playlists, SmartLists, Stats, Lyrics, FileSettings, Covers }

/** A preference as kept: a Boolean, Int, Long, Float, String or a set of strings. */
data class SettingValue(val key: String, val value: Any)

/** A playlist as kept: its name and its lines, as a playlist file has them (S-804). */
data class BackupPlaylist(val name: String, val entries: List<PlaylistEntry>)

/** A cover chosen by hand (S-1006): its file's name in the covers' folder, which its album's key gives, and its bytes. */
class BackupCover(val name: String, val bytes: ByteArray)

/**
 * S-1502: what a backup holds. A part the backup was made without is null; an empty one is a
 * part with nothing in it. [settingsVersion] is VLC's settings migration's (VersionMigration) at
 * the time, so that older settings are brought up to date on the way in.
 */
data class Backup(
    val createdAt: Long,
    /** The version of Nağmedar that made it. */
    val appVersion: String,
    val settingsVersion: Int = 0,
    val settings: List<SettingValue>? = null,
    /** The classic interface's equaliser presets, as VLC exports them (JSON). */
    val equalizers: String? = null,
    /** The user's keys over the shipped shortcuts (S-402): a shortcut's name to its keys' text, "" for none. */
    val shortcuts: Map<String, String>? = null,
    /** TV Lab's profiles as the user saved them (S-1203), each as its JSON file. */
    val profiles: List<String>? = null,
    val selectedProfile: String? = null,
    val playlists: List<BackupPlaylist>? = null,
    val smartLists: List<SmartPlaylistEntry>? = null,
    val stats: List<TrackStatsEntry>? = null,
    /** The lyrics written by the user and those given a delay (S-1003, S-1004); downloaded ones come again by themselves. */
    val lyrics: List<LyricsEntry>? = null,
    val fileSettings: List<MediaSettings>? = null,
    val covers: List<BackupCover>? = null,
) {
    /** How many things [part] holds: settings, shortcuts changed, profiles, lists, tracks, files, covers. */
    fun count(part: BackupPart): Int = when (part) {
        BackupPart.Settings -> settings?.size ?: 0
        BackupPart.Shortcuts -> shortcuts?.size ?: 0
        BackupPart.Profiles -> profiles?.size ?: 0
        BackupPart.Playlists -> playlists?.size ?: 0
        BackupPart.SmartLists -> smartLists?.size ?: 0
        BackupPart.Stats -> stats?.size ?: 0
        BackupPart.Lyrics -> lyrics?.size ?: 0
        BackupPart.FileSettings -> fileSettings?.size ?: 0
        BackupPart.Covers -> covers?.size ?: 0
    }

    /** The parts with something in them, in order. */
    val parts: List<BackupPart> get() = BackupPart.entries.filter { count(it) > 0 }

    /** The tracks of all its playlists. */
    val playlistTracks: Int get() = playlists?.sumOf { it.entries.size } ?: 0

    /** Only [parts] of it: what is restored when the others are left out. */
    fun only(parts: Set<BackupPart>): Backup = copy(
        settings = settings.takeIf { BackupPart.Settings in parts },
        equalizers = equalizers.takeIf { BackupPart.Settings in parts },
        shortcuts = shortcuts.takeIf { BackupPart.Shortcuts in parts },
        profiles = profiles.takeIf { BackupPart.Profiles in parts },
        selectedProfile = selectedProfile.takeIf { BackupPart.Profiles in parts },
        playlists = playlists.takeIf { BackupPart.Playlists in parts },
        smartLists = smartLists.takeIf { BackupPart.SmartLists in parts },
        stats = stats.takeIf { BackupPart.Stats in parts },
        lyrics = lyrics.takeIf { BackupPart.Lyrics in parts },
        fileSettings = fileSettings.takeIf { BackupPart.FileSettings in parts },
        covers = covers.takeIf { BackupPart.Covers in parts },
    )
}

/** Why a file could not be read as a backup. */
class BackupException(val reason: Reason, cause: Throwable? = null) : IOException(reason.name, cause) {
    enum class Reason {
        /** Not a Nağmedar backup at all. */
        NotABackup,

        /** Made by a newer Nağmedar, whose backups this one cannot read. */
        Newer,

        /** A backup, but cut short or garbled. */
        Damaged,
    }
}

/**
 * S-1502: a backup as a file: a ZIP with the manifest, "nagmedar-backup.json" (format 1), and the
 * covers chosen by hand under "covers/". The manifest is the backup's readable record:
 *
 *   {"type":"nagmedar-backup","format":1,"app_version":"0.2.0","created_at":1760300000000,
 *    "settings_version":12,"settings":[{"key":"nagmedar_theme","type":"string","value":"Dark"}],
 *    "equalizers":"…","shortcuts":{"Stop":"Q","Mute":""},"profiles":[{…}],"selected_profile":"…",
 *    "playlists":[{"name":"Yol","entries":[{"location":"/storage/…/01.flac","title":"…","duration_ms":245000}]}],
 *    "smart_lists":[…],"stats":[…],"lyrics":[…],"file_settings":[…],"covers":["<sha1>.user"]}
 *
 * Unknown keys are left; an entry that does not read is passed over, the rest still comes back.
 * The manifest alone (the ZIP's JSON, taken out of it) is read as a backup without covers.
 */
object BackupCodec {
    const val FORMAT = 1
    const val TYPE = "nagmedar-backup"
    const val MANIFEST = "nagmedar-backup.json"
    private const val COVERS = "covers/"

    /** How large a backup may be read: its manifest, each cover, all of it. */
    private const val MAX_MANIFEST = 64 * 1024 * 1024
    private const val MAX_COVER = 16 * 1024 * 1024
    private const val MAX_TOTAL = 512L * 1024 * 1024

    /** How a ZIP file begins: "PK", 3, 4. */
    private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    /** [backup] written to [out] as a ZIP; [out] stays open. */
    fun write(backup: Backup, out: OutputStream) {
        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry(MANIFEST))
        zip.write(manifest(backup).toString(1).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        backup.covers?.forEach { cover ->
            zip.putNextEntry(ZipEntry(COVERS + cover.name))
            zip.write(cover.bytes)
            zip.closeEntry()
        }
        zip.finish()
        zip.flush()
    }

    /** The backup in [input]: a backup's ZIP, or its manifest alone. */
    fun read(input: InputStream): Backup {
        val stream = BufferedInputStream(input)
        stream.mark(ZIP_SIGNATURE.size)
        val head = ByteArray(ZIP_SIGNATURE.size)
        var got = 0
        while (got < head.size) {
            val count = stream.read(head, got, head.size - got)
            if (count < 0) break
            got += count
        }
        stream.reset()
        val zipped = got == head.size && head.contentEquals(ZIP_SIGNATURE)
        if (!zipped) return parse(String(stream.readAtMost(MAX_MANIFEST), Charsets.UTF_8), emptyMap())
        var manifest: String? = null
        val covers = LinkedHashMap<String, ByteArray>()
        var total = 0L
        try {
            val zip = ZipInputStream(stream)
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                when {
                    name == MANIFEST -> manifest = String(zip.readAtMost(MAX_MANIFEST).also { total += it.size }, Charsets.UTF_8)
                    name.startsWith(COVERS) && CoverStore.CHOSEN_NAME.matches(name.removePrefix(COVERS)) ->
                        covers[name.removePrefix(COVERS)] = zip.readAtMost(MAX_COVER).also { total += it.size }
                }
                if (total > MAX_TOTAL) throw BackupException(BackupException.Reason.Damaged)
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: IOException) {
            // a ZIP cut short or garbled
            throw BackupException(BackupException.Reason.Damaged, e)
        }
        return parse(manifest ?: throw BackupException(BackupException.Reason.NotABackup), covers)
    }

    // ------------------------------------------------------------------ the manifest

    internal fun manifest(backup: Backup): JSONObject {
        val o = JSONObject()
            .put("type", TYPE)
            .put("format", FORMAT)
            .put("app_version", backup.appVersion)
            .put("created_at", backup.createdAt)
            .put("settings_version", backup.settingsVersion)
        backup.settings?.let { values -> o.put("settings", JSONArray().apply { values.forEach { put(setting(it)) } }) }
        backup.equalizers?.let { o.put("equalizers", it) }
        backup.shortcuts?.let { keys -> o.put("shortcuts", JSONObject().apply { keys.toSortedMap().forEach { (id, chord) -> put(id, chord) } }) }
        backup.profiles?.let { profiles ->
            o.put("profiles", JSONArray().apply { profiles.forEach { text -> put(runCatching { JSONObject(text) }.getOrElse { return@forEach }) } })
        }
        backup.selectedProfile?.let { o.put("selected_profile", it) }
        backup.playlists?.let { lists -> o.put("playlists", JSONArray().apply { lists.forEach { put(playlist(it)) } }) }
        backup.smartLists?.let { lists -> o.put("smart_lists", JSONArray().apply { lists.forEach { put(smartList(it)) } }) }
        backup.stats?.let { rows -> o.put("stats", JSONArray().apply { rows.forEach { put(stats(it)) } }) }
        backup.lyrics?.let { rows -> o.put("lyrics", JSONArray().apply { rows.forEach { put(lyrics(it)) } }) }
        backup.fileSettings?.let { rows -> o.put("file_settings", JSONArray().apply { rows.forEach { put(fileSettings(it)) } }) }
        backup.covers?.let { covers -> o.put("covers", JSONArray().apply { covers.forEach { put(it.name) } }) }
        return o
    }

    internal fun parse(text: String, coverFiles: Map<String, ByteArray>): Backup {
        val o = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupException(if (text.trimStart().startsWith("{")) BackupException.Reason.Damaged else BackupException.Reason.NotABackup, e)
        }
        if (o.optString("type") != TYPE) throw BackupException(BackupException.Reason.NotABackup)
        val format = o.optInt("format", 0)
        if (format <= 0) throw BackupException(BackupException.Reason.NotABackup)
        if (format > FORMAT) throw BackupException(BackupException.Reason.Newer)
        return Backup(
            createdAt = o.optLong("created_at", 0L),
            appVersion = o.optString("app_version"),
            settingsVersion = o.optInt("settings_version", 0),
            settings = o.optJSONArray("settings")?.objects()?.mapNotNull(::setting),
            equalizers = o.optStringOrNull("equalizers"),
            shortcuts = o.optJSONObject("shortcuts")?.let { keys -> keys.keys().asSequence().associateWith { keys.optString(it) } },
            profiles = o.optJSONArray("profiles")?.objects()?.map { it.toString(2) },
            selectedProfile = o.optStringOrNull("selected_profile"),
            playlists = o.optJSONArray("playlists")?.objects()?.mapNotNull(::playlist),
            smartLists = o.optJSONArray("smart_lists")?.objects()?.mapNotNull(::smartList),
            stats = o.optJSONArray("stats")?.objects()?.mapNotNull(::stats),
            lyrics = o.optJSONArray("lyrics")?.objects()?.mapNotNull(::lyrics),
            fileSettings = o.optJSONArray("file_settings")?.objects()?.mapNotNull(::fileSettings),
            covers = o.optJSONArray("covers")?.let { names ->
                (0 until names.length()).mapNotNull { i ->
                    val name = names.optString(i)
                    coverFiles[name]?.let { BackupCover(name, it) }
                }
            },
        )
    }

    private fun setting(value: SettingValue): JSONObject {
        val o = JSONObject().put("key", value.key)
        when (val v = value.value) {
            is Boolean -> o.put("type", "boolean").put("value", v)
            is Int -> o.put("type", "int").put("value", v)
            is Long -> o.put("type", "long").put("value", v)
            is Float -> o.put("type", "float").put("value", v.toDouble())
            is Set<*> -> o.put("type", "set").put("value", JSONArray(v.map { it.toString() }.sorted()))
            else -> o.put("type", "string").put("value", v.toString())
        }
        return o
    }

    private fun setting(o: JSONObject): SettingValue? {
        val key = o.optString("key").takeIf { it.isNotEmpty() } ?: return null
        if (o.isNull("value")) return null
        val value: Any = when (o.optString("type")) {
            "boolean" -> o.optBoolean("value")
            "int" -> o.optInt("value")
            "long" -> o.optLong("value")
            "float" -> o.optDouble("value").takeIf { !it.isNaN() }?.toFloat() ?: return null
            "set" -> o.optJSONArray("value")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } ?: return null
            "string" -> o.optString("value")
            else -> return null
        }
        return SettingValue(key, value)
    }

    private fun playlist(list: BackupPlaylist): JSONObject = JSONObject()
        .put("name", list.name)
        .put(
            "entries",
            JSONArray().apply {
                list.entries.forEach { e ->
                    put(
                        JSONObject()
                            .put("location", e.location)
                            .put("title", e.title)
                            .put("artist", e.artist)
                            .put("album", e.album)
                            .put("duration_ms", e.durationMs),
                    )
                }
            },
        )

    private fun playlist(o: JSONObject): BackupPlaylist? {
        val name = o.optString("name").takeIf { it.isNotBlank() } ?: return null
        val entries = o.optJSONArray("entries")?.objects()?.mapNotNull { e ->
            val location = e.optString("location").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            PlaylistEntry(location, e.optStringOrNull("title"), e.optStringOrNull("artist"), e.optStringOrNull("album"), e.optLong("duration_ms", 0L))
        }.orEmpty()
        return BackupPlaylist(name, entries)
    }

    private fun smartList(list: SmartPlaylistEntry): JSONObject = JSONObject()
        .put("name", list.name)
        .put("rules", list.rules)
        .put("match_all", list.matchAll)
        .put("sort", list.sort)
        .put("limit", list.limitCount)
        .put("created_at", list.createdAt)
        .put("updated_at", list.updatedAt)

    private fun smartList(o: JSONObject): SmartPlaylistEntry? {
        val name = o.optString("name").takeIf { it.isNotBlank() } ?: return null
        return SmartPlaylistEntry(
            name = name,
            rules = o.optString("rules"),
            matchAll = o.optBoolean("match_all", true),
            sort = o.optStringOrNull("sort"),
            limitCount = o.optIntOrNull("limit"),
            createdAt = o.optLong("created_at", 0L),
            updatedAt = o.optLong("updated_at", 0L),
        )
    }

    private fun stats(row: TrackStatsEntry): JSONObject = JSONObject()
        .put("track", row.trackKey)
        .put("rating", row.rating)
        .put("plays", row.playCount)
        .put("skips", row.skipCount)
        .put("first_played_at", row.firstPlayedAt)
        .put("last_played_at", row.lastPlayedAt)
        .put("updated_at", row.updatedAt)

    private fun stats(o: JSONObject): TrackStatsEntry? {
        val key = o.optString("track").takeIf { it.isNotEmpty() } ?: return null
        return TrackStatsEntry(
            trackKey = key,
            rating = o.optInt("rating", 0).coerceIn(0, 5),
            playCount = o.optInt("plays", 0).coerceAtLeast(0),
            skipCount = o.optInt("skips", 0).coerceAtLeast(0),
            firstPlayedAt = o.optLong("first_played_at", 0L),
            lastPlayedAt = o.optLong("last_played_at", 0L),
            updatedAt = o.optLong("updated_at", 0L),
        )
    }

    private fun lyrics(row: LyricsEntry): JSONObject = JSONObject()
        .put("track", row.trackKey)
        .put("source", row.source)
        .put("synced", row.synced)
        .put("text", row.text)
        .put("language", row.language)
        .put("offset_ms", row.offsetMs)
        .put("fetched_at", row.fetchedAt)

    private fun lyrics(o: JSONObject): LyricsEntry? {
        val key = o.optString("track").takeIf { it.isNotEmpty() } ?: return null
        val source = o.optString("source").takeIf { it.isNotEmpty() } ?: return null
        return LyricsEntry(
            trackKey = key,
            source = source,
            synced = o.optBoolean("synced", false),
            text = o.optString("text"),
            language = o.optStringOrNull("language"),
            offsetMs = o.optLong("offset_ms", 0L),
            fetchedAt = o.optLong("fetched_at", 0L),
        )
    }

    private fun fileSettings(row: MediaSettings): JSONObject = JSONObject()
        .put("uri", row.mediaUri)
        .put("audio_delay_ms", row.audioDelayMs)
        .put("subtitle_delay_ms", row.subtitleDelayMs)
        .put("audio_track", row.audioTrack)
        .put("subtitle_track", row.subtitleTrack)
        .put("secondary_subtitle_track", row.secondarySubtitleTrack)
        .put("subtitle_encoding", row.subtitleEncoding)
        .put("aspect_ratio", row.aspectRatio)
        .put("playback_rate", row.playbackRate?.toDouble())
        .put("deinterlace", row.deinterlace)
        .put("equalizer_preset", row.equalizerPreset)
        .put("effects", row.effects)
        .put("subtitle_rate", row.subtitleRate)
        .put("updated_at", row.updatedAt)

    private fun fileSettings(o: JSONObject): MediaSettings? {
        val uri = o.optString("uri").takeIf { it.isNotEmpty() } ?: return null
        return MediaSettings(
            mediaUri = uri,
            audioDelayMs = o.optLongOrNull("audio_delay_ms"),
            subtitleDelayMs = o.optLongOrNull("subtitle_delay_ms"),
            audioTrack = o.optStringOrNull("audio_track"),
            subtitleTrack = o.optStringOrNull("subtitle_track"),
            secondarySubtitleTrack = o.optStringOrNull("secondary_subtitle_track"),
            subtitleEncoding = o.optStringOrNull("subtitle_encoding"),
            aspectRatio = o.optStringOrNull("aspect_ratio"),
            playbackRate = o.optDouble("playback_rate").takeIf { !it.isNaN() }?.toFloat(),
            deinterlace = o.optStringOrNull("deinterlace"),
            equalizerPreset = o.optStringOrNull("equalizer_preset"),
            updatedAt = o.optLong("updated_at", 0L),
            effects = o.optStringOrNull("effects"),
            subtitleRate = o.optStringOrNull("subtitle_rate"),
        )
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.optStringOrNull(name: String): String? = if (isNull(name)) null else optString(name)

    private fun JSONObject.optLongOrNull(name: String): Long? = if (isNull(name)) null else optLong(name, 0L)

    private fun JSONObject.optIntOrNull(name: String): Int? = if (isNull(name)) null else optInt(name, 0)
}

/** At most [limit] bytes of the stream; a longer one is not a backup this app made. */
private fun InputStream.readAtMost(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (out.size() + count > limit) throw BackupException(BackupException.Reason.Damaged)
        out.write(buffer, 0, count)
    }
    return out.toByteArray()
}
