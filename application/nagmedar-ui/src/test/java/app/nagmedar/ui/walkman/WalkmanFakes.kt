package app.nagmedar.ui.walkman

import android.content.Intent
import android.net.Uri
import app.nagmedar.walkman.index.SyncIndex
import app.nagmedar.walkman.model.AudioFormat
import app.nagmedar.walkman.model.AudioTags
import app.nagmedar.walkman.model.BindResult
import app.nagmedar.walkman.model.CopyReason
import app.nagmedar.walkman.model.FileEntry
import app.nagmedar.walkman.model.RemovableVolume
import app.nagmedar.walkman.model.SkipReason
import app.nagmedar.walkman.model.SkippedItem
import app.nagmedar.walkman.model.SourceHandle
import app.nagmedar.walkman.model.SyncItem
import app.nagmedar.walkman.model.SyncPlan
import app.nagmedar.walkman.model.SyncState
import app.nagmedar.walkman.model.TargetFileSystem
import app.nagmedar.walkman.model.VolumeId
import app.nagmedar.walkman.model.VolumePair
import app.nagmedar.walkman.model.VolumeRole
import app.nagmedar.walkman.model.VolumeStatus
import app.nagmedar.walkman.sync.SyncController
import app.nagmedar.walkman.workflow.VolumeSnapshot
import kotlinx.coroutines.flow.MutableStateFlow

/** 1.5.0: the one bridge, as the page sees it: states set by the test, what the page asked for kept. */
class FakeWalkmanController : SyncController {
    override val state = MutableStateFlow<SyncState>(SyncState.AwaitingVolumes(VolumeStatus.Unbound, VolumeStatus.Unbound))
    override val volumes = MutableStateFlow(VolumeSnapshot(VolumeStatus.Unbound, VolumeStatus.Unbound))
    override val mountedVolumes = MutableStateFlow<List<RemovableVolume>>(emptyList())
    var scans = 0
    val transfers = mutableListOf<Set<String>>()

    override fun scan(): Boolean {
        scans++
        return true
    }

    override fun startTransfer(selection: Set<String>): Boolean {
        transfers += selection
        return true
    }

    override fun pause() = true
    override fun resume() = true
    override fun cancel() = true
    override fun dismiss(): Boolean {
        state.value = SyncState.Idle(null)
        return true
    }

    override fun createPickerIntent(role: VolumeRole, volume: VolumeId?) = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
    override suspend fun bindVolume(role: VolumeRole, treeUri: Uri, expected: VolumeId?): BindResult = error("not in these tests")
    override suspend fun unbindVolume(role: VolumeRole) = Unit
    override fun refreshVolumes(afterUsbAttach: Boolean) = Unit
}

/** The tablet's card and the Walkman's, as the samples have them. */
val sampleSource: VolumeId = VolumeId.require("1A2B-3C4D")
val sampleTarget: VolumeId = VolumeId.require("5E6F-7A8B")

private fun sourceFile(path: String, size: Long) = FileEntry(SourceHandle("$sampleSource:$path"), path, path.substringAfterLast('/'), size, 1_700_000_000_000L)

/** A scan's plan: two new files from the downloads, filed by their tags; one passed over, already copied. */
fun sampleWalkmanPlan(): SyncPlan {
    val pair = VolumePair(sampleSource, sampleTarget, "$sampleSource:Music", "$sampleTarget:MUSIC")
    val garip = SyncItem(
        "Download/Garip.flac", sourceFile("Download/Garip.flac", 31_000_000), "MUSIC/Moğollar/Moğollar/02 - Garip.flac", CopyReason.NEW_TAG_PLACED, AudioFormat.FLAC,
        AudioTags(artist = "Moğollar", album = "Moğollar", title = "Garip", trackNumber = 2, durationMs = 245_000),
    )
    val halic = SyncItem(
        "Download/Haliç.mp3", sourceFile("Download/Haliç.mp3", 8_000_000), "MUSIC/Moğollar/Moğollar/03 - Haliç.mp3", CopyReason.NEW_TAG_PLACED, AudioFormat.MP3,
        AudioTags(artist = "Moğollar", album = "Moğollar", title = "Haliç", trackNumber = 3),
    )
    val known = SkippedItem(sourceFile("Music/Bir Sana Bir Bana.flac", 29_000_000), SkipReason.ALREADY_SYNCED)
    val index = SyncIndex(SyncIndex.CURRENT_SCHEMA, sampleTarget, sampleSource, 0, 0, emptyList(), emptyList())
    return SyncPlan(pair, 1_700_000_000_000L, listOf(garip, halic), listOf(known), index, TargetFileSystem.EXFAT, 64_000_000_000L)
}
