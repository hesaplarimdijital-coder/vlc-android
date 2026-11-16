/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.2.0: from Metamatik (core/domain/src/main/kotlin/com/bobbyesp/metadator/core/domain/editor/MultiValues.kt), a fork of Metadator, under the AGPL-3.0.
 */
package app.nagmedar.tags.editor

import app.nagmedar.tags.TagChanges
import app.nagmedar.tags.TagField

/** How a field with several values (artists, genres) is written: kept apart, or joined into one. */
enum class MultiValueMode {
    Separate,
    Joined,
}

/**
 * Applies the user's choice for fields with several values just before writing: kept apart, or
 * joined into one for players that only read the first value. Only touches fields the user changed:
 * a file's existing values are never rewritten in another style behind their back.
 */
fun TagChanges.withMultiValueMode(mode: MultiValueMode, separator: String): TagChanges {
    if (mode == MultiValueMode.Separate) return this
    return copy(
        fields =
            fields.mapValues { (key, values) ->
                if (TagField.forKey(key)?.multiValue == true && values.size > 1) {
                    listOf(values.joinToString(separator))
                } else values
            }
    )
}

/** Splits a typed value into several on [separator], for pasting "A; B" into a chip field. */
fun splitValues(text: String, separator: String): List<String> {
    val trimmed = separator.trim()
    if (trimmed.isEmpty()) return listOf(text.trim()).filter { it.isNotEmpty() }
    return text.split(trimmed).map { it.trim() }.filter { it.isNotEmpty() }
}
