@file:OptIn(ExperimentalForeignApi::class)

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
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi

@OptIn(NativeRuntimeApi::class)
actual fun startScanning(
    controller: CameraController,
    onInvoiceScanner: (InvoiceScannerData) -> Unit
) {

    val barcodeScanner = MLKBarcodeScanner.barcodeScannerWithOptions(MLKBarcodeScannerOptions(MLKBarcodeFormatPDF417))



    val isProcessing = atomic(false)

    controller.setFrameObjectsDelegate(object: CameraControllerCallback {
        override fun invoke(p1: ImageData) {

            if (!isProcessing.compareAndSet(expect = false, update = true)) {
                return
            }

            val inputImage = MLKVisionImage(p1.buffer).apply {
                setOrientation(p1.orientation)
            }
            barcodeScanner.processImage(inputImage as MLKCompatibleImageProtocol) { barcodes, error ->
                try {
                    if (barcodes != null) {
                        barcodes.map { result ->
                            val barcode = result as MLKBarcode
                            println(barcode.rawValue)
                        }
                        println("Success")
//                    emitter.resume(Result.success(result))
                    } else {
                        println("Failure")
//                    emitter.resume(Result.failure(Throwable(error?.debugDescription)))
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