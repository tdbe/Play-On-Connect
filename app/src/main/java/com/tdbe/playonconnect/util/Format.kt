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

/** Truncates with an ellipsis to at most [maxChars] total characters. */
fun clampName(name: String, maxChars: Int): String {
    if (name.length <= maxChars) return name
    val keep = (maxChars - 3).coerceAtLeast(1)
    return name.take(keep).trimEnd() + "..."
}

/**
 * Comma-separated display list of file names: at most 4 names shown, each
 * clamped by the list size (256/128/85/64), with a trailing ", ..." when
 * more than 4 files exist.
 */
fun clampedFileNames(names: List<String>): String {
    if (names.isEmpty()) return ""
    val maxChars = when {
        names.size == 1 -> 256
        names.size == 2 -> 128
        names.size == 3 -> 85
        else -> 64
    }
    val shown = names.take(4).map { clampName(it, maxChars) }
    return shown.joinToString(", ") + if (names.size > 4) ", ..." else ""
}

/** Truncates a name to [limit] chars, marking with "...". */
fun truncateName(name: String, limit: Int): String =
    if (name.length <= limit) name else name.take(limit) + "..."

/**
 * Display text for a picked file list: single name, or "<n> files selected"
 * followed by up to 4 comma-separated clamped names.
 */
fun formatFileList(names: List<String>): String = when {
    names.isEmpty() -> ""
    names.size == 1 -> truncateName(names[0], 256)
    else -> {
        val perLimit = when (names.size) {
            2 -> 128
            3 -> 85
            else -> 64
        }
        val shown = names.take(4).joinToString(", ") { truncateName(it, perLimit) }
        val suffix = if (names.size > 4) ", ..." else ""
        "${names.size} files selected: $shown$suffix"
    }
}
