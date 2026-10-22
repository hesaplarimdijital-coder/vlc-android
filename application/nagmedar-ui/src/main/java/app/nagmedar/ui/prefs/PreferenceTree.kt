package app.nagmedar.ui.prefs

import android.content.res.Resources
import android.content.res.XmlResourceParser
import androidx.annotation.XmlRes
import androidx.compose.runtime.Immutable
import org.xmlpull.v1.XmlPullParser

/** What a line of VLC's preference XML is drawn as (the generic renderer of "All settings"). */
enum class PrefKind {
    /** A PreferenceScreen or a plain Preference: opens a page, a Nağmedar section, or does something. */
    Link,

    /** A Preference with only a summary: a line of text. */
    Info,

    /** CheckBoxPreference, SwitchPreference: a Boolean. */
    Toggle,

    /** ListPreference: one String of [PrefItem.entryValues]. */
    Choice,

    /** MultiSelectListPreference: a String set. */
    MultiChoice,

    /** EditTextPreference: a String. */
    Text,

    /** SeekBarPreference: an Int between [PrefItem.min] and [PrefItem.max]. */
    Slider,

    /** VLC's NumberPickerPreference: an Int of 1…100 (seconds). */
    Number,

    /** The colour picker's ColorPreferenceCompat: an ARGB Int. */
    Colour,
}

/**
 * One preference as VLC's XML describes it: its key and default (what the playback code reads, so
 * it is written unchanged), its title and summary from the same string resources, its
 * `android:dependency`, its entries. [summary] may hold `%s`, which the renderer fills in.
 */
@Immutable
data class PrefItem(
    val kind: PrefKind,
    val key: String?,
    val title: String,
    val summary: String? = null,
    val defaultValue: String? = null,
    val defaultSet: Set<String> = emptySet(),
    val dependency: String? = null,
    val disableDependentsState: Boolean = false,
    val entries: List<String> = emptyList(),
    val entryValues: List<String> = emptyList(),
    val min: Int = 0,
    val max: Int = 100,
    /** False for a value the page applies but does not keep (the subtitles' presets). */
    val persistent: Boolean = true,
    /** `app:isPreferenceVisible`; the catalogue's rules come on top. */
    val shown: Boolean = true,
    /** EditTextPreference's `useSimpleSummaryProvider`: the text itself is the summary. */
    val simpleSummary: Boolean = false,
    val maxLength: Int = 0,
    val multiLine: Boolean = false,
)

/** A PreferenceCategory, or the lines of a page before its first one ([title] null). */
@Immutable
data class PrefSection(val key: String?, val title: String?, val items: List<PrefItem>)

/** One preference XML: a page of "All settings". [id] is the key that opens it. */
@Immutable
data class PrefPage(val id: String, val title: String, val sections: List<PrefSection>) {
    val items: List<PrefItem> get() = sections.flatMap { it.items }
}

/** Reads VLC's preference XML out of the APK's resources at run time ([Resources.getXml]). */
object PreferenceXml {

    private const val ANDROID = "http://schemas.android.com/apk/res/android"

    /** The page in [xml]; [title], when given, stands for the XML's own (a page opened under another name). */
    fun parse(res: Resources, @XmlRes xml: Int, id: String, title: String? = null): PrefPage {
        val parser = res.getXml(xml)
        try {
            return read(res, parser, id, title)
        } finally {
            parser.close()
        }
    }

