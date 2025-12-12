package com.kashif.invoicescannerplugin

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

data class ExtractedDates(
    val dueDate: LocalDate?,
    val invoicePeriod: LocalDate?
)
object InvoiceDateExtractor {

    fun parseDate(qrDescription: String?, ocrText: List<String>): ExtractedDates {
        val qrPart = qrDescription?.split(" ").orEmpty()
        val ocrPart = ocrText.flatMap { it.split(" ") }

        val qrExtractedDates = InvoiceDateExtractor.extractInvoiceDates(qrPart, assumeInvoiceDate = false)
        val ocrExtractedDates = InvoiceDateExtractor.extractInvoiceDates(ocrPart)

        val dueDate = qrExtractedDates.dueDate ?: ocrExtractedDates.dueDate
        val invoiceDate = qrExtractedDates.invoicePeriod ?: ocrExtractedDates.invoicePeriod

        return ExtractedDates(dueDate, invoiceDate)
    }

    private val croatianMonthMap: Map<String, Int> = buildMap {
        val months = listOf(
            Triple(1, listOf("siječanj", "siječnja", "sij"), "jan"),
            Triple(2, listOf("veljača", "veljače", "velj"), "feb"),
            Triple(3, listOf("ožujak", "ožujka", "ožu"), "mar"),
            Triple(4, listOf("travanj", "travnja", "tra"), "apr"),
            Triple(5, listOf("svibanj", "svibnja", "svi"), "may"),
            Triple(6, listOf("lipanj", "lipnja", "lip"), "jun"),
            Triple(7, listOf("srpanj", "srpnja", "srp"), "jul"),
            Triple(8, listOf("kolovoz", "kolovoza", "kol"), "aug"),
            Triple(9, listOf("rujan", "rujna", "ruj"), "sep"),
            Triple(10, listOf("listopad", "listopada", "lis"), "oct"),
            Triple(11, listOf("studeni", "studenoga", "stu"), "nov"),
            Triple(12, listOf("prosinac", "prosinca", "pro"), "dec")
        )
        for ((num, names, _) in months) {
            names.forEach { put(it.lowercase(), num) }
        }
    }

