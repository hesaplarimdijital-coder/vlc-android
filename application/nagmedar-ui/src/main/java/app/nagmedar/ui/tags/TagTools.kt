/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.3.0: after Metamatik (feature/editor/lookup/LookupSheet.kt, core/ui/cover/CoverFinderSheet.kt,
 * feature/batch/AlbumLookupSheet.kt, MergeAlbumsCard.kt and BatchScreen.kt), a fork of Metadator, under the
 * AGPL-3.0; written again on Nağmedar's components.
 */
package app.nagmedar.ui.tags

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.tags.TagField
import app.nagmedar.tags.batch.FileNamePattern
import app.nagmedar.tags.batch.TrackNumbering
import app.nagmedar.tags.cover.CoverReason
import app.nagmedar.tags.cover.CoverSearchOutcome
import app.nagmedar.tags.cover.CoverSearchProgress
import app.nagmedar.tags.cover.CoverSuggestion
import app.nagmedar.tags.lookup.AlbumProposal
import app.nagmedar.tags.lookup.AlbumSearchOutcome
import app.nagmedar.tags.lookup.AlbumSearchProgress
import app.nagmedar.tags.lookup.FieldProposal
import app.nagmedar.tags.lookup.LookupCandidate
import app.nagmedar.tags.lookup.LookupOutcome
import app.nagmedar.tags.lookup.LookupQuery
import app.nagmedar.tags.lookup.MatchConfidence
import app.nagmedar.ui.R
import app.nagmedar.ui.components.DialogCard
import app.nagmedar.ui.components.DialogFrame
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.NagDialog
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.NagSwitch
import app.nagmedar.ui.components.NagTextField
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.radio.RadioLogos
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.launch
import org.videolan.tools.Notices

/** Which of the editor's lookups is open. */
internal enum class TagTool { Lookup, Covers, Album }

/** 1.3.0: the head's buttons: one file's details or several files' album looked up online, and a cover. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ToolButtons(model: TagEditorModel, onOpen: (TagTool) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (model.many) {
            NagButton(stringResource(R.string.nag_tag_find_album), { onOpen(TagTool.Album) }, icon = NagIcons.Albums, modifier = Modifier.testTag("tag-find-album"))
        } else {
            NagButton(stringResource(R.string.nag_tag_find_info), { onOpen(TagTool.Lookup) }, icon = NagIcons.Search, modifier = Modifier.testTag("tag-find-info"))
        }
        NagButton(stringResource(R.string.nag_tag_find_cover), { onOpen(TagTool.Covers) }, icon = NagIcons.Albums, modifier = Modifier.testTag("tag-find-cover"))
    }
}

/** The lookup [tool] over the editor, while open. */
@Composable
internal fun ToolDialog(model: TagEditorModel, tool: TagTool, onDismiss: () -> Unit) {
    when (tool) {
        TagTool.Lookup -> LookupDialog(model, onDismiss)
        TagTool.Covers -> CoverFinderDialog(model, onDismiss)
        TagTool.Album -> AlbumLookupDialog(model, onDismiss)
    }
}

/** A picture on the network, for a list: what [RadioLogos] keeps of it. */
@Composable
private fun rememberWebImage(url: String?, size: Dp): ImageBitmap? {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val image by produceState<ImageBitmap?>(null, url, px) { value = url?.let { RadioLogos.load(it, px) } }
    return image
}

@Composable
private fun Thumbnail(url: String?, size: Dp, modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(6.dp)
    Box(modifier.size(size).clip(shape).background(colors.surfaceRaised, shape), contentAlignment = Alignment.Center) {
        val image = rememberWebImage(url, size)
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Icon(NagIcons.Albums, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(size / 3))
        }
    }
}

@Composable
private fun confidenceLabel(confidence: MatchConfidence): String = stringResource(
    when (confidence) {
        MatchConfidence.High -> R.string.nag_tag_match_high
        MatchConfidence.Medium -> R.string.nag_tag_match_medium
        MatchConfidence.Low -> R.string.nag_tag_match_low
    },
)

