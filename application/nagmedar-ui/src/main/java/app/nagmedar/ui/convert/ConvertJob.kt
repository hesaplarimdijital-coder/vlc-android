package app.nagmedar.ui.convert

import org.json.JSONArray
import org.json.JSONObject

/** S-1010: a batch the converter works through: its choices and its files, in order. */
data class ConvertJob(val id: String, val createdAt: Long, val options: ConvertOptions, val items: List<ConvertItem>) {

    fun toJson(): String = JSONObject()
        .put("format", FORMAT)
        .put("id", id)
        .put("created_at", createdAt)
        .put("preset", options.preset.key)
        .put("rate", options.rate.name)
        .put("channels", options.channels.name)
        .put("naming", options.naming.name)
        .put("skip_existing", options.skipExisting)
        .put("folder", options.folder)
        .put("items", JSONArray(items.map { item ->
            JSONObject()
                .put("uri", item.uri)
                .put("title", item.title)
                .put("folders", JSONArray(item.folders))
                .put("name", item.name)
                .put("tags", JSONObject(item.tags))
        }))
        .toString()

    companion object {
        private const val FORMAT = 1

        /** A job as [toJson] wrote it; null for anything else. */
        fun parse(json: String): ConvertJob? = runCatching {
            val o = JSONObject(json)
            if (o.optInt("format") != FORMAT) return null
            val items = o.getJSONArray("items")
            ConvertJob(
                id = o.getString("id"),
                createdAt = o.optLong("created_at"),
                options = ConvertOptions(
                    preset = ConvertPreset.of(o.optString("preset")),
                    rate = ConvertRate.of(o.optString("rate")),
                    // a job written before the channels were a choice mixed everything to stereo
                    channels = ConvertChannels.of(o.optString("channels"), fallback = ConvertChannels.Stereo),
                    naming = ConvertNaming.of(o.optString("naming")),
                    skipExisting = o.optBoolean("skip_existing", true),
                    folder = o.optString("folder"),
                ),
                items = (0 until items.length()).map { index ->
                    val item = items.getJSONObject(index)
                    val folders = item.optJSONArray("folders") ?: JSONArray()
                    val tags = item.optJSONObject("tags") ?: JSONObject()
                    ConvertItem(
                        uri = item.getString("uri"),
                        title = item.optString("title"),
                        folders = (0 until folders.length()).map { folders.getString(it) },
                        name = item.getString("name"),
                        tags = tags.keys().asSequence().associateWith { tags.getString(it) },
                    )
                },
            )
        }.getOrNull()
    }
}

/** Why a file was not made. */
enum class ConvertReason(val key: String) {
    /** A file of that name is in the folder already, and the user asked to leave such files alone. */
    Exists("exists"),

    /** This build has no music engine. */
    NoEngine("no-engine"),

    /** The source could not be read: gone, or no longer ours to read. */
    Input("input"),

    /** The destination folder is gone, or no longer ours to write in. */
    Folder("folder"),

    /** The new file could not be made or written. */
    Output("output"),

    /** The file in the planned place is the source itself: it is never written over. */
    Source("source"),

    /** The engine stopped on the source (a damaged file) or the output (a full drive). */
    Engine("engine"),

    /** The battery ran low before this file. */
    Battery("battery"),

    /** The device got too hot before this file. */
    Thermal("thermal"),

    /** The user stopped the job before this file. */
    Cancelled("cancelled"),
    ;

    companion object {
        fun of(key: String?): ConvertReason = entries.firstOrNull { it.key == key } ?: Engine
    }
}

/** What became of one file of a job. */
sealed interface ItemOutcome {
    val item: ConvertItem

    /** Written, [bytes] long. */
    data class Converted(override val item: ConvertItem, val bytes: Long) : ItemOutcome

    /** Left alone: [ConvertReason.Exists]. */
    data class Skipped(override val item: ConvertItem) : ItemOutcome

    /** Not made, for [reason]; [detail] is the engine's word, for the curious. */
    data class Failed(override val item: ConvertItem, val reason: ConvertReason, val detail: String = "") : ItemOutcome
}

/**
 * S-1010: a job's outcome so far, kept as each file is done (a job taken up again after the
 * process was stopped goes on after the files it did): what was made, left alone, or failed.
 */
data class ConvertSummary(val jobId: String, val total: Int, val outcomes: List<ItemOutcome>, val finished: Boolean = false) {
    val converted: Int get() = outcomes.count { it is ItemOutcome.Converted }
    val skipped: Int get() = outcomes.count { it is ItemOutcome.Skipped }
    val failed: List<ItemOutcome.Failed> get() = outcomes.filterIsInstance<ItemOutcome.Failed>()

    /** Whether the user stopped it: the files after are failed as cancelled. */
    val cancelled: Boolean get() = outcomes.any { it is ItemOutcome.Failed && it.reason == ConvertReason.Cancelled }

    fun with(outcome: ItemOutcome): ConvertSummary = copy(outcomes = outcomes + outcome)

    fun toJson(): String = JSONObject()
        .put("job", jobId)
        .put("total", total)
        .put("finished", finished)
        .put("outcomes", JSONArray(outcomes.map { outcome ->
            val o = JSONObject().put("uri", outcome.item.uri).put("path", outcome.item.path)
            when (outcome) {
                is ItemOutcome.Converted -> o.put("state", "converted").put("bytes", outcome.bytes)
                is ItemOutcome.Skipped -> o.put("state", "skipped")
                is ItemOutcome.Failed -> o.put("state", "failed").put("reason", outcome.reason.key).put("detail", outcome.detail)
            }
        }))
        .toString()

    companion object {
        /** A summary as [toJson] wrote it, its outcomes matched to [job]'s files by uri and path; null for anything else. */
        fun parse(json: String, job: ConvertJob): ConvertSummary? = runCatching {
            val o = JSONObject(json)
            val byKey = job.items.associateBy { it.uri + "\u0000" + it.path }
            val list = o.getJSONArray("outcomes")
            val outcomes = (0 until list.length()).mapNotNull { index ->
                val entry = list.getJSONObject(index)
                val item = byKey[entry.optString("uri") + "\u0000" + entry.optString("path")] ?: return@mapNotNull null
                when (entry.optString("state")) {
                    "converted" -> ItemOutcome.Converted(item, entry.optLong("bytes"))
                    "skipped" -> ItemOutcome.Skipped(item)
                    else -> ItemOutcome.Failed(item, ConvertReason.of(entry.optString("reason")), entry.optString("detail"))
                }
            }
            ConvertSummary(o.optString("job", job.id), o.optInt("total", job.items.size), outcomes, o.optBoolean("finished"))
        }.getOrNull()
    }
}
