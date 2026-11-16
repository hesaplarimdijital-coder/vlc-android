/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.2.0: from Metamatik (core/domain/src/main/kotlin/com/bobbyesp/metadator/core/domain/text/TextRepairs.kt), a fork of Metadator, under the AGPL-3.0.
 */
package app.nagmedar.tags.text

import app.nagmedar.tags.FieldKind
import app.nagmedar.tags.TagField
import app.nagmedar.tags.TagMap

/** The keys whose text is looked at for garbling: every field of text the editor names. */
val TextFieldKeys: List<String> =
    TagField.entries
        .filter { it.kind == FieldKind.Text || it.kind == FieldKind.LongText }
        .map { it.key }

/**
 * Whether [text] looks garbled, repairable or not (1.2.0: what the library's lists ask of the fields
 * a song is listed by, title, artist, album and album artist; Metamatik asked it of its own tracks).
 */
fun looksGarbled(text: String?): Boolean = text != null && GarbledText.inspect(text) != null

/**
 * What to write where these tags hold text garbled in a way the text itself can undo: by key, every
 * value of the key, each garbled one repaired. Empty when there is nothing to repair. The editor
 * offers it as changes to save, and it is what a batch writes to many files at once.
 */
fun TagMap.textRepairs(keys: Collection<String> = TextFieldKeys): Map<String, List<String>> =
    buildMap {
        for (key in keys) {
            val values = this@textRepairs[key]
            val repaired = values.map { GarbledText.repair(it) ?: it }
            if (repaired != values) put(key, repaired)
        }
    }
