/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.2.0: from Metamatik (tags/api/src/main/kotlin/com/bobbyesp/metadator/tags/api/TagSnapshot.kt), a fork of Metadator, under the AGPL-3.0.
 */
package app.nagmedar.tags

/** Everything that can be edited in a file at one moment: its tags and its pictures. */
data class TagSnapshot(val tags: TagMap, val pictures: List<EmbeddedPicture>) {
    val frontCover: EmbeddedPicture?
        get() = pictures.firstOrNull { it.type == PictureType.FrontCover } ?: pictures.firstOrNull()

    companion object {
        val Empty = TagSnapshot(TagMap.Empty, emptyList())
    }
}

/** The audio stream, read-only. */
data class AudioProperties(
    val durationMs: Long,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val channels: Int,
)
