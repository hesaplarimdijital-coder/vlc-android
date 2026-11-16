/*
 * Nağmedar 1.5.0: from Walkman Sync (app/src/main/kotlin/com/walkmansync/ui/UiText.kt), the owner's own app.
 */
package app.nagmedar.ui.walkman

import android.content.res.Resources
import app.nagmedar.ui.R
import app.nagmedar.walkman.model.BindRejection
import app.nagmedar.walkman.model.CopyReason
import app.nagmedar.walkman.model.PlanWarning
import app.nagmedar.walkman.model.ScanPhase
import app.nagmedar.walkman.model.ScanProgress
import app.nagmedar.walkman.model.SkipReason
import app.nagmedar.walkman.model.SyncError
import app.nagmedar.walkman.model.TargetFileSystem
import app.nagmedar.walkman.model.TransferOutcome
import app.nagmedar.walkman.model.VolumeId
import app.nagmedar.walkman.model.VolumeRole

// The model's states in the interface's words (strings.xml's nag_walkman_*); a SyncError's message is for the log
// only, and is not used here.

fun Resources.roleName(role: VolumeRole): String =
    getString(if (role == VolumeRole.SOURCE) R.string.nag_walkman_role_source else R.string.nag_walkman_role_target)

private fun Resources.volumeText(volume: VolumeId?): String = volume?.toString() ?: getString(R.string.nag_walkman_unregistered)

fun Resources.errorText(error: SyncError): String = when (error) {
    is SyncError.VolumeMissing -> getString(R.string.nag_walkman_err_volume_missing, roleName(error.role), volumeText(error.volume))
    is SyncError.VolumeMismatch -> getString(
        R.string.nag_walkman_err_volume_mismatch,
        roleName(error.role),
        volumeText(error.expected),
        error.actual ?: getString(R.string.nag_walkman_unknown),
    )
    is SyncError.PermissionLost -> getString(R.string.nag_walkman_err_permission_lost, roleName(error.role))
    is SyncError.VolumeReadOnly -> getString(R.string.nag_walkman_err_volume_read_only, roleName(error.role), error.volume.toString())
    is SyncError.InsufficientSpace -> getString(
        R.string.nag_walkman_err_insufficient_space,
        formatBytes(error.requiredBytes),
        error.availableBytes?.let { formatBytes(it) } ?: getString(R.string.nag_walkman_unknown),
    )
    is SyncError.FileTooLargeForFat32 -> getString(R.string.nag_walkman_err_fat32_too_large, error.path, formatBytes(error.size))
    is SyncError.SizeMismatch -> getString(
        R.string.nag_walkman_err_size_mismatch,
        error.path,
        formatBytes(error.expected),
        formatBytes(error.actual),
    )
    is SyncError.TargetExists -> getString(R.string.nag_walkman_err_target_exists, error.path)
    is SyncError.SourceGone -> getString(R.string.nag_walkman_err_source_gone, error.path)
    is SyncError.IoFailure -> {
        val cause = error.causeMessage ?: getString(R.string.nag_walkman_unknown)
        if (error.path != null) getString(R.string.nag_walkman_err_io_path, error.path, cause) else getString(R.string.nag_walkman_err_io, cause)
    }
    is SyncError.IndexUnsupported -> getString(R.string.nag_walkman_err_index_unsupported, error.schemaVersion)
    is SyncError.IndexCorrupt -> getString(R.string.nag_walkman_err_index_corrupt, error.detail)
    SyncError.Cancelled -> getString(R.string.nag_walkman_err_cancelled)
    is SyncError.Unexpected -> getString(R.string.nag_walkman_err_unexpected, error.causeMessage ?: getString(R.string.nag_walkman_unknown))
}

fun Resources.skipReasonLabel(reason: SkipReason): String = getString(
    when (reason) {
        SkipReason.ALREADY_SYNCED -> R.string.nag_walkman_skip_already_synced
        SkipReason.DUPLICATE_CONTENT -> R.string.nag_walkman_skip_duplicate_content
        SkipReason.ALREADY_ON_TARGET -> R.string.nag_walkman_skip_already_on_target
        SkipReason.TARGET_PATH_OCCUPIED -> R.string.nag_walkman_skip_target_path_occupied
        SkipReason.CHANGED_SINCE_SYNC -> R.string.nag_walkman_skip_changed_since_sync
        SkipReason.REMOVED_FROM_TARGET -> R.string.nag_walkman_skip_removed_from_target
        SkipReason.DUPLICATE_IN_SOURCE -> R.string.nag_walkman_skip_duplicate_in_source
        SkipReason.TOO_LARGE_FOR_FAT32 -> R.string.nag_walkman_skip_too_large_for_fat32
        SkipReason.UNKNOWN_SIZE -> R.string.nag_walkman_skip_unknown_size
        SkipReason.EMPTY_FILE -> R.string.nag_walkman_skip_empty_file
        SkipReason.ORPHAN_AUXILIARY -> R.string.nag_walkman_skip_orphan_auxiliary
    },
)