    private fun read(res: Resources, parser: XmlResourceParser, id: String, title: String?): PrefPage {
        val sections = mutableListOf<PrefSection>()
        var pageTitle = title
        var loose = mutableListOf<PrefItem>()
        // a category's key and title, read at its start tag, and its lines
        var category: Triple<String?, String?, MutableList<PrefItem>>? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val attrs = Attrs(res, parser)
                val tag = parser.name.substringAfterLast('.')
                when {
                    parser.depth == 1 -> if (pageTitle == null) pageTitle = attrs.string("title").orEmpty()
                    tag == "PreferenceCategory" -> {
                        if (loose.isNotEmpty()) sections += PrefSection(null, null, loose)
                        loose = mutableListOf()
                        category = Triple(attrs.string("key"), attrs.string("title"), mutableListOf<PrefItem>())
                    }
                    else -> item(tag, attrs)?.let { (category?.third ?: loose) += it }
                }
            } else if (event == XmlPullParser.END_TAG && parser.name.substringAfterLast('.') == "PreferenceCategory") {
                category?.let { (key, categoryTitle, items) -> sections += PrefSection(key, categoryTitle, items) }
                category = null
            }
            event = parser.next()
        }
        if (loose.isNotEmpty()) sections += PrefSection(null, null, loose)
        return PrefPage(id, pageTitle.orEmpty(), sections)
    }

    private fun item(tag: String, attrs: Attrs): PrefItem? {
        val kind = when (tag) {
            "CheckBoxPreference", "SwitchPreference", "SwitchPreferenceCompat", "TwoStatePreference" -> PrefKind.Toggle
            "ListPreference", "DropDownPreference" -> PrefKind.Choice
            "MultiSelectListPreference" -> PrefKind.MultiChoice
            "EditTextPreference" -> PrefKind.Text
            "SeekBarPreference" -> PrefKind.Slider
            "NumberPickerPreference" -> PrefKind.Number
            "ColorPreferenceCompat", "ColorPreference" -> PrefKind.Colour
            else -> if (attrs.string("title").isNullOrEmpty()) PrefKind.Info else PrefKind.Link
        }
        val key = attrs.string("key")
        val summary = attrs.string("summary")
        if (kind == PrefKind.Info && summary.isNullOrEmpty()) return null
        return PrefItem(
            kind = kind,
            key = key,
            title = attrs.string("title").orEmpty(),
            summary = summary,
            defaultValue = attrs.default(),
            defaultSet = attrs.array("defaultValue").toSet(),
            dependency = attrs.string("dependency"),
            disableDependentsState = attrs.bool("disableDependentsState", false),
            entries = attrs.array("entries"),
            entryValues = attrs.array("entryValues"),
            min = attrs.int("min", if (kind == PrefKind.Number) 1 else 0),
            max = attrs.int("max", 100),
            persistent = attrs.bool("persistent", true),
            shown = attrs.bool("isPreferenceVisible", true),
            simpleSummary = attrs.bool("useSimpleSummaryProvider", false),
            maxLength = attrs.int("maxLength", 0),
            multiLine = attrs.string("inputType")?.contains("multiLine", ignoreCase = true) == true,
        )
    }

    /**
     * The attributes of one tag by their names; `android:` wins over `app:` when both are there.
     * A value is a resource reference (`@string/…`, `@array/…`, `@bool/…`, `@color/…`) or a literal.
     */
    private class Attrs(private val res: Resources, private val parser: XmlResourceParser) {
        private val index: Map<String, Int> = buildMap {
            for (i in 0 until parser.attributeCount) {
                val name = parser.getAttributeName(i)
                if (parser.getAttributeNamespace(i) == ANDROID) put(name, i) else if (!containsKey(name)) put(name, i)
            }
        }

        private fun reference(name: String): Int = index[name]?.let { parser.getAttributeResourceValue(it, 0) } ?: 0

        fun string(name: String): String? {
            val i = index[name] ?: return null
            val ref = parser.getAttributeResourceValue(i, 0)
            return if (ref != 0) runCatching { res.getString(ref) }.getOrNull() else parser.getAttributeValue(i)
        }

        fun array(name: String): List<String> {
            val ref = reference(name)
            if (ref == 0 || runCatching { res.getResourceTypeName(ref) }.getOrNull() != "array") return emptyList()
            return runCatching { res.getStringArray(ref).toList() }.getOrDefault(emptyList())
        }

        fun bool(name: String, fallback: Boolean): Boolean {
            val i = index[name] ?: return fallback
            val ref = parser.getAttributeResourceValue(i, 0)
            if (ref != 0) return runCatching { res.getBoolean(ref) }.getOrDefault(fallback)
            return parser.getAttributeValue(i)?.toBooleanStrictOrNull() ?: fallback
        }

        fun int(name: String, fallback: Int): Int {
            val i = index[name] ?: return fallback
            val ref = parser.getAttributeResourceValue(i, 0)
            if (ref != 0) return runCatching { res.getInteger(ref) }.getOrDefault(fallback)
            return parser.getAttributeValue(i)?.toIntOrNull() ?: fallback
        }

        /** The default as the preference would keep it, written as text: "true", "16", "-1", an ARGB colour's Int. */
        fun default(): String? {
            val i = index["defaultValue"] ?: return null
            val ref = parser.getAttributeResourceValue(i, 0)
            if (ref == 0) return parser.getAttributeValue(i)
            return runCatching {
                when (res.getResourceTypeName(ref)) {
                    "bool" -> res.getBoolean(ref).toString()
                    "integer" -> res.getInteger(ref).toString()
                    "color" -> res.getColor(ref, null).toString()
                    "array" -> null
                    else -> res.getString(ref)
                }
            }.getOrNull()
        }
    }
}
