package app.nagmedar.ui.tvlab.remux

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.lab.AudioTech
import app.nagmedar.lab.DolbyVisionMode
import app.nagmedar.lab.MediaTech
import app.nagmedar.lab.PlannedStream
import app.nagmedar.lab.RemuxPlan
import app.nagmedar.lab.StreamRef
import app.nagmedar.lab.StreamType
import app.nagmedar.ui.library.mainAudio
import app.nagmedar.ui.tvlab.CheckReport
import app.nagmedar.ui.tvlab.DeviceProfile
import app.nagmedar.ui.tvlab.FindingKind
import app.nagmedar.ui.tvlab.ProfileCheck
import app.nagmedar.ui.tvlab.audioKey
import app.nagmedar.ui.tvlab.subtitleKey
import java.util.Locale

/** What a step of the plan keeps (S-1205): everything, everything with its tags changed, or not all of it. */
enum class Fidelity { Lossless, LosslessMetadata, Lossy }

/** An audio or subtitle track as the source has it, named as the interface names it. */
@Immutable
data class SourceTrack(
    val ref: StreamRef,
    val codec: String,
    val label: String,
    /** ISO 639-2 as tagged, empty when untagged. */
    val language: String,
    val title: String,
    val isDefault: Boolean,
    val forced: Boolean,
    /** Whether the target device plays (or shows) it. */
    val playable: Boolean,
)

/** A track in the planner: kept or dropped, its flags, language and title as edited. */
@Stable
class PlanTrack(val source: SourceTrack) {
    var kept by mutableStateOf(true)
    var isDefault by mutableStateOf(source.isDefault)
    var forced by mutableStateOf(source.forced)
    var language by mutableStateOf(source.language)
    var title by mutableStateOf(source.title)

    val ref: StreamRef get() = source.ref

    /** Three letters (ISO 639-2), or nothing: the source's then. */
    val languageValid: Boolean get() = language.isBlank() || LANGUAGE.matches(language.trim())

    /** The language the output gets: the edited one, or the source's when left empty. */
    val outputLanguage: String get() = language.trim().lowercase(Locale.ROOT).ifEmpty { source.language }

    val metadataChanged: Boolean
        get() = isDefault != source.isDefault || forced != source.forced || outputLanguage != source.language || title != source.title

    private companion object {
        val LANGUAGE = Regex("[A-Za-z]{3}")
    }
}

/** What the device profile's findings suggest for the plan, with what they are about. */
enum class SuggestionKind {
    /** Dolby Vision profile 7 on a device that plays only single-layer profiles: converted to 8.1. */
    ConvertDolbyVision,

    /** The track a player starts with is one the device does not play: another becomes the default and goes first. */
    DefaultAudio,

    /** Not one track the device plays: an added compatibility track would be needed (the original stays). */
    NoPlayableAudio,

    /** Subtitles the device does not show: they may be dropped. */
    DropSubtitles,
}

/** A suggestion of the plan: [subject] what it is about, [target] what it proposes (a track's name), [refs] the tracks it changes. */
@Immutable
data class PlanSuggestion(val kind: SuggestionKind, val subject: String, val target: String = "", val refs: List<StreamRef> = emptyList())

/** What a plan made for many films at once does (S-1210): the suggestions applied, or left. */
@Immutable
data class PlanOptions(
    val convertDolbyVision: Boolean = true,
    val fixDefaultAudio: Boolean = true,
    val dropUnsupportedSubtitles: Boolean = false,
    val chapters: Boolean = true,
    val attachments: Boolean = true,
    val metadata: Boolean = true,
)

/** The plan in numbers, for the summary before it is queued. */
@Immutable
data class PlanSummary(
    val audioKept: Int,
    val subtitlesKept: Int,
    val audioDropped: Int,
    val subtitlesDropped: Int,
    val lossless: Int,
    val metadataChanged: Int,
    val lossy: Int,
) {
    val streams: Int get() = 1 + audioKept + subtitlesKept
}

