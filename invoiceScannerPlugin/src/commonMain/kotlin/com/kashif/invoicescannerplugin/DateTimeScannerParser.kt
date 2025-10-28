package com.kashif.invoicescannerplugin

import kotlinx.datetime.LocalDate
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


expect fun parseFlexibleDate(input: String): LocalDate?