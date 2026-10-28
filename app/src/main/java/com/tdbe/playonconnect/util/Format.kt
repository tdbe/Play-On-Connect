package com.tdbe.playonconnect.util

/** Keeps user input to a single decimal number (digits + at most one dot). */
fun sanitizeDecimalInput(raw: String): String {
    val filtered = raw.filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    return if (firstDot == -1) {
        filtered
    } else {
        filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
    }
}

/** "20" for whole numbers, "0.5" for fractions. */
fun formatMinutes(minutes: Double): String =
    if (minutes == minutes.toLong().toDouble()) minutes.toLong().toString() else {
        minutes.toString().trimEnd('0').trimEnd('.').ifEmpty { "0" }
    }
