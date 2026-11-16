/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.2.0: from Metamatik (core/model/src/main/kotlin/com/bobbyesp/metadator/core/model/ContentRef.kt), a fork of Metadator, under the AGPL-3.0.
 */
package app.nagmedar.tags

/**
 * Where an audio file is, as a URI string: in Nağmedar a `file://` address, as the library has its
 * songs, or a `content://` one another app handed over (1.2.0; Metamatik had only the latter). Never
 * `android.net.Uri`, so that the rules stay plain Kotlin.
 */
@JvmInline
value class ContentRef(val uri: String) {
    override fun toString(): String = uri
}