fun Resources.skipReasonDescription(reason: SkipReason): String = getString(
    when (reason) {
        SkipReason.ALREADY_SYNCED -> R.string.nag_walkman_skip_desc_already_synced
        SkipReason.DUPLICATE_CONTENT -> R.string.nag_walkman_skip_desc_duplicate_content
        SkipReason.ALREADY_ON_TARGET -> R.string.nag_walkman_skip_desc_already_on_target
        SkipReason.TARGET_PATH_OCCUPIED -> R.string.nag_walkman_skip_desc_target_path_occupied
        SkipReason.CHANGED_SINCE_SYNC -> R.string.nag_walkman_skip_desc_changed_since_sync
        SkipReason.REMOVED_FROM_TARGET -> R.string.nag_walkman_skip_desc_removed_from_target
        SkipReason.DUPLICATE_IN_SOURCE -> R.string.nag_walkman_skip_desc_duplicate_in_source
        SkipReason.TOO_LARGE_FOR_FAT32 -> R.string.nag_walkman_skip_desc_too_large_for_fat32
        SkipReason.UNKNOWN_SIZE -> R.string.nag_walkman_skip_desc_unknown_size
        SkipReason.EMPTY_FILE -> R.string.nag_walkman_skip_desc_empty_file
        SkipReason.ORPHAN_AUXILIARY -> R.string.nag_walkman_skip_desc_orphan_auxiliary
    },
)

fun Resources.copyReasonLabel(reason: CopyReason): String = getString(
    when (reason) {
        CopyReason.NEW -> R.string.nag_walkman_copy_new
        CopyReason.NEW_TAG_PLACED -> R.string.nag_walkman_copy_new_tag_placed
        CopyReason.COMPANION -> R.string.nag_walkman_copy_companion
        CopyReason.FORCED -> R.string.nag_walkman_copy_forced
    },
)

fun Resources.bindRejectionText(reason: BindRejection): String = getString(
    when (reason) {
        BindRejection.NOT_REMOVABLE -> R.string.nag_walkman_bind_not_removable
        BindRejection.VOLUME_NOT_MOUNTED -> R.string.nag_walkman_bind_volume_not_mounted
        BindRejection.WRONG_VOLUME -> R.string.nag_walkman_bind_wrong_volume
        BindRejection.SAME_AS_OTHER_ROLE -> R.string.nag_walkman_bind_same_as_other_role
        BindRejection.DUPLICATE_UUID -> R.string.nag_walkman_bind_duplicate_uuid
        BindRejection.TARGET_INVALID_FOLDER -> R.string.nag_walkman_bind_target_invalid_folder
        BindRejection.TARGET_READ_ONLY -> R.string.nag_walkman_bind_target_read_only
        BindRejection.PERMISSION_NOT_GRANTED -> R.string.nag_walkman_bind_permission_not_granted
    },
)

fun Resources.planWarningText(warning: PlanWarning): String = getString(
    when (warning) {
        PlanWarning.FOREIGN_INDEX_IGNORED -> R.string.nag_walkman_warn_foreign_index
        PlanWarning.INDEX_RECOVERED_FROM_BACKUP -> R.string.nag_walkman_warn_index_recovered
        PlanWarning.INDEX_RESET_CORRUPT -> R.string.nag_walkman_warn_index_reset
        PlanWarning.INSUFFICIENT_SPACE_FOR_ALL -> R.string.nag_walkman_warn_insufficient_space
        PlanWarning.TARGET_FS_UNKNOWN -> R.string.nag_walkman_warn_fs_unknown
    },
)

fun Resources.scanPhaseText(progress: ScanProgress): String = when (progress.phase) {
    ScanPhase.LOADING_INDEX -> getString(R.string.nag_walkman_phase_loading_index)
    ScanPhase.LISTING_TARGET -> getString(R.string.nag_walkman_phase_listing_target)
    ScanPhase.CLEANING_STALE_PARTS -> getString(R.string.nag_walkman_phase_cleaning)
    ScanPhase.LISTING_SOURCE ->
        if (progress.itemsDone > 0) getString(R.string.nag_walkman_phase_listing_source_count, progress.itemsDone)
        else getString(R.string.nag_walkman_phase_listing_source)
    ScanPhase.READING_TAGS ->
        if (progress.itemsTotal > 0) getString(R.string.nag_walkman_phase_reading_tags_count, progress.itemsDone, progress.itemsTotal)
        else getString(R.string.nag_walkman_phase_reading_tags)
    ScanPhase.PLANNING -> getString(R.string.nag_walkman_phase_planning)
}

fun Resources.outcomeTitle(outcome: TransferOutcome): String = getString(
    when (outcome) {
        TransferOutcome.COMPLETED -> R.string.nag_walkman_outcome_completed
        TransferOutcome.COMPLETED_WITH_ERRORS -> R.string.nag_walkman_outcome_completed_with_errors
        TransferOutcome.CANCELLED -> R.string.nag_walkman_outcome_cancelled
        TransferOutcome.ABORTED -> R.string.nag_walkman_outcome_aborted
    },
)

fun Resources.fileSystemLabel(fileSystem: TargetFileSystem?): String = when (fileSystem) {
    TargetFileSystem.EXFAT -> "exFAT"
    TargetFileSystem.FAT32 -> "FAT32"
    TargetFileSystem.UNKNOWN, null -> getString(R.string.nag_walkman_unknown)
}

/** A count of files ("1 dosya", "12 dosya"). */
fun Resources.fileCount(count: Int): String = getQuantityString(R.plurals.nag_walkman_file_count, count, count)
