package com.kashif.invoicescannerplugin

import kotlinx.datetime.LocalDate

actual fun parseFlexibleDate(input: String): LocalDate? {
    val cleaned = input.trim()

    return try {
        when {
            cleaned.matches(Regex("""\d{2}/\d{4}""")) -> {
                // MM/yyyy → LocalDate(year, month, 1)
                val (month, year) = cleaned.split("/").map { it.toInt() }
                LocalDate(year, month, 1)
            }

            cleaned.matches(Regex("""\d{2}\.\d{2}\.\d{4}""")) -> {
                // dd.MM.yyyy
                val (day, month, year) = cleaned.split(".").map { it.toInt() }
                LocalDate(year, month, day)
            }

            cleaned.matches(Regex("""\d{4}-\d{2}-\d{2}""")) -> {
                // ISO 8601
                val (year, month, day) = cleaned.split("-").map { it.toInt() }
                LocalDate(year, month, day)
            }

            else -> null
        }
    } catch (e: Exception) {
        null
    }
}