/**
 * The remux planner (S-1205): a film's plan as the user edits it before it is queued. The
 * video is always copied (with Dolby Vision profile 7 optionally converted to 8.1); every audio
 * and subtitle track kept or dropped, its default and forced flags, language and title set, the
 * tracks of a kind in the order the user puts them; chapters, attachments and the file's tags
 * kept or not; the output's name. Every step says what it keeps ([Fidelity]): dropping a track
 * loses that track, the conversion keeps the base layer bit for bit. The device profile's
 * findings fill it in first ([of]).
 */
@Stable
class PlanDraft(
    val tech: MediaTech,
    val videoLabel: String,
    audio: List<SourceTrack>,
    subtitles: List<SourceTrack>,
    /** The target device's name, for the suggestions' words. */
    val device: String = "",
) {
    var audio by mutableStateOf(audio.map(::PlanTrack))
        private set
    var subtitles by mutableStateOf(subtitles.map(::PlanTrack))
        private set
    var convertDolbyVision by mutableStateOf(false)
    var chapters by mutableStateOf(true)
    var attachments by mutableStateOf(true)
    var metadata by mutableStateOf(true)
    var outputName by mutableStateOf("")

    /**
     * The compatibility tracks of S-1207 (a source track encoded beside its original, its
     * [PlannedStream.encode] set), each written right after its original. None until the
     * engine encodes: the planner's "Ek ses izi" stays off.
     */
    var compatibility by mutableStateOf(emptyList<PlannedStream>())

    var suggestions by mutableStateOf(emptyList<PlanSuggestion>())
        private set

    val dvProfile: Int get() = tech.video?.dvProfile ?: 0

    /** Profile 7 (UHD Blu-ray, two layers) is what the conversion takes. */
    val canConvert: Boolean get() = dvProfile == 7

    /** A full enhancement layer carries picture (12 bit) that the conversion drops; a minimal one only metadata. */
    val fullEnhancement: Boolean get() = tech.video?.dvEnhancement == "FEL"

    fun tracks(type: StreamType): List<PlanTrack> = if (type == StreamType.Subtitle) subtitles else audio

    /** Moves a track of [type] from [from] to [to] among its kind. */
    fun move(type: StreamType, from: Int, to: Int) {
        val list = tracks(type).toMutableList()
        if (from !in list.indices) return
        val target = to.coerceIn(0, list.lastIndex)
        if (target == from) return
        list.add(target, list.removeAt(from))
        if (type == StreamType.Subtitle) subtitles = list else audio = list
    }

    /** [track] made the default of its kind or not; a default is one of a kind, the others lose it. */
    fun setDefault(track: PlanTrack, on: Boolean) {
        if (on) tracks(track.ref.type).forEach { if (it !== track) it.isDefault = false }
        track.isDefault = on
    }

    val videoFidelity: Fidelity
        get() = when {
            !convertDolbyVision || !canConvert -> Fidelity.Lossless
            fullEnhancement -> Fidelity.Lossy
            else -> Fidelity.LosslessMetadata
        }

    /** A track's step: dropped it is lost; kept with other tags or in another place its packets stay as they were. */
    fun fidelity(track: PlanTrack): Fidelity {
        val index = tracks(track.ref.type).indexOf(track)
        return when {
            !track.kept -> Fidelity.Lossy
            track.metadataChanged || index != track.ref.ordinal -> Fidelity.LosslessMetadata
            else -> Fidelity.Lossless
        }
    }

    /** Chapters, attachments, tags: kept, or lost. */
    fun fidelity(kept: Boolean): Fidelity = if (kept) Fidelity.Lossless else Fidelity.Lossy

    /** The output's name, ".mkv" at its end. */
    val outputFileName: String
        get() = outputName.trim().let { if (it.endsWith(MKV, ignoreCase = true)) it else it + MKV }

    val outputNameValid: Boolean get() = outputName.isNotBlank() && outputName.none { it == '/' || it == '\\' || it.isISOControl() }

    /** Whether it can be queued: a video, a name, the languages typed right. */
    val valid: Boolean get() = tech.video != null && outputNameValid && (audio + subtitles).all { !it.kept || it.languageValid }

    /**
     * The plan the engine runs: the video, then the audio tracks kept, each with the
     * compatibility tracks made from it right after it (also when it is dropped itself), then
     * the subtitles, in the order set.
     */
    fun plan(): RemuxPlan {
        val streams = mutableListOf<PlannedStream>()
        tech.video?.let { streams += PlannedStream(StreamRef(StreamType.Video, 0), it.codec, videoLabel) }
        audio.forEach { track ->
            if (track.kept) streams += planned(track)
            streams += compatibility.filter { it.source == track.ref }
        }
        streams += compatibility.filter { added -> audio.none { it.ref == added.source } }
        streams += subtitles.filter { it.kept }.map(::planned)
        return RemuxPlan(
            streams = streams,
            dolbyVision = if (convertDolbyVision && canConvert) DolbyVisionMode.ConvertToProfile81 else DolbyVisionMode.Keep,
            chapters = chapters,
            attachments = attachments,
            metadata = metadata,
        )
    }

    /** Only what changed is written: the rest the engine keeps as the source has it. */
    private fun planned(track: PlanTrack): PlannedStream {
        val source = track.source
        return PlannedStream(
            source = source.ref,
            codec = source.codec,
            label = source.label,
            language = track.outputLanguage.takeIf { it != source.language },
            title = track.title.takeIf { it != source.title },
            isDefault = track.isDefault.takeIf { it != source.isDefault },
            forced = track.forced.takeIf { it != source.forced },
        )
    }

    fun summary(): PlanSummary {
        val all = audio + subtitles
        val steps = all.map(::fidelity) + videoFidelity +
            listOfNotNull(fidelity(chapters).takeIf { tech.chapters > 0 }, fidelity(attachments), fidelity(metadata))
        return PlanSummary(
            audioKept = audio.count { it.kept },
            subtitlesKept = subtitles.count { it.kept },
            audioDropped = audio.count { !it.kept },
            subtitlesDropped = subtitles.count { !it.kept },
            lossless = steps.count { it == Fidelity.Lossless },
            metadataChanged = steps.count { it == Fidelity.LosslessMetadata },
            lossy = steps.count { it == Fidelity.Lossy },
        )
    }

    /** Whether [suggestion] is what the plan does now. */
    fun applied(suggestion: PlanSuggestion): Boolean = when (suggestion.kind) {
        SuggestionKind.ConvertDolbyVision -> convertDolbyVision
        SuggestionKind.DefaultAudio -> suggestion.refs.firstOrNull()?.let { ref -> audio.firstOrNull { it.ref == ref }?.let { it.kept && it.isDefault } } == true
        SuggestionKind.DropSubtitles -> suggestion.refs.all { ref -> subtitles.none { it.ref == ref && it.kept } }
        SuggestionKind.NoPlayableAudio -> false
    }

    /** Whether [suggestion] has something to apply. */
    fun actionable(suggestion: PlanSuggestion): Boolean = suggestion.kind != SuggestionKind.NoPlayableAudio

    /** [suggestion] applied ([on]) or taken back. */
    fun apply(suggestion: PlanSuggestion, on: Boolean) {
        when (suggestion.kind) {
            SuggestionKind.ConvertDolbyVision -> convertDolbyVision = on && canConvert
            SuggestionKind.DefaultAudio -> {
                val ref = suggestion.refs.firstOrNull() ?: return
                if (on) {
                    val track = audio.firstOrNull { it.ref == ref } ?: return
                    track.kept = true
                    setDefault(track, true)
                    // a player without a default track takes the first: it goes first too
                    move(StreamType.Audio, audio.indexOf(track), 0)
                } else {
                    audio.forEach { it.isDefault = it.source.isDefault }
                    audio = audio.sortedBy { it.ref.ordinal }
                }
            }
            SuggestionKind.DropSubtitles -> subtitles.filter { it.ref in suggestion.refs }.forEach { it.kept = !on }
            SuggestionKind.NoPlayableAudio -> Unit
        }
    }

    /** Applies [options] to the plan: the suggestions it names, chapters, attachments and tags. */
    fun applyOptions(options: PlanOptions) {
        suggestions.forEach { suggestion ->
            val on = when (suggestion.kind) {
                SuggestionKind.ConvertDolbyVision -> options.convertDolbyVision
                SuggestionKind.DefaultAudio -> options.fixDefaultAudio
                SuggestionKind.DropSubtitles -> options.dropUnsupportedSubtitles
                SuggestionKind.NoPlayableAudio -> return@forEach
            }
            if (applied(suggestion) != on) apply(suggestion, on)
        }
        chapters = options.chapters
        attachments = options.attachments
        metadata = options.metadata
    }

    companion object {
        private const val MKV = ".mkv"

        /**
         * The plan of [tech] for [report]'s device: every track kept as it is, then the findings'
         * suggestions applied as [options] says (the Dolby Vision conversion, a default track the
         * device plays); [names] names the tracks as the interface does, [outputName] the file.
         */
        fun of(
            tech: MediaTech,
            report: CheckReport?,
            profile: DeviceProfile?,
            names: ProfileCheck.StreamNames,
            outputName: String,
            options: PlanOptions = PlanOptions(),
        ): PlanDraft {
            val audio = tech.audio.mapIndexed { i, a ->
                SourceTrack(StreamRef(StreamType.Audio, i), a.codec, names.audio(a), a.language, a.title, a.isDefault, a.forced, profile?.let { plays(it, a) } ?: true)
            }
            val subtitles = tech.subtitles.mapIndexed { i, s ->
                SourceTrack(
                    StreamRef(StreamType.Subtitle, i), s.codec, names.subtitles(listOf(s)), s.language, s.title, s.isDefault, s.forced,
                    profile?.allowsSubtitle(subtitleKey(s)) ?: true,
                )
            }
            val draft = PlanDraft(tech, tech.video?.let(names::video).orEmpty(), audio, subtitles, profile?.name.orEmpty())
            draft.outputName = outputName
            draft.suggestions = suggestionsOf(tech, report, audio, subtitles, names)
            draft.applyOptions(options)
            return draft
        }

        private fun plays(profile: DeviceProfile, track: AudioTech): Boolean {
            val key = audioKey(track)
            return profile.allowsAudio(key) || (key == "dtshd" && profile.allowsAudio("dts"))
        }

        private fun suggestionsOf(
            tech: MediaTech,
            report: CheckReport?,
            audio: List<SourceTrack>,
            subtitles: List<SourceTrack>,
            names: ProfileCheck.StreamNames,
        ): List<PlanSuggestion> {
            if (report == null) return emptyList()
            val found = mutableListOf<PlanSuggestion>()
            val kinds = report.findings.map { it.kind }.toSet()
            if (FindingKind.DolbyVisionDualLayer in kinds && tech.video?.dvProfile == 7) {
                found += PlanSuggestion(SuggestionKind.ConvertDolbyVision, report.findings.first { it.kind == FindingKind.DolbyVisionDualLayer }.subject)
            }
            val main = tech.mainAudio()
            val mainIndex = main?.let { tech.audio.indexOf(it) } ?: -1
            val mainTrack = audio.getOrNull(mainIndex)
            if (mainTrack != null && !mainTrack.playable) {
                val alternative = audio.withIndex()
                    .filter { (i, t) -> t.playable && !tech.audio[i].commentary }
                    .sortedWith(
                        compareByDescending<IndexedValue<SourceTrack>> { it.value.language == mainTrack.language }
                            .thenByDescending { tech.audio[it.index].channels }
                            .thenBy { it.index },
                    )
                    .firstOrNull()?.value
                found += if (alternative != null) {
                    PlanSuggestion(SuggestionKind.DefaultAudio, mainTrack.label, alternative.label, listOf(alternative.ref))
                } else {
                    PlanSuggestion(SuggestionKind.NoPlayableAudio, mainTrack.label)
                }
            }
            val hidden = subtitles.filter { !it.playable }
            if (hidden.isNotEmpty()) {
                val tracks = hidden.map { tech.subtitles[it.ref.ordinal] }
                found += PlanSuggestion(SuggestionKind.DropSubtitles, names.subtitles(tracks), refs = hidden.map { it.ref })
            }
            return found
        }
    }
}
