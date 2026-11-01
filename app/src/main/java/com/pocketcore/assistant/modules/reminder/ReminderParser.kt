package com.pocketcore.assistant.modules.reminder

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import java.util.regex.Pattern

data class ParsedReminder(
    val task: String,
    val triggerDateTime: LocalDateTime
)

/**
 * Natural language reminder parser using regex and java.time.
 * Strictly offline, zero dependencies, light footprint for Helio G81.
 *
 * Implements intelligent time inference:
 * 1. "after X minutes", "in X mins", "after 2 hours" -> adds relative time directly to today.
 * 2. "today at X" or "at X" -> prioritizes today.
 * 3. Meridian context awareness:
 *    - If user says a plain hour (e.g. "at 5" or "at 9") without specifying AM/PM:
 *      * If current time is afternoon/evening (PM), check if 5 PM / 9 PM is still in the future today. If so, choose PM today!
 *      * If that PM time has passed, or if current time is morning, compare AM and PM to schedule the soonest upcoming occurrence.
 */
object ReminderParser {

    private val MONTH_MAP = mapOf(
        "jan" to 1, "january" to 1,
        "feb" to 2, "february" to 2,
        "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4,
        "may" to 5,
        "jun" to 6, "june" to 6,
        "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8,
        "sep" to 9, "september" to 9,
        "oct" to 10, "october" to 10,
        "nov" to 11, "november" to 11,
        "dec" to 12, "december" to 12
    )

    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): ParsedReminder? {
        val cleanInput = input.trim()
        val lower = cleanInput.lowercase(Locale.ENGLISH)

        // Must relate to remind / reminder
        if (!lower.startsWith("remind") && !lower.startsWith("set a reminder") && !lower.startsWith("reminder")) {
            return null
        }

        // 1. RELATIVE TIME: "in X minutes", "after X minutes", "after 2 hours", "today after 2 minutes"
        // e.g.: "remind me to call Mom after 2 minutes", "remind me to check mail in 30 mins"
        val relativePattern = Pattern.compile(
            "^(?:remind\\s+(?:me\\s+)?(?:to\\s+)?|reminder\\s+|set\\s+a\\s+reminder\\s+(?:to\\s+)?)(.+?)(?:\\s+at\\s+today|\\s+today)?(?:\\s+after|\\s+in)\\s+(\\d+)\\s*(minute|minutes|min|mins|hour|hours|hr|hrs|second|seconds|sec|secs)\\b",
            Pattern.CASE_INSENSITIVE
        )
        val relMatcher = relativePattern.matcher(cleanInput)
        if (relMatcher.find()) {
            val task = relMatcher.group(1)?.trim() ?: return null
            val amount = relMatcher.group(2)?.toLongOrNull() ?: return null
            val unit = relMatcher.group(3)?.lowercase(Locale.ENGLISH) ?: return null

            val targetTime = when {
                unit.startsWith("h") -> now.plusHours(amount)
                unit.startsWith("s") -> now.plusSeconds(amount)
                else -> now.plusMinutes(amount)
            }
            return ParsedReminder(task = cleanTask(task), triggerDateTime = targetTime)
        }

        // 2. SPECIFIC DATE + TIME (e.g., "remind me to call Mom on Oct 24 at 10am")
        val dateTimePattern = Pattern.compile(
            "^(?:remind\\s+(?:me\\s+)?(?:to\\s+)?|reminder\\s+)(.+?)\\s+(?:on|for)\\s+([a-zA-Z]+|\\d{1,2})[\\s/\\-]+(\\d{1,2}|[a-zA-Z]+)(?:\\s+(?:at\\s+)?(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?))?\\b",
            Pattern.CASE_INSENSITIVE
        )
        val dtMatcher = dateTimePattern.matcher(cleanInput)
        if (dtMatcher.find()) {
            val task = dtMatcher.group(1)?.trim() ?: return null
            val part1 = dtMatcher.group(2)?.lowercase(Locale.ENGLISH) ?: ""
            val part2 = dtMatcher.group(3)?.lowercase(Locale.ENGLISH) ?: ""
            val timePart = dtMatcher.group(4)

            var month = MONTH_MAP[part1]
            var day = part2.toIntOrNull()

            if (month == null) {
                month = MONTH_MAP[part2]
                day = part1.toIntOrNull()
            }

            if (month != null && day != null) {
                var year = now.year
                var date = try {
                    LocalDate.of(year, month, day)
                } catch (e: Exception) {
                    null
                }
                if (date != null) {
                    if (date.isBefore(now.toLocalDate())) {
                        date = date.plusYears(1)
                    }
                    val parsedTime = if (!timePart.isNullOrBlank()) {
                        parseTimeWithContext(timePart, now.toLocalTime()) ?: LocalTime.of(9, 0)
                    } else {
                        LocalTime.of(9, 0)
                    }
                    return ParsedReminder(task = cleanTask(task), triggerDateTime = LocalDateTime.of(date, parsedTime))
                }
            }
        }

        // 3. "TOMORROW AT [TIME]"
        // e.g., "remind me to buy groceries tomorrow at 5pm"
        val tomorrowPattern = Pattern.compile(
            "^(?:remind\\s+(?:me\\s+)?(?:to\\s+)?|reminder\\s+)(.+?)\\s+(?:tomorrow|tmrw)(?:\\s+(?:at\\s+)?(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?))?",
            Pattern.CASE_INSENSITIVE
        )
        val tmMatcher = tomorrowPattern.matcher(cleanInput)
        if (tmMatcher.find()) {
            val task = tmMatcher.group(1)?.trim() ?: return null
            val timePart = tmMatcher.group(2)
            val date = now.toLocalDate().plusDays(1)
            val parsedTime = if (!timePart.isNullOrBlank()) {
                parseTimeWithContext(timePart, now.toLocalTime()) ?: LocalTime.of(9, 0)
            } else {
                LocalTime.of(9, 0)
            }
            return ParsedReminder(task = cleanTask(task), triggerDateTime = LocalDateTime.of(date, parsedTime))
        }

        // 4. "TODAY AT [TIME]" or "AT TODAY [TIME]"
        // e.g., "remind me to call Mom today at 6pm" or "remind me to check mail at today 5"
        val todayPattern = Pattern.compile(
            "^(?:remind\\s+(?:me\\s+)?(?:to\\s+)?|reminder\\s+)(.+?)\\s+(?:(?:today\\s+at|at\\s+today|at)\\s+)(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?)\\b",
            Pattern.CASE_INSENSITIVE
        )
        val todayMatcher = todayPattern.matcher(cleanInput)
        if (todayMatcher.find()) {
            val task = todayMatcher.group(1)?.trim() ?: return null
            val timePart = todayMatcher.group(2)?.trim() ?: return null
            val parsedTime = parseTimeWithContext(timePart, now.toLocalTime())
            if (parsedTime != null) {
                var triggerDateTime = LocalDateTime.of(now.toLocalDate(), parsedTime)
                // If the time already passed today, only push to tomorrow if user didn't explicitly say "today"
                if (triggerDateTime.isBefore(now)) {
                    if (!lower.contains("today")) {
                        triggerDateTime = triggerDateTime.plusDays(1)
                    }
                }
                return ParsedReminder(task = cleanTask(task), triggerDateTime = triggerDateTime)
            }
        }

        // 5. GENERIC FALLBACK: "remind me to [task]" without time specified -> default to 1 hour later
        val genericPattern = Pattern.compile(
            "^(?:remind\\s+(?:me\\s+)?(?:to\\s+)?|reminder\\s+)(.+)$",
            Pattern.CASE_INSENSITIVE
        )
        val genMatcher = genericPattern.matcher(cleanInput)
        if (genMatcher.find()) {
            val task = genMatcher.group(1)?.trim() ?: return null
            return ParsedReminder(
                task = cleanTask(task),
                triggerDateTime = now.plusHours(1)
            )
        }

        return null
    }

    private fun cleanTask(raw: String): String {
        return raw.trim()
            .replaceFirst(Regex("^(?:me\\s+)?to\\s+", RegexOption.IGNORE_CASE), "")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    /**
     * Intelligently parses time string:
     * - If explicit AM/PM is provided, respects it.
     * - If plain hour is provided (e.g. "5" or "5:30"):
     *   * Checks if current time is PM and whether PM version is in the future today.
     *   * If current time is morning (AM), checks if AM is in future; otherwise checks PM.
     *   * Always prioritizes the soonest upcoming hour today!
     */
    fun parseTimeWithContext(raw: String, currentTime: LocalTime = LocalTime.now()): LocalTime? {
        val cleaned = raw.trim().lowercase(Locale.ENGLISH).replace(" ", "")

        // 1. Explicit 12-hour format with AM/PM (e.g. "9pm", "9:30pm", "10am")
        val amPmPattern = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?(am|pm)")
        val matcher = amPmPattern.matcher(cleaned)
        if (matcher.matches()) {
            var hour = matcher.group(1)?.toIntOrNull() ?: return null
            val minute = matcher.group(2)?.toIntOrNull() ?: 0
            val meridian = matcher.group(3)

            if (meridian == "pm" && hour < 12) {
                hour += 12
            } else if (meridian == "am" && hour == 12) {
                hour = 0
            }
            return if (hour in 0..23 && minute in 0..59) {
                LocalTime.of(hour, minute)
            } else null
        }

        // 2. 24-hour format with colon (e.g. "21:30" or "09:00" or "5:30")
        val colonPattern = Pattern.compile("(\\d{1,2}):(\\d{2})")
        val colonMatcher = colonPattern.matcher(cleaned)
        if (colonMatcher.matches()) {
            var hour = colonMatcher.group(1)?.toIntOrNull() ?: return null
            val minute = colonMatcher.group(2)?.toIntOrNull() ?: return null

            // If hour is 1..12 and no AM/PM was specified:
            // Apply context inference based on current time
            if (hour in 1..12) {
                val candidateAm = if (hour == 12) LocalTime.of(0, minute) else LocalTime.of(hour, minute)
                val candidatePm = if (hour == 12) LocalTime.of(12, minute) else LocalTime.of(hour + 12, minute)

                // If currently afternoon (>= 12:00)
                return if (currentTime.hour >= 12) {
                    if (candidatePm.isAfter(currentTime)) candidatePm else candidateAm
                } else {
                    if (candidateAm.isAfter(currentTime)) candidateAm else candidatePm
                }
            }

            return if (hour in 0..23 && minute in 0..59) {
                LocalTime.of(hour, minute)
            } else null
        }

        // 3. Plain number (e.g. "5", "9", "2", "11")
        val plainHour = cleaned.toIntOrNull()
        if (plainHour != null && plainHour in 1..24) {
            if (plainHour > 12 && plainHour <= 23) {
                return LocalTime.of(plainHour, 0)
            }

            val hour12 = plainHour
            val candidateAm = if (hour12 == 12) LocalTime.of(0, 0) else LocalTime.of(hour12, 0)
            val candidatePm = if (hour12 == 12) LocalTime.of(12, 0) else LocalTime.of(hour12 + 12, 0)

            // If user triggers at PM:
            return if (currentTime.hour >= 12) {
                // If PM version is still in the future today, schedule PM today!
                // Otherwise if already passed, fall back to AM
                if (candidatePm.isAfter(currentTime)) candidatePm else candidateAm
            } else {
                // If in AM: if AM is in the future today, pick AM; otherwise pick PM
                if (candidateAm.isAfter(currentTime)) candidateAm else candidatePm
            }
        }

        return null
    }
}
