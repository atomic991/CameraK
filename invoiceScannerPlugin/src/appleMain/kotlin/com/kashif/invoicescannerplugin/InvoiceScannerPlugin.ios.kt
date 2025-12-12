@file:OptIn(ExperimentalForeignApi::class)
@file:Suppress("UNCHECKED_CAST_TO_FORWARD_DECLARATION")

package com.kashif.invoicescannerplugin

import cocoapods.GoogleMLKit.MLKBarcode
import cocoapods.GoogleMLKit.MLKBarcodeFormatPDF417
import cocoapods.GoogleMLKit.MLKBarcodeScanner
import cocoapods.GoogleMLKit.MLKBarcodeScannerOptions
import cocoapods.GoogleMLKit.MLKVisionImage
import com.kashif.cameraK.controller.CameraController
import com.kashif.cameraK.controller.CameraControllerCallback
import com.kashif.cameraK.controller.ImageData
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import objcnames.protocols.MLKCompatibleImageProtocol
import platform.CoreImage.CIContext
import platform.CoreImage.CIImage
import platform.CoreImage.createCGImage
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVImageBufferRef
import platform.Foundation.NSTimer
import platform.UIKit.UIImage
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi


@OptIn(NativeRuntimeApi::class)
class ScannerController(
    private val controller: CameraController,
    private val onQrScanner: (InvoiceScannerData) -> Unit
) {
    private var timer: NSTimer? = null
    private var ticksCount = 0
    private val maxTicks = 20 // 4000ms / 200ms = 20 ticks
    val barcodeScanner = MLKBarcodeScanner.barcodeScannerWithOptions(MLKBarcodeScannerOptions(MLKBarcodeFormatPDF417))
    val isProcessing = atomic(false)
    var data = InvoiceScannerData("")

    fun analyze() {

        controller.setFrameObjectsDelegate(object: CameraControllerCallback {
            override fun invoke(p1: ImageData) {

                if (!isProcessing.compareAndSet(expect = false, update = true)) {
                    return
                }

                val inputImage = MLKVisionImage(p1.buffer).apply {
                    setOrientation(p1.orientation)
                }

                val uiImage = convertSampleBufferToUIImage(p1.buffer)
                val ocrText = recognizeText(uiImage)

                data = data.copy(ocrText = ocrText)

                val mlImage = inputImage as MLKCompatibleImageProtocol
                barcodeScanner.processImage(mlImage) { barcodes, error ->
                    try {
                        if (barcodes != null) {
                            val mlBarcodes = barcodes.map { it as MLKBarcode }
                            for(barcode in mlBarcodes) {
                                val qrRawData = barcode.rawValue() ?: continue
                                if(data.rawText.isBlank() || data.rawText != qrRawData) {
                                    startParseTimer()
                                }

                                val extractedDates = InvoiceDateExtractor.parseDate(qrRawData, data.ocrText)
                                data = data.copy(
                                    date = extractedDates.invoicePeriod,
                                    dueDate = extractedDates.dueDate,
                                    rawText = qrRawData
                                )
                            }
                        }
                    } finally {
                        GC.collect() // HEAVY OPERATION
                        isProcessing.value = false
                    }
                }
            }
        })

        controller.startSession()
    }

    private fun startParseTimer() {
        // 1. Cancel existing
        timer?.invalidate()
        ticksCount = 0

        // 2. Schedule new timer (0.2 seconds = 200ms)
        timer = NSTimer.scheduledTimerWithTimeInterval(
            interval = 0.2,
            repeats = true
        ) { t ->
            ticksCount++

            // --- onTick Logic ---
            if (data.date != null && data.dueDate != null) {
                onQrScanner(data)
                t?.invalidate() // Stop timer
                return@scheduledTimerWithTimeInterval
            }

            // --- onFinish Logic ---
            if (ticksCount >= maxTicks) {
                onQrScanner(data)
                t?.invalidate() // Stop timer
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    fun recognizeText(image: UIImage?): List<String> {
        val cgImage = image?.CGImage ?: return emptyList()

        // 1. Variable to hold the list
        var recognizedLines = emptyList<String>()

        val request = VNRecognizeTextRequest { request, error ->
            if (error != null) {
                println("Vision Error: ${error.localizedDescription}")
                return@VNRecognizeTextRequest
            }

            val observations = request?.results as? List<VNRecognizedTextObservation> ?: emptyList()

            // 2. Map observations directly to List<String>
            // mapNotNull skips any lines where text detection failed
            recognizedLines = observations.flatMap { observation ->
                val topCandidate = observation.topCandidates(1u).firstOrNull() as? VNRecognizedText
                topCandidate?.string?.split(" ")?.filter { !it.isBlank() } ?: emptyList()
            }
        }

        request.recognitionLevel = VNRequestTextRecognitionLevelAccurate

        val handler = VNImageRequestHandler(cgImage, options = emptyMap<Any?, Any?>())

        try {
            // 3. Block until finished
            handler.performRequests(listOf(request), error = null)
        } catch (e: Exception) {
            println("Exception during recognition: ${e.message}")
        }

        // 4. Return the populated list
        return recognizedLines
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun convertSampleBufferToUIImage(sampleBuffer: CMSampleBufferRef?): UIImage? {
        if (sampleBuffer == null) return null


        val imageBuffer: CVImageBufferRef = CMSampleBufferGetImageBuffer(sampleBuffer) ?: return null


        val ciImage = CIImage.imageWithCVPixelBuffer(imageBuffer)
        val ciContext = CIContext()


        val cgImage = ciContext.createCGImage(ciImage, ciImage.extent)


        return UIImage(cgImage)
    }
}

actual fun startScanning(
    controller: CameraController,
    onInvoiceScanner: (InvoiceScannerData) -> Unit
) {
    ScannerController(controller, onInvoiceScanner).analyze()
}