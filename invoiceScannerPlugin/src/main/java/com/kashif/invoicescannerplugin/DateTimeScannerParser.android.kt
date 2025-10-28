package com.kashif.invoicescannerplugin

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

actual fun parseFlexibleDate(input: String): LocalDate? {
    val locale = Locale.getDefault()

    val patterns = listOf(
        "dd.MM.yyyy",
        "d.M.yyyy",
        "dd MMM yyyy",
        "MM/yyyy",
        "M/yyyy",
        "MM.yyyy",
        "M.yyyy",
        "MMM yyyy",
        "MMMM yyyy",
        "MMyyyy"
    )

    for (pattern in patterns) {
        try {
            val hasDay = pattern.contains("d") || pattern.contains("dd")

            val normalizedInput = if (!hasDay) {
                when (pattern) {
                    "MM/yyyy", "M/yyyy"      -> "01/$input"
                    "MM.yyyy", "M.yyyy"      -> "01.$input"
                    "MMM yyyy", "MMMM yyyy"  -> "01 $input"
                    "MMyyyy"                 -> "01$input"
                    else                     -> input
                }
            } else {
                input
            }

            val adjustedPattern = if (!hasDay) {
                when (pattern) {
                    "MM/yyyy", "M/yyyy"      -> "dd/MM/yyyy"
                    "MM.yyyy", "M.yyyy"      -> "dd.MM.yyyy"
                    "MMM yyyy", "MMMM yyyy"  -> "dd MMM yyyy"
                    "MMyyyy"                 -> "ddMMyyyy"
                    else                     -> pattern
                }
            } else {
                pattern
            }

            val adjustedFormatter = DateTimeFormatter.ofPattern(adjustedPattern, locale)
            val date = java.time.LocalDate.parse(normalizedInput, adjustedFormatter)
            if(date.year in 2010..2040) {
                return date.toKotlinLocalDate()
            }
        } catch (_: DateTimeParseException) {
            // Try next pattern
        }
    }

    return null
}