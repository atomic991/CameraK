package com.kashif.invoicescannerplugin

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
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.kashif.cameraK.controller.CameraController

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
                InvoiceAnalyzer(onInvoiceScanner)
            )
        }

    updateImageAnalyzer()
}

private class InvoiceAnalyzer(private val onQrScanner: (InvoiceScannerData) -> Unit) : ImageAnalysis.Analyzer {

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
                    val qrRawData = barcode.rawValue
                    val valueType = barcode.valueType
                    if(!qrRawData.isNullOrBlank() && valueType == Barcode.TYPE_TEXT) {
//                        Log.d("BarcodeScanner", "Barcode detected with type: $valueType and value: $rawValue")
                        val boundingBox = barcode.boundingBox?.let {
                            Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
                        }
                        if(data.rawText.isBlank() || data.rawText != qrRawData) {
                            startParseTimer()
                        }

                        val extractedDates = InvoiceDateExtractor.parseDate(qrRawData, data.ocrText)
                        data = data.copy(
                            date = extractedDates.invoicePeriod,
                            dueDate = extractedDates.dueDate,
                            rawText = qrRawData,
                            boundingBox = boundingBox,
                            imageWidth = imageProxy.width,
                            imageHeight = imageProxy.height
                        )
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