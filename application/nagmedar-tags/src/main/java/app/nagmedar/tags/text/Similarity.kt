/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.3.0: from Metamatik (core/common/src/main/kotlin/com/bobbyesp/metadator/core/common/Text.kt), a fork of Metadator, under the AGPL-3.0.
 */
package app.nagmedar.tags.text

import java.text.Normalizer
import java.util.Locale

/**
 * Lower case, without accents or punctuation, with single spaces: the form two titles are compared
 * in. "Beyoncé – Halo (Remastered)" and "beyonce halo remastered" are the same here.
 */
fun String.normalizedForComparison(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .lowercase(Locale.ROOT)
        .replace(NON_ALPHANUMERIC, " ")
        .trim()
        .replace(WHITESPACE, " ")

private val DIACRITICS = Regex("\\p{Mn}+")
private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
private val WHITESPACE = Regex("\\s+")

/** Similarity between 0 (nothing in common) and 1 (equal), on normalized text. */
fun similarity(a: String, b: String): Double {
    val x = a.normalizedForComparison()
    val y = b.normalizedForComparison()
    if (x.isEmpty() && y.isEmpty()) return 1.0
    if (x.isEmpty() || y.isEmpty()) return 0.0
    if (x == y) return 1.0
    val distance = levenshtein(x, y)
    return 1.0 - distance.toDouble() / maxOf(x.length, y.length)
}

private fun levenshtein(a: String, b: String): Int {
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[b.length]
}
