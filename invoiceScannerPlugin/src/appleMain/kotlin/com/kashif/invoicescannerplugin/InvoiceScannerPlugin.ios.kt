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
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import objcnames.protocols.MLKCompatibleImageProtocol
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.CoreMedia.CMSampleBufferRef
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation
import platform.UIKit.UIImageOrientation
import platform.darwin.DISPATCH_QUEUE_CONCURRENT
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create
import kotlin.native.runtime.NativeRuntimeApi

//sealed class ScannedCode {
//    abstract val value: String
//    abstract val type: String
//
//
//    companion object {
//        fun fromAVMetadata(metadata: AVMetadataMachineReadableCodeObject): ScannedCode? {
//            val value = fixEncoding(metadata.stringValue) ?: return null
//            return Barcode(value, "PDF_417")
//        }
//
//        private fun fixEncoding(input: String?): String? {
//            val byteList = input?.map { it.code.toByte() } // Reinterpret characters as Latin-1 bytes
//            val byteArray = byteList?.toByteArray()
//            return byteArray?.decodeToString()
//        }
//    }
//}

actual fun startScanning(
    controller: CameraController,
    onInvoiceScanner: (InvoiceScannerData) -> Unit
) {

    val barcodeScanner = MLKBarcodeScanner.barcodeScannerWithOptions(MLKBarcodeScannerOptions(MLKBarcodeFormatPDF417))

    controller.setFrameObjectsDelegate(object: CameraControllerCallback {
        override fun invoke(p1: ImageData) {
            val inputImage = MLKVisionImage(p1.buffer).apply {
                setOrientation(p1.orientation)
            }
            barcodeScanner.processImage(inputImage as MLKCompatibleImageProtocol) { barcodes, error ->
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
            }
        }
    })

    controller.startSession()
}