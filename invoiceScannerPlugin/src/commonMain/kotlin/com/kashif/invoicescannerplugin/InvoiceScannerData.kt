package com.kashif.invoicescannerplugin

import androidx.compose.ui.geometry.Rect
import kotlinx.datetime.LocalDate

data class InvoiceScannerData(
    val rawText: String,
    val boundingBox: Rect? = null,
    val ocrText: List<String> = emptyList(),
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val date: LocalDate? = null,
    val dueDate: LocalDate? = null
)