    private fun extractInvoiceDates(words: List<String>, assumeInvoiceDate: Boolean = true): ExtractedDates {
        val fullText = words.joinToString(" ")

        // --- Regex Definitions (Same as before) ---
        val monthNamesRegex = croatianMonthMap.keys.joinToString("|")
        val numericFullDate = Regex("""\b(\d{1,2})[./-](\d{1,2})[./-](\d{4})\b""")
        val textualFullDate = Regex("""(?i)\b(\d{1,2})\.?\s+($monthNamesRegex)\.?\s+(\d{4})\b""")
        val numericMonthYear = Regex("""\b(\d{1,2})[./](\d{4})\b""")
        val textualMonthYear = Regex("""(?i)\b($monthNamesRegex)\.?\s+(\d{4})\b""")
        val compactMonthYear = Regex("""\b(\d{2})(\d{4})\b""")

        // --- 1. FIND DUE DATE ---
        var rawDueDate: String? = null
        val dueKeywords = "do|rok|dospijeće|valuta|datum dospijeća|plaćanje"

        val numericDueMatch = Regex("""(?i)\b($dueKeywords)[:\s]+($numericFullDate)""").find(fullText)
        rawDueDate = numericDueMatch?.groupValues?.get(2)

        if (rawDueDate == null) {
            val textualDueMatch = Regex("""(?i)\b($dueKeywords)[:\s]+($textualFullDate)""").find(fullText)
            rawDueDate = textualDueMatch?.groupValues?.get(2)
        }

        if (rawDueDate == null) {
            rawDueDate = numericFullDate.findAll(fullText).map { it.value }.lastOrNull()
        }

        val parsedDueDate = parseCroatianDate(rawDueDate, isPeriod = false)


        // --- 2. FIND INVOICE PERIOD ---
        var rawPeriod: String? = null
        val periodKeywords = "za|mjesec|razdoblje|period|obračun"

        // A. Full Date Period
        val textFullPeriodMatch = Regex("""(?i)\b($periodKeywords)[:\s]+($textualFullDate)""").find(fullText)
        if (textFullPeriodMatch != null) rawPeriod = textFullPeriodMatch.groupValues[2]

        if (rawPeriod == null) {
            val numFullPeriodMatch = Regex("""(?i)\b($periodKeywords)[:\s]+($numericFullDate)""").find(fullText)
            if (numFullPeriodMatch != null) rawPeriod = numFullPeriodMatch.groupValues[2]
        }

        // B. Month-Year Period
        if (rawPeriod == null) {
            val textMonthMatch = Regex("""(?i)\b($periodKeywords)[:\s]+($textualMonthYear)""").find(fullText)
                ?: textualMonthYear.find(fullText)
            if (textMonthMatch != null) {
                rawPeriod = if (textMonthMatch.groupValues.size > 2) textMonthMatch.groupValues[2] else textMonthMatch.value
            }
        }

        // C. Numeric Month-Year
        if (rawPeriod == null) {
            val numMonthMatch = Regex("""(?i)\b($periodKeywords)[:\s]+($numericMonthYear)""").find(fullText)
            rawPeriod = numMonthMatch?.groupValues?.get(2)
        }

        // D. Compact
        if (rawPeriod == null) {
            val compactMatch = Regex("""(?i)\b(mjesec|razdoblje)[:\s]+($compactMonthYear)""").find(fullText)
            rawPeriod = compactMatch?.groupValues?.get(2)
        }

        var parsedPeriod = parseCroatianDate(rawPeriod, isPeriod = true)

        // --- 3. FALLBACK LOGIC ---
        // If no period found in text, infer from Due Date (1 month prior, last day)
        if (assumeInvoiceDate && (parsedPeriod == null && parsedDueDate != null)) {
            // Go back 1 month
            val previousMonthDate = parsedDueDate.minus(1, DateTimeUnit.MONTH)
            // Set to last day of that previous month
            parsedPeriod = getLastDayOfMonth(previousMonthDate.year, previousMonthDate.monthNumber)
        }

        return ExtractedDates(
            dueDate = parsedDueDate,
            invoicePeriod = parsedPeriod
        )
    }

    /**
     * Parses a string to LocalDate.
     * @param isPeriod If true and no day is present, defaults to the LAST DAY of the month.
     */
    private fun parseCroatianDate(raw: String?, isPeriod: Boolean): LocalDate? {
        if (raw == null) return null

        try {
            val clean = raw.trim().trimEnd('.').replace(Regex("\\s+"), " ")

            // 1. Compact (MMyyyy)
            if (isPeriod && clean.length == 6 && clean.all { it.isDigit() }) {
                val month = clean.substring(0, 2).toInt()
                val year = clean.substring(2, 6).toInt()
                return getLastDayOfMonth(year, month)
            }

            // 2. Parts Splitter
            val parts = clean.split(Regex("[./\\-\\s]+"))

            if (parts.size == 3) {
                // Full Date (Day exists) - Respect it
                val day = parts[0].toInt()
                val monthStr = parts[1].lowercase()
                val year = parts[2].toInt()
                val month = monthStr.toIntOrNull() ?: croatianMonthMap[monthStr] ?: return null
                return LocalDate(year, month, day)
            }
            else if (parts.size == 2 && isPeriod) {
                // Month-Year only - Calculate Last Day
                val monthStr = parts[0].lowercase()
                val year = parts[1].toInt()
                val month = monthStr.toIntOrNull() ?: croatianMonthMap[monthStr] ?: return null

                return getLastDayOfMonth(year, month)
            }

        } catch (e: Exception) {
            // Handle parsing errors
        }
        return null
    }

    /**
     * Helper to calculate the last day of a specific month/year.
     * Logic: (First day of next month) minus 1 day.
     */
    private fun getLastDayOfMonth(year: Int, month: Int): LocalDate {
        // 1. Create date for 1st of current month
        val startOfMonth = LocalDate(year, month, 1)
        // 2. Add 1 month to get 1st of next month
        val startOfNextMonth = startOfMonth.plus(1, DateTimeUnit.MONTH)
        // 3. Subtract 1 day
        return startOfNextMonth.minus(1, DateTimeUnit.DAY)
    }
}