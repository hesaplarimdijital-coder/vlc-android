package app.nagmedar.ui.tvlab

import android.content.SharedPreferences
import java.io.File
import java.text.Normalizer
import java.util.Locale

/** Where the profiles are kept (S-1203). */
interface ProfileStore {
    /** The app's profiles (as the user changed them) and the user's own, in that order. */
    fun all(): List<DeviceProfile>

    fun save(profile: DeviceProfile)

    /** A user's profile goes; one of the app's goes back to as it came. */
    fun remove(id: String)

    /** Whether the profile [id] is kept as the user saved it: theirs, or one of the app's they changed. */
    fun saved(id: String): Boolean

    var selected: String?
}

/**
 * The profiles as JSON files in [dir] ("<id>.json", the file a user shares), the one chosen in
 * [prefs]. An app profile saved there replaces the built-in one until it is removed.
 */
class FileProfileStore(private val dir: File, private val prefs: SharedPreferences) : ProfileStore {

    override fun all(): List<DeviceProfile> {
        val saved = dir.listFiles { file -> file.extension == "json" }.orEmpty()
            .mapNotNull { file -> runCatching { DeviceProfileCodec.decode(file.readText()) }.getOrNull() }
            .associateBy { it.id }
        val builtIns = BuiltInProfiles.all.map { builtIn -> saved[builtIn.id]?.copy(builtIn = true) ?: builtIn }
        val own = saved.values.filter { BuiltInProfiles.of(it.id) == null }.sortedBy { it.name.lowercase(Locale.ROOT) }
        return builtIns + own
    }

    override fun save(profile: DeviceProfile) {
        dir.mkdirs()
        File(dir, fileName(profile.id)).writeText(DeviceProfileCodec.encode(profile))
    }

    override fun remove(id: String) {
        File(dir, fileName(id)).delete()
        if (selected == id && BuiltInProfiles.of(id) == null) selected = null
    }

    override fun saved(id: String): Boolean = File(dir, fileName(id)).isFile

    override var selected: String?
        get() = prefs.getString(KEY_SELECTED, null)
        set(value) {
            prefs.edit().apply { if (value == null) remove(KEY_SELECTED) else putString(KEY_SELECTED, value) }.apply()
        }

    companion object {
        const val KEY_SELECTED = "nagmedar_tvlab_profile"

        /** A file name from an id: letters, digits and dashes. */
        fun fileName(id: String): String = id.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json"

        /** An id for a new profile named [name], not among [taken]: "sony-bravia-8", "sony-bravia-8-2". */
        fun newId(name: String, taken: Collection<String>): String {
            // letters without their marks: "Güçlü" → "guclu", "İzmir" → "izmir"; the dotless i has none to lose
            val base = Normalizer.normalize(name.lowercase(Locale.ROOT).replace('ı', 'i'), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "profile" }
            if (base !in taken) return base
            var n = 2
            while ("$base-$n" in taken) n++
            return "$base-$n"
        }
    }
}
