/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.4.0: from Metamatik (core/domain/src/main/kotlin/com/bobbyesp/metadator/core/domain/quality/CheckLosslessUseCase.kt), a fork of Metadator, under the AGPL-3.0.
 */
package app.nagmedar.tags.quality

import app.nagmedar.tags.ContentRef
import app.nagmedar.tags.files.AccessMode
import app.nagmedar.tags.files.AudioFileOpener
import app.nagmedar.tags.files.OpenResult
import app.nagmedar.tags.files.OpenedAudioFile
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

sealed interface LosslessCheckResult {
    data class Checked(val report: LosslessReport) : LosslessCheckResult

    /**
     * Not a file whose audio is read here: a lossy format, or a lossless one other than FLAC and
     * WAV.
     */
    data object NotApplicable : LosslessCheckResult

    /** The file could not be opened, or none of its audio could be decoded. */
    data object Unreadable : LosslessCheckResult
}

/**
 * Says whether a lossless file's audio has been through a lossy encoder on its way there: an MP3
 * saved as FLAC is as large as a real one and sounds like the MP3.
 *
 * It decodes some twenty seconds of audio, taken from all along the song, and looks at its spectrum
 * ([LosslessAnalyzer]). Nothing is written, and nothing leaves the device.
 */
class CheckLosslessUseCase(
    private val opener: AudioFileOpener,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend operator fun invoke(ref: ContentRef): LosslessCheckResult =
        withContext(io) {
            when (val open = opener.open(ref, AccessMode.Read)) {
                is OpenResult.Opened ->
                    open.file.use { file ->
                        try {
                            check(file) { ensureActive() }
                        } catch (_: IOException) {
                            LosslessCheckResult.Unreadable
                        }
                    }
                else -> LosslessCheckResult.Unreadable
            }
        }

    /**
     * What the file's header says of its samples, in a moment, for the editor to show while the
     * check reads the audio: null for a file that is not FLAC or WAV, or cannot be opened.
     */
    suspend fun bitsPerSample(ref: ContentRef): Int? =
        withContext(io) {
            when (val open = opener.open(ref, AccessMode.Read)) {
                is OpenResult.Opened ->
                    open.file.use { file ->
                        try {
                            (FlacSource.open(file) ?: WavSource.open(file))?.bitsPerSample
                        } catch (_: IOException) {
                            null
                        }
                    }
                else -> null
            }
        }

    private fun check(file: OpenedAudioFile, checkpoint: () -> Unit): LosslessCheckResult {
        val source =
            FlacSource.open(file)
                ?: WavSource.open(file)
                ?: return LosslessCheckResult.NotApplicable
        val report = LosslessAnalyzer.analyze(source, checkpoint)
        return if (report == null) LosslessCheckResult.Unreadable
        else LosslessCheckResult.Checked(report)
    }
}
