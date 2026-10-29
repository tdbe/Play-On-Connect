package com.tdbe.playonconnect.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Parses time-event specs of the form:
 *   "hh:mm:ss repeat:Mo,Tu,We,Th,Fr,Sa,Su"
 * The "repeat:" part is optional; without it the event does not repeat.
 */
object TimeSpec {

    // Weekday indices: Monday = 1 .. Sunday = 7.
    private val DAY_TOKENS = mapOf(
        "mo" to Calendar.MONDAY,
        "tu" to Calendar.TUESDAY,
        "we" to Calendar.WEDNESDAY,
        "th" to Calendar.THURSDAY,
        "fr" to Calendar.FRIDAY,
        "sa" to Calendar.SATURDAY,
        "su" to Calendar.SUNDAY,
    )

    data class Parsed(val secondsOfDay: Int, val repeatDays: Set<Int>?)

    private val TIME_REGEX = Regex("""^(\d{1,2}):(\d{2}):(\d{2})$""")

    fun parse(spec: String): Parsed? {
        val normalized = spec.lowercase().trim()
        if (normalized.isEmpty()) return null
        val repeatIndex = normalized.indexOf("repeat:")
        val timePart = (if (repeatIndex == -1) normalized else normalized.substring(0, repeatIndex)).trim()
        val match = TIME_REGEX.matchEntire(timePart) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        val second = match.groupValues[3].toIntOrNull() ?: return null
        if (hour > 23 || minute > 59 || second > 59) return null

        var repeatDays: Set<Int>? = null
        if (repeatIndex != -1) {
            val daysPart = normalized.substring(repeatIndex + "repeat:".length)
            val tokens = daysPart.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isNotEmpty()) {
                val days = mutableSetOf<Int>()
                for (token in tokens) {
                    val day = DAY_TOKENS[token] ?: return null
                    days.add(day)
                }
                repeatDays = days
            }
        }
        return Parsed(hour * 3600 + minute * 60 + second, repeatDays)
    }

    fun repeats(spec: String): Boolean = parse(spec)?.repeatDays != null

    /** Next occurrence (epoch millis) strictly after [fromMs], or null if none within a week. */
    fun nextFireMs(parsed: Parsed, fromMs: Long = System.currentTimeMillis()): Long? {
        for (offsetDays in 0..7) {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = fromMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, offsetDays)
            }
            val fireAt = calendar.timeInMillis + parsed.secondsOfDay * 1000L
            if (fireAt <= fromMs) continue
            if (parsed.repeatDays == null || weekdayIndexMonFirst(calendar) in parsed.repeatDays) {
                return fireAt
            }
        }
        return null
    }

    fun defaultSpec(now: Date = Date()): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(now) + " repeat:Mo,Tu,We,Th,Fr,Sa,Su"

    private fun weekdayIndexMonFirst(calendar: Calendar): Int =
        ((calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
}
