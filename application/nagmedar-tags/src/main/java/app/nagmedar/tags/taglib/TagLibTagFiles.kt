/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.2.0: from Metamatik (tags/taglib/src/main/kotlin/com/bobbyesp/metadator/tags/taglib/TagLibTagFiles.kt), a fork of Metadator, under the AGPL-3.0.
 */
package app.nagmedar.tags.taglib

import app.nagmedar.tags.ContentRef
import app.nagmedar.tags.files.AccessMode
import app.nagmedar.tags.files.AudioFileOpener
import app.nagmedar.tags.files.OpenResult
import app.nagmedar.tags.files.OpenedAudioFile
import app.nagmedar.tags.AudioProperties
import app.nagmedar.tags.EmbeddedPicture
import app.nagmedar.tags.PictureType
import app.nagmedar.tags.TagMap
import app.nagmedar.tags.TagReadResult
import app.nagmedar.tags.TagReader
import app.nagmedar.tags.TagSnapshot
import app.nagmedar.tags.TagWriteResult
import app.nagmedar.tags.TagWriter
import com.kyant.taglib.AudioPropertiesReadStyle
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads and writes tags with TagLib, through file descriptors the [opener] hands out.
 *
 * Every TagLib call takes a fresh duplicate descriptor, because TagLib closes the one it is given.
 * Calls are blocking native code, so they run on the IO dispatcher.
 */
class TagLibTagFiles(
    private val opener: AudioFileOpener,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : TagReader, TagWriter {

    override suspend fun read(ref: ContentRef, includePictures: Boolean): TagReadResult =
        withContext(io) {
            when (val open = opener.open(ref, AccessMode.Read)) {
                OpenResult.NotFound -> TagReadResult.NotFound
                OpenResult.AccessDenied -> TagReadResult.AccessDenied
                is OpenResult.Failed -> TagReadResult.Failed(open.message)
                is OpenResult.Opened ->
                    open.file.use { file ->
                        guarded(onError = { TagReadResult.Failed(it) }) {
                            val metadata =
                                TagLib.getMetadata(file.detachDuplicateFd(), includePictures)
                                    ?: return@guarded TagReadResult.Unsupported
                            TagReadResult.Success(
                                TagSnapshot(
                                    tags = metadata.propertyMap.toTagMap(),
                                    pictures = metadata.pictures.map { it.toEmbeddedPicture() },
                                )
                            )
                        }
                    }
            }
        }

    override suspend fun readAudioProperties(ref: ContentRef): AudioProperties? =
        withContext(io) {
            val open =
                opener.open(ref, AccessMode.Read) as? OpenResult.Opened ?: return@withContext null
            open.file.use { file ->
                guarded(onError = { null }) {
                    TagLib.getAudioProperties(
                            file.detachDuplicateFd(),
                            AudioPropertiesReadStyle.Average,
                        )
                        ?.let {
                            AudioProperties(
                                durationMs = it.length.toLong(),
                                bitrateKbps = it.bitrate,
                                sampleRateHz = it.sampleRate,
                                channels = it.channels,
                            )
                        }
                }
            }
        }

    override suspend fun write(
        ref: ContentRef,
        tags: TagMap,
        pictures: List<EmbeddedPicture>?,
    ): TagWriteResult =
        withContext(io) {
            when (val open = opener.open(ref, AccessMode.ReadWrite)) {
                OpenResult.NotFound -> TagWriteResult.NotFound
                OpenResult.AccessDenied -> TagWriteResult.NeedsAccess
                is OpenResult.Failed -> TagWriteResult.Failed(open.message)
                is OpenResult.Opened -> open.file.use { file -> write(file, tags, pictures) }
            }
        }

    private fun write(
        file: OpenedAudioFile,
        tags: TagMap,
        pictures: List<EmbeddedPicture>?,
    ): TagWriteResult = writeOn(file::detachDuplicateFd, tags, pictures)

    /**
     * Nağmedar 1.2.0: [tags] and, unless null, [pictures] written on a file already open, [detachFd] handing TagLib a
     * descriptor of its own each time (TagLib closes it): Nağmedar's guarded write, which keeps a copy of the file
     * while it is written, calls it.
     */
    fun writeOn(detachFd: () -> Int, tags: TagMap, pictures: List<EmbeddedPicture>?): TagWriteResult =
        guarded(onError = { TagWriteResult.Failed(it) }) {
            val propertyMap = HashMap<String, Array<String>>()
            tags.toMap().forEach { (key, values) -> propertyMap[key] = values.toTypedArray() }
            if (!TagLib.savePropertyMap(detachFd(), propertyMap)) {
                return@guarded TagWriteResult.Failed("TagLib could not save the tags")
            }
            if (
                pictures != null &&
                    !TagLib.savePictures(
                        detachFd(),
                        pictures.map { it.toTagLibPicture() }.toTypedArray(),
                    )
            ) {
                return@guarded TagWriteResult.Failed("TagLib could not save the pictures")
            }
            TagWriteResult.Written
        }

    /** Native code throws whatever it likes; none of it may crash the app. */
    private inline fun <T> guarded(onError: (String) -> T, block: () -> T): T =
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            onError(error.message ?: error::class.java.simpleName)
        }
}

internal fun Map<String, Array<String>>.toTagMap(): TagMap =
    TagMap.of(mapValues { (_, values) -> values.toList() })

internal fun Picture.toEmbeddedPicture() =
    EmbeddedPicture(
        data = data,
        mimeType = mimeType.ifBlank { "image/jpeg" },
        description = description,
        type = PictureType.parse(pictureType),
    )

internal fun EmbeddedPicture.toTagLibPicture() =
    Picture(data = data, description = description, pictureType = type.label, mimeType = mimeType)