/** A row a list can be chosen from: pressed, it opens. */
@Composable
private fun ChoiceRow(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(NagTheme.dimens.radiusControl)
    Box(
        modifier
            .fillMaxWidth()
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .nagClickable(interaction, onClick)
            .padding(8.dp),
    ) { content() }
}

/** A word in place of results: searching, nothing, no connection. */
@Composable
private fun StatusLine(text: String, progress: Float? = -1f) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, style = NagTheme.type.body, color = NagTheme.colors.textSecondary, modifier = Modifier.testTag("tag-tool-status"))
        if (progress == null || progress >= 0f) NagProgressBar(progress, text)
    }
}

// ------------------------------------------------------------------ one file: its details

private sealed interface LookupStatus {
    data object Searching : LookupStatus

    data class Results(val candidates: List<LookupCandidate>, val failed: List<String>) : LookupStatus

    data class Problem(val text: Int) : LookupStatus
}

private fun LookupOutcome.status(): LookupStatus = when (this) {
    is LookupOutcome.Results -> LookupStatus.Results(candidates, failedProviders)
    LookupOutcome.NoProviders -> LookupStatus.Problem(R.string.nag_tag_lookup_no_catalogues)
    LookupOutcome.Offline -> LookupStatus.Problem(R.string.nag_tag_lookup_offline)
    LookupOutcome.RateLimited -> LookupStatus.Problem(R.string.nag_tag_lookup_busy)
    LookupOutcome.Failed -> LookupStatus.Problem(R.string.nag_tag_lookup_failed)
}

/**
 * "Bilgi ara": the song looked up in the catalogues by its title, artist and album; a result opened is compared field
 * by field with the file, the fields that differ checked; the chosen ones become changes to save (nothing is written
 * here), the cover with them when asked.
 */
@Composable
private fun LookupDialog(model: TagEditorModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val tools = rememberTagLookups()
    val scope = rememberCoroutineScope()
    val first = remember { model.lookupQuery() }
    var title by remember { mutableStateOf(first.title) }
    var artist by remember { mutableStateOf(first.artist.orEmpty()) }
    var album by remember { mutableStateOf(first.album.orEmpty()) }
    var status by remember { mutableStateOf<LookupStatus>(LookupStatus.Searching) }
    var open by remember { mutableStateOf<LookupCandidate?>(null) }
    val search = {
        status = LookupStatus.Searching
        scope.launch {
            status = tools.service.search(LookupQuery(title.trim(), artist.trim().ifEmpty { null }, album.trim().ifEmpty { null }, first.durationMs)).status()
        }
    }
    LaunchedEffect(Unit) { search() }
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) {
            DialogCard(stringResource(R.string.nag_tag_lookup_title), Modifier.testTag("tag-lookup"), cardWidth = 560.dp) {
                val candidate = open
                if (candidate == null) {
                    NagTextField(title, { title = it }, stringResource(R.string.nag_tag_title), modifier = Modifier.testTag("tag-lookup-title"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { NagTextField(artist, { artist = it }, stringResource(R.string.nag_tag_artists)) }
                        Box(Modifier.weight(1f)) { NagTextField(album, { album = it }, stringResource(R.string.nag_tag_album), onDone = { search() }) }
                    }
                    Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                        when (val now = status) {
                            LookupStatus.Searching -> StatusLine(stringResource(R.string.nag_tag_lookup_searching), progress = null)
                            is LookupStatus.Problem -> StatusLine(stringResource(now.text), progress = -2f)
                            is LookupStatus.Results -> {
                                if (now.candidates.isEmpty()) StatusLine(stringResource(R.string.nag_tag_lookup_none), progress = -2f)
                                now.candidates.forEachIndexed { index, found ->
                                    ChoiceRow({ open = found }, Modifier.testTag("tag-lookup-result-$index")) { CandidateLine(found) }
                                }
                                if (now.failed.isNotEmpty()) {
                                    Text(stringResource(R.string.nag_tag_lookup_partly, now.failed.joinToString(", ")), style = NagTheme.type.labelSmall, color = NagTheme.colors.warning)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        NagButton(stringResource(R.string.nag_tag_close), onDismiss)
                        NagButton(stringResource(R.string.nag_tag_lookup_search), { search() }, primary = true, enabled = title.isNotBlank() || album.isNotBlank(), modifier = Modifier.testTag("tag-lookup-search"))
                    }
                } else {
                    Comparison(model, candidate, onBack = { open = null }, onApply = { fields, coverUrl ->
                        model.applyFields(fields)
                        if (coverUrl != null) model.chooseCover { tools.downloader.download(coverUrl)?.let { it.bytes to it.mimeType } }
                        Notices.show(context, context.getString(R.string.nag_tag_lookup_applied))
                        onDismiss()
                    })
                }
            }
        }
    }
}

/** A result: its cover, title and artists, album, year and track, where it was found and how well it matches. */
@Composable
private fun CandidateLine(found: LookupCandidate, thumbnail: Dp = 44.dp) {
    val colors = NagTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Thumbnail(found.artworkThumbnailUrl, thumbnail)
        Column(Modifier.weight(1f)) {
            Text(listOf(found.title, found.artists.joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · "), style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = listOfNotNull(found.album, found.year, found.trackNumber?.let { "#$it" }).joinToString(" · ")
            if (line.isNotEmpty()) Text(line, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(found.providerName + " · " + confidenceLabel(MatchConfidence.of(found.score)), style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1)
        }
    }
}

/**
 * [found] beside the file, field by field: what it adds checked, what would take from the file (fewer artists, a
 * shorter date, the same in other letters) offered unchecked, the rest shown; the cover with them when it has one.
 */
@Composable
private fun Comparison(model: TagEditorModel, found: LookupCandidate, onBack: () -> Unit, onApply: (Map<String, List<String>>, String?) -> Unit) {
    val colors = NagTheme.colors
    val labels = proposalLabels()
    val proposals = remember(found) { model.proposals(found) }
    var checked by remember(found) { mutableStateOf(proposals.filter { it.takenAtFirst }.map { it.key }.toSet()) }
    var withCover by remember(found) { mutableStateOf(found.artworkUrl != null) }
    CandidateLine(found, thumbnail = 56.dp)
    Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        proposals.forEach { proposal -> ProposalRow(proposal, labels[proposal.key] ?: proposal.key, proposal.key in checked) { on -> checked = if (on) checked + proposal.key else checked - proposal.key } }
        if (found.artworkUrl != null) {
            NagCheckbox(withCover, { withCover = it }, stringResource(R.string.nag_tag_lookup_cover), modifier = Modifier.testTag("tag-lookup-cover"))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        NagButton(stringResource(R.string.nag_tag_lookup_back), onBack, icon = NagIcons.ChevronLeft)
        NagButton(
            stringResource(R.string.nag_tag_lookup_apply),
            { onApply(proposals.filter { it.key in checked }.associate { it.key to it.proposed }, found.artworkUrl.takeIf { withCover }) },
            primary = true,
            enabled = checked.isNotEmpty() || withCover,
            modifier = Modifier.testTag("tag-lookup-apply"),
        )
    }
    Text(stringResource(R.string.nag_tag_lookup_note), style = NagTheme.type.labelSmall, color = colors.textTertiary)
}

/** What a found field is called: the editor's name for it; the totals and MusicBrainz's identifiers theirs; another its key. */
@Composable
private fun proposalLabels(): Map<String, String> {
    val fields = fieldLabels()
    val trackTotal = stringResource(R.string.nag_tag_track_total)
    val discTotal = stringResource(R.string.nag_tag_disc_total)
    val identifiers = mapOf(
        MB_RECORDING to stringResource(R.string.nag_tag_mb_recording),
        MB_RELEASE_TRACK to stringResource(R.string.nag_tag_mb_release_track),
        MB_RELEASE to stringResource(R.string.nag_tag_mb_release),
        MB_RELEASE_GROUP to stringResource(R.string.nag_tag_mb_release_group),
        MB_ARTIST to stringResource(R.string.nag_tag_mb_artist),
    )
    return fields + TagField.TrackTotalKeys.associateWith { trackTotal } + TagField.DiscTotalKeys.associateWith { discTotal } + identifiers
}

private const val MB_RECORDING = "MUSICBRAINZ_TRACKID"
private const val MB_RELEASE_TRACK = "MUSICBRAINZ_RELEASETRACKID"
private const val MB_RELEASE = "MUSICBRAINZ_ALBUMID"
private const val MB_RELEASE_GROUP = "MUSICBRAINZ_RELEASEGROUPID"
private const val MB_ARTIST = "MUSICBRAINZ_ARTISTID"

/** A field found: the file's value, the one found; checked to be taken. One found as the file has it is said only. */
@Composable
private fun ProposalRow(proposal: FieldProposal, label: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    val colors = NagTheme.colors
    val now = proposal.current.joinToString(", ").ifEmpty { "—" }
    val found = proposal.proposed.joinToString(", ")
    if (!proposal.differs) {
        Text("$label: $found", style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp))
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        NagCheckbox(checked, onCheck, label, modifier = Modifier.testTag("tag-lookup-field-${proposal.key}"))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(now, style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(found, style = NagTheme.type.body, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ------------------------------------------------------------------ covers

/**
 * "Kapak ara": the covers the catalogues have for the song (or the songs, as they will be), the one to recommend first:
 * each with its size, where it was found, and what speaks for it or against it. One chosen is downloaded and becomes
 * the cover to save.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverFinderDialog(model: TagEditorModel, onDismiss: () -> Unit) {
    val tools = rememberTagLookups()
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<CoverSearchProgress>(CoverSearchProgress.Asking) }
    var outcome by remember { mutableStateOf<CoverSearchOutcome?>(null) }
    var downloading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        outcome = tools.covers(model.coverRequest()) { progress = it }
    }
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) {
            DialogCard(stringResource(R.string.nag_tag_covers_title), Modifier.testTag("tag-covers-finder"), cardWidth = 640.dp) {
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    when (val done = outcome) {
                        null -> when (val now = progress) {
                            CoverSearchProgress.Asking -> StatusLine(stringResource(R.string.nag_tag_covers_asking), progress = null)
                            is CoverSearchProgress.Looking -> StatusLine(
                                stringResource(R.string.nag_tag_covers_looking, now.seen, now.of),
                                progress = if (now.of > 0) now.seen.toFloat() / now.of else null,
                            )
                        }
                        is CoverSearchOutcome.Results -> {
                            if (done.suggestions.isEmpty()) StatusLine(stringResource(R.string.nag_tag_covers_none), progress = -2f)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                done.suggestions.forEachIndexed { index, suggestion ->
                                    CoverTile(suggestion, Modifier.testTag("tag-cover-suggestion-$index")) {
                                        downloading = true
                                        model.chooseCover { tools.downloader.download(suggestion.url)?.let { it.bytes to it.mimeType } }
                                        onDismiss()
                                    }
                                }
                            }
                            if (done.failedProviders.isNotEmpty()) {
                                Text(stringResource(R.string.nag_tag_lookup_partly, done.failedProviders.joinToString(", ")), style = NagTheme.type.labelSmall, color = NagTheme.colors.warning)
                            }
                        }
                        CoverSearchOutcome.NoProviders -> StatusLine(stringResource(R.string.nag_tag_lookup_no_catalogues), progress = -2f)
                        CoverSearchOutcome.Offline -> StatusLine(stringResource(R.string.nag_tag_lookup_offline), progress = -2f)
                        CoverSearchOutcome.RateLimited -> StatusLine(stringResource(R.string.nag_tag_lookup_busy), progress = -2f)
                        CoverSearchOutcome.Failed -> StatusLine(stringResource(R.string.nag_tag_lookup_failed), progress = -2f)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    NagButton(stringResource(R.string.nag_tag_close), onDismiss, enabled = !downloading)
                }
            }
        }
    }
}

/** A cover found: its picture, its size, where it is from, what speaks for or against it. */
@Composable
private fun CoverTile(suggestion: CoverSuggestion, modifier: Modifier, onChoose: () -> Unit) {
    val colors = NagTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(NagTheme.dimens.radiusControl)
    Column(
        modifier
            .width(140.dp)
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .nagClickable(interaction, onChoose)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Thumbnail(suggestion.thumbnailUrl ?: suggestion.url, 128.dp)
        suggestion.dimensions?.let { Text(stringResource(R.string.nag_tag_cover_size, it.width, it.height), style = NagTheme.type.mono, color = colors.text) }
        Text(suggestion.sources.joinToString(" · "), style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val reasons = suggestion.reasons.map { it to reasonLabel(it) }
        reasons.filter { !it.first.isWarning }.takeIf { it.isNotEmpty() }?.let { good ->
            Text(good.joinToString(", ") { it.second }, style = NagTheme.type.labelSmall, color = colors.success, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        reasons.filter { it.first.isWarning }.takeIf { it.isNotEmpty() }?.let { bad ->
            Text(bad.joinToString(", ") { it.second }, style = NagTheme.type.labelSmall, color = colors.warning, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun reasonLabel(reason: CoverReason): String = stringResource(
    when (reason) {
        CoverReason.SameRelease -> R.string.nag_tag_reason_same_release
        CoverReason.SameRecording -> R.string.nag_tag_reason_same_recording
        CoverReason.AlbumMatches -> R.string.nag_tag_reason_album
        CoverReason.ArtistMatches -> R.string.nag_tag_reason_artist
        CoverReason.TitleMatches -> R.string.nag_tag_reason_title
        CoverReason.YearMatches -> R.string.nag_tag_reason_year
        CoverReason.SeveralSources -> R.string.nag_tag_reason_sources
        CoverReason.SameAsCurrent -> R.string.nag_tag_reason_current
        CoverReason.LargerThanCurrent -> R.string.nag_tag_reason_larger
        CoverReason.HighResolution -> R.string.nag_tag_reason_high
        CoverReason.AlbumDiffers -> R.string.nag_tag_reason_other_album
        CoverReason.VariousArtists -> R.string.nag_tag_reason_compilation
        CoverReason.LowResolution -> R.string.nag_tag_reason_low
        CoverReason.NotSquare -> R.string.nag_tag_reason_not_square
    },
)

// ------------------------------------------------------------------ several files: their album

/**
 * "Albüm ara": the releases the songs may be from, the album first, then songs spread over the batch (a search a
 * second); one chosen fills the fields it knows for every song and numbers the songs found on it, to be checked and
 * saved with the rest.
 */
@Composable
private fun AlbumLookupDialog(model: TagEditorModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val tools = rememberTagLookups()
    var progress by remember { mutableStateOf<AlbumSearchProgress?>(null) }
    var outcome by remember { mutableStateOf<AlbumSearchOutcome?>(null) }
    LaunchedEffect(Unit) {
        outcome = tools.albums(model.albumQuery()) { progress = it }
    }
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) {
            DialogCard(stringResource(R.string.nag_tag_album_title), Modifier.testTag("tag-album-lookup"), cardWidth = 560.dp) {
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    when (val done = outcome) {
                        null -> {
                            val now = progress
                            StatusLine(
                                if (now == null) stringResource(R.string.nag_tag_lookup_searching) else stringResource(R.string.nag_tag_album_searching, now.searched, now.of),
                                progress = now?.takeIf { it.of > 0 }?.let { it.searched.toFloat() / it.of },
                            )
                        }
                        is AlbumSearchOutcome.Results -> {
                            if (done.proposals.isEmpty()) StatusLine(stringResource(R.string.nag_tag_lookup_none), progress = -2f)
                            done.proposals.forEachIndexed { index, proposal ->
                                ChoiceRow({
                                    model.useAlbum(proposal)
                                    Notices.show(context, context.getString(R.string.nag_tag_album_applied, proposal.album))
                                    onDismiss()
                                }, Modifier.testTag("tag-album-result-$index")) { ProposalLine(proposal, model.loaded.size) }
                            }
                            if (done.failedProviders.isNotEmpty()) {
                                Text(stringResource(R.string.nag_tag_lookup_partly, done.failedProviders.joinToString(", ")), style = NagTheme.type.labelSmall, color = NagTheme.colors.warning)
                            }
                        }
                        AlbumSearchOutcome.NoProviders -> StatusLine(stringResource(R.string.nag_tag_lookup_no_catalogues), progress = -2f)
                        AlbumSearchOutcome.Offline -> StatusLine(stringResource(R.string.nag_tag_lookup_offline), progress = -2f)
                        AlbumSearchOutcome.RateLimited -> StatusLine(stringResource(R.string.nag_tag_lookup_busy), progress = -2f)
                        AlbumSearchOutcome.Failed -> StatusLine(stringResource(R.string.nag_tag_lookup_failed), progress = -2f)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    NagButton(stringResource(R.string.nag_tag_close), onDismiss)
                }
            }
        }
    }
}

/** A release found: its cover, title and artists, year and tracks, how many of the songs are on it, how well it suits. */
@Composable
private fun ProposalLine(proposal: AlbumProposal, songs: Int) {
    val colors = NagTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Thumbnail(proposal.artworkThumbnailUrl, 52.dp)
        Column(Modifier.weight(1f)) {
            Text(listOf(proposal.album, proposal.albumArtists.joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · "), style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = listOfNotNull(proposal.year, proposal.trackCount?.let { pluralStringResource(R.plurals.nag_tag_files, it, it) }).joinToString(" · ")
            if (line.isNotEmpty()) Text(line, style = NagTheme.type.labelSmall, color = colors.textSecondary)
            Text(
                stringResource(R.string.nag_tag_album_songs_found, proposal.songsFound, songs) + " · " + proposal.sources.joinToString(", ") + " · " + confidenceLabel(proposal.confidence),
                style = NagTheme.type.labelSmall,
                color = colors.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ------------------------------------------------------------------ several files: the batch's tools

/**
 * 1.3.0, the batch's tools (Metamatik's): the albums the files are on made one; the tracks numbered in their order;
 * tags read from the files' names; each file's garbled text put right; the numbers an album search found.
 */
@Composable
internal fun BatchTools(model: TagEditorModel) {
    val colors = NagTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("tag-tools")) {
        Text(
            stringResource(R.string.nag_tag_tools).uppercase(currentLocale()),
            style = NagTheme.type.overline,
            color = colors.textTertiary,
        )
        model.merge?.let { MergeCard(model, it) }
        val found = model.filePositions.count { !it.value.isEmpty }
        if (found > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pluralStringResource(R.plurals.nag_tag_positions_found, found, found), style = NagTheme.type.labelSmall, color = colors.accentText, modifier = Modifier.weight(1f))
                NagButton(stringResource(R.string.nag_tag_positions_reset), model::resetFilePositions)
            }
        }
        Numbering(model)
        PatternTool(model)
        if (model.garbledFiles > 0) RepairTool(model)
    }
}

/** The albums the files are on, and one made of them: its name and album artist chosen, the discs from the names. */
@Composable
private fun MergeCard(model: TagEditorModel, merge: app.nagmedar.tags.batch.AlbumMerge) {
    val colors = NagTheme.colors
    var title by remember(merge) { mutableStateOf(merge.title) }
    var artists by remember(merge) { mutableStateOf(merge.artists.joinToString("; ")) }
    var discs by remember(merge) { mutableStateOf(merge.discs.isNotEmpty()) }
    val shape = RoundedCornerShape(NagTheme.dimens.radiusControl)
    Column(
        Modifier.fillMaxWidth().border(1.dp, colors.border, shape).padding(12.dp).testTag("tag-merge"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.nag_tag_merge_title, merge.albums.size), style = NagTheme.type.bodyStrong, color = colors.text)
        Text(
            merge.albums.joinToString("\n") { album -> "· " + album.title + (album.artists.takeIf { it.isNotEmpty() }?.let { " — " + it.joinToString(", ") } ?: "") },
            style = NagTheme.type.labelSmall,
            color = colors.textSecondary,
        )
        NagTextField(title, { title = it }, stringResource(R.string.nag_tag_album), modifier = Modifier.testTag("tag-merge-album"))
        NagTextField(artists, { artists = it }, stringResource(R.string.nag_tag_album_artists), modifier = Modifier.testTag("tag-merge-artists"))
        if (merge.discs.isNotEmpty()) NagCheckbox(discs, { discs = it }, stringResource(R.string.nag_tag_merge_discs))
        NagButton(
            stringResource(R.string.nag_tag_merge_apply),
            { model.mergeAlbums(title, artists.split(';').map { it.trim() }, discs) },
            enabled = title.isNotBlank() && artists.isNotBlank(),
            modifier = Modifier.testTag("tag-merge-apply"),
        )
    }
}

/** The tracks numbered in the order they are listed, from a number chosen, with their total or without. */
@Composable
private fun Numbering(model: TagEditorModel) {
    val numbering = model.numbering
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NagSwitch(numbering != null, { on -> model.number(if (on) TrackNumbering() else null) }, stringResource(R.string.nag_tag_numbering), modifier = Modifier.testTag("tag-numbering"))
        if (numbering != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(120.dp)) {
                    NagTextField(
                        numbering.startAt.toString(),
                        { typed -> typed.trim().toIntOrNull()?.takeIf { it in 0..9999 }?.let { model.number(numbering.copy(startAt = it)) } },
                        stringResource(R.string.nag_tag_numbering_start),
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    )
                }
                NagCheckbox(numbering.writeTotal, { model.number(numbering.copy(writeTotal = it)) }, stringResource(R.string.nag_tag_numbering_total))
            }
        }
    }
}

/** Tags read from the files' names by a pattern, with how the first names read. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatternTool(model: TagEditorModel) {
    val colors = NagTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NagSwitch(model.usePattern, model::readNames, stringResource(R.string.nag_tag_pattern), modifier = Modifier.testTag("tag-pattern"))
        if (!model.usePattern) return@Column
        NagTextField(model.pattern, model::choosePattern, stringResource(R.string.nag_tag_pattern_field), error = !FileNamePattern(model.pattern).isValid, modifier = Modifier.testTag("tag-pattern-field"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FileNamePattern.Suggestions.forEach { suggestion -> NagButton(suggestion, { model.choosePattern(suggestion) }) }
        }
        val labels = fieldLabels()
        model.patternPreview.forEach { (name, tags) ->
            Text(
                name + " → " + (tags?.entries?.joinToString(" · ") { (key, values) -> (labels[key] ?: key) + ": " + values.joinToString(", ") } ?: stringResource(R.string.nag_tag_pattern_no_match)),
                style = NagTheme.type.labelSmall,
                color = if (tags == null) colors.warning else colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Each file's garbled text put right, with a few of the fields it changes. */
@Composable
private fun RepairTool(model: TagEditorModel) {
    val colors = NagTheme.colors
    val labels = fieldLabels()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NagSwitch(model.repairEach, model::fixGarbledEach, stringResource(R.string.nag_tag_repair), modifier = Modifier.testTag("tag-repair"))
        Text(pluralStringResource(R.plurals.nag_tag_repair_count, model.garbledFiles, model.garbledFiles), style = NagTheme.type.labelSmall, color = colors.textSecondary)
        model.garbledExamples.forEach { (key, before, after) ->
            Text((labels[key] ?: key) + ": " + before + " → " + after, style = NagTheme.type.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
