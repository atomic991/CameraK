package com.kashif.invoicescannerplugin

import cocoapods.GoogleMLKit.MLKBarcodeScanner
import com.kashif.cameraK.controller.CameraController
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypePDF417Code
import platform.darwin.NSObject

sealed class ScannedCode {
    abstract val value: String
    abstract val type: String

    data class Barcode(
        override val value: String,
        override val type: String
    ) : ScannedCode()

    companion object {
        fun fromAVMetadata(metadata: AVMetadataMachineReadableCodeObject): ScannedCode? {

            val value = fixEncoding(metadata.stringValue) ?: return null

            return Barcode(value, "PDF_417")
        }

        private fun fixEncoding(input: String?): String? {
            val byteList = input?.map { it.code.toByte() } // Reinterpret characters as Latin-1 bytes
            val byteArray = byteList?.toByteArray()
            return byteArray?.decodeToString()
        }
    }
}

actual fun startScanning(
    controller: CameraController,
    onInvoiceScanner: (InvoiceScannerData) -> Unit
) {
    val codeAnalyzer = CodeAnalyzer(onCodeScanned = {
        onInvoiceScanner(InvoiceScannerData(it.value))
    })
    controller.setMetadataObjectsDelegate(codeAnalyzer)


    controller.updateMetadataObjectTypes(
        listOf(AVMetadataObjectTypePDF417Code!!)
    )
    controller.startSession()
}

@OptIn(ExperimentalForeignApi::class)
private class CodeAnalyzer(
    private val onCodeScanned: (ScannedCode) -> Unit,
    private val debounceMs: Long = 1000L
) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {

    private val isProcessing = atomic(false)
    private val scope = CoroutineScope(Dispatchers.Main)
    private var lastScannedCode: ScannedCode? = null
    private var debounceJob: Job? = null

    private val scanner = MLKBarcodeScanner.barcodeScanner()

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection
    ) {
        if (isProcessing.value) return
        for (metadata in didOutputMetadataObjects) {
            if (metadata !is AVMetadataMachineReadableCodeObject) continue
            val scannedCode = ScannedCode.fromAVMetadata(metadata) ?: continue
            if (scannedCode == lastScannedCode) continue

            processCode(scannedCode)
            break
        }
    }

    private fun processCode(code: ScannedCode) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            if (isProcessing.compareAndSet(expect = false, update = true)) {
                try {
                    lastScannedCode = code
                    onCodeScanned(code)
                    delay(debounceMs)
                } finally {
                    isProcessing.value = false
                }
            }
        }
    }
}