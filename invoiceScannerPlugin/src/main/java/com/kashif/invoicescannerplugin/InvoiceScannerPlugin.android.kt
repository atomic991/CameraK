package com.kashif.invoicescannerplugin

import android.content.Context
import android.graphics.ImageFormat
import android.os.CountDownTimer
import android.util.Log
import android.util.SparseIntArray
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.ui.geometry.Rect
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.kashif.cameraK.controller.CameraController
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.number

fun CameraController.enableInvoiceScanner(onInvoiceScanner: (InvoiceScannerData) -> Unit) {
    Log.i("InvoiceScanner", "Enabling invoice scanner")

    val resolutionSelector = ResolutionSelector.Builder()
        .setAspectRatioStrategy(
            AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        )
        .setResolutionStrategy(
            ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY
        )
        .build()

    imageAnalyzer = ImageAnalysis.Builder()
        .setResolutionSelector(resolutionSelector)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build().apply {
            setAnalyzer(
                ContextCompat.getMainExecutor(context),
                InvoiceAnalyzer(context, onInvoiceScanner)
            )
        }

    updateImageAnalyzer()
}

private class InvoiceAnalyzer(context: Context, private val onQrScanner: (InvoiceScannerData) -> Unit) : ImageAnalysis.Analyzer {

    private val ORIENTATIONS = SparseIntArray()

    init {
        ORIENTATIONS.append(Surface.ROTATION_0, 0)
        ORIENTATIONS.append(Surface.ROTATION_90, 90)
        ORIENTATIONS.append(Surface.ROTATION_180, 180)
        ORIENTATIONS.append(Surface.ROTATION_270, 270)
    }

    var data = InvoiceScannerData("")

    val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_PDF417)
//        .setZoomSuggestionOptions(
//            ZoomSuggestionOptions.Builder(zoomCallback)
//                .setMaxSupportedZoomRatio(maxSupportedZoomRatio)
//                .build()
//        )
        .build()

    val barcodeScanner = BarcodeScanning.getClient(options)
    val textScanner = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    var timer: CountDownTimer? = null

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        Log.d("InvoiceScanner", "InvoiceAnalyzer.analyze called")
        val mediaImage = imageProxy.image ?: return
        if (mediaImage.format != ImageFormat.YUV_420_888) {
            Log.e("InvoiceScanner", "Unsupported image format: ${mediaImage.format}")
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
//        Log.d("InvoiceScanner2", "Image rotation degrees: ${imageProxy.imageInfo.rotationDegrees}")
//        Log.d("InvoiceScanner2","Image width: ${image.width} and height: ${image.height}")

        textScanner.process(image)
            .addOnSuccessListener { text ->
                data = data.copy(ocrText = text.textBlocks.flatMap { it.lines.map { l -> l.text } })
                println("OCR Text: ${text.textBlocks.flatMap { it.lines.map { l -> l.text } }}")
            }

        barcodeScanner.process(image)
            .addOnCompleteListener {
                imageProxy.close()
            }
            .addOnFailureListener {
                imageProxy.close()
            }
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    println("Barcode bounding box ${barcode.boundingBox}")
                    val rawValue = barcode.rawValue
                    val valueType = barcode.valueType
                    if(!rawValue.isNullOrBlank() && valueType == Barcode.TYPE_TEXT) {
//                        Log.d("BarcodeScanner", "Barcode detected with type: $valueType and value: $rawValue")
                        val boundingBox = barcode.boundingBox?.let {
                            Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
                        }
                        if(data.rawText.isBlank() || data.rawText != rawValue) {
                            startParseTimer()
                        }
                        data = data.copy(rawText = rawValue, boundingBox = boundingBox, imageWidth = imageProxy.width, imageHeight = imageProxy.height)
                        parseDate(data.rawText, data.ocrText)
                    }
                }
            }
    }

    private fun startParseTimer() {
        timer?.cancel()
        timer = object: CountDownTimer(4000, 200) {
            override fun onFinish() {
                onQrScanner(data)
            }

            override fun onTick(millisUntilFinished: Long) {
                if(data.date != null && data.dueDate != null) {
                    this.cancel()
                    onQrScanner(data)
                }
            }
        }
        timer?.start()
    }

    private fun parseDate(qrDescription: String?, ocrText: List<String>) {

        val qrDates = mutableSetOf<LocalDate>()
        val ocrDates = mutableSetOf<LocalDate>()

        val qrPart = qrDescription?.split(" ").orEmpty()
        val ocrPart = ocrText.flatMap { it.split(" ") }

        qrPart.forEach {
            val words = it.split(" ")
            val digitWords = words.filter { w -> w.any { d -> d.isDigit() } && w.length >= 5 }
            for (word in digitWords) {
                val cleanWord = word.replace(",", " ").trim().removeSuffix(".")
                println("CameraViewModel clean word: $cleanWord")
                val dueDate = parseFlexibleDate(cleanWord)
                if(dueDate != null) {
                    qrDates.add(dueDate)
                    println("CameraViewModel dueDate: $dueDate")
                }
            }
        }

        ocrPart.forEach {
            val words = it.split(" ")
            val digitWords = words.filter { w -> w.any { d -> d.isDigit() } && w.length >= 5 }
            for (word in digitWords) {
                val cleanWord = word.replace(",", " ").trim().removeSuffix(".")
                println("CameraViewModel clean word: $cleanWord")
                val dueDate = parseFlexibleDate(cleanWord)
                if(dueDate != null) {
                    ocrDates.add(dueDate)
                    data = data.copy(dueDate = dueDate)
                    println("CameraViewModel dueDate: $dueDate")
                }
            }
        }

        val matchDates = findFirstOneMonthApartPair(qrDates.toList(), ocrDates.toList())
        data = data.copy(date = matchDates.first, dueDate = matchDates.second)
    }

    fun findFirstOneMonthApartPair(qrDates: List<LocalDate>, ocrDates: List<LocalDate>): Pair<LocalDate?, LocalDate?> {
        val sortedOcr = ocrDates.sortedBy { it.year * 12 + it.month.number }
        val sortedQr = qrDates.sortedBy { it.year * 12 + it.month.number }
        if(sortedOcr.size == 1) {
            val date = checkIfOneMonthApart(sortedQr, sortedOcr.first())
            if(date != null) {
                return date to sortedOcr.first()
            } else if(sortedQr.isEmpty()) {
                return sortedOcr.first().minus(1, DateTimeUnit.MONTH) to sortedOcr.first()
            }
        }
        if(sortedOcr.size == 2) {
            return sortedOcr.first() to sortedOcr.last()
        }
         // Sort by year/month


        val dates = ocrDates.plus(qrDates).sortedBy { it.year * 12 + it.month.number }
        for (i in dates) {
            val date = checkIfOneMonthApart(sortedQr, i)
            if(date != null) {
                return date to i
            }
        }

        return dates.firstOrNull() to dates.lastOrNull()
    }

    private fun checkIfOneMonthApart(source: List<LocalDate>, target: LocalDate): LocalDate? {
        for (i in source.indices) {
            val date1 = source[i]
            val year1 = date1.year
            val month1 = date1.month.number

            val diff = (target.year - year1) * 12 + (target.month.number - month1)

            if (diff == 1) {
                return date1
            }
        }
        return null
    }
}

/**
 * Platform-specific function to start scanning for QR codes.
 *
 * @param controller The CameraController to be used for scanning.
 * @param onInvoiceScanner A callback function that is invoked when a QR code is scanned.
 */
actual fun startScanning(
    controller: CameraController,
    onInvoiceScanner: (InvoiceScannerData) -> Unit
) {
    Log.i("InvoiceScanner", "startScanning called")
    controller.enableInvoiceScanner(onInvoiceScanner)

}

/**
 * Get the angle by which an image must be rotated given the device's current
 * orientation.
 */
//@Throws(CameraAccessException::class)
//private fun getRotationCompensation(cameraId: String, activity: Activity, isFrontFacing: Boolean): Int {
//    // Get the device's current rotation relative to its "native" orientation.
//    // Then, from the ORIENTATIONS table, look up the angle the image must be
//    // rotated to compensate for the device's rotation.
//    val deviceRotation = activity.windowManager.defaultDisplay.rotation
//    var rotationCompensation = ORIENTATIONS.get(deviceRotation)
//
//    // Get the device's sensor orientation.
//    val cameraManager = activity.getSystemService(CAMERA_SERVICE) as CameraManager
//    val sensorOrientation = cameraManager
//        .getCameraCharacteristics(cameraId)
//        .get(CameraCharacteristics.SENSOR_ORIENTATION)!!
//
//    if (isFrontFacing) {
//        rotationCompensation = (sensorOrientation + rotationCompensation) % 360
//    } else { // back-facing
//        rotationCompensation = (sensorOrientation - rotationCompensation + 360) % 360
//    }
//    return rotationCompensation
//}