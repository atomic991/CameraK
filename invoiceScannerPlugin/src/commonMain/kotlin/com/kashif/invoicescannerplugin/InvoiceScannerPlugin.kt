package com.kashif.invoicescannerplugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.kashif.cameraK.controller.CameraController
import com.kashif.cameraK.plugins.CameraPlugin
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class InvoiceScannerPlugin(private val coroutineScope: CoroutineScope) : CameraPlugin {
    private var cameraController: CameraController? = null
    private val barcodeFlow = MutableSharedFlow<InvoiceScannerData>()
    private var isScanning = atomic(false)

    /**
     * Initializes the InvoiceScannerPlugin with the given CameraController.
     *
     * @param cameraController The CameraController to be used for scanning.
     */
    override fun initialize(cameraController: CameraController) {
        println("InvoiceScannerPlugin initialized")
        this.cameraController = cameraController
    }

    /**
     * Starts the QR code scanning process.
     *
     * @throws IllegalStateException If the CameraController is not initialized.
     */
    fun startScanning() {
        cameraController?.let { controller ->
            isScanning.value = true
            startScanning(controller = controller) { data ->
                if (isScanning.value) {
                    coroutineScope.launch {
                        barcodeFlow.emit(data)
                    }
                }
            }
        } ?: throw IllegalStateException("CameraController is not initialized")
    }

    /**
     * Pauses the scanning process.
     */
    fun pauseScanning() {
        isScanning.value = false
    }

    /**
     * Resumes the scanning process.
     */
    fun resumeScanning() {
        isScanning.value = true
        startScanning()
    }

    /**
     * Returns a flow that emits QR codes.
     *
     * @return SharedFlow<String>
     */
    fun getBarcodeFlow() = barcodeFlow.asSharedFlow()
}

/**
 * Platform-specific function to start scanning for QR codes.
 *
 * @param controller The CameraController to be used for scanning.
 * @param onInvoiceScanner A callback function that is invoked when a QR code is scanned.
 */
expect fun startScanning(
    controller: CameraController,
    onInvoiceScanner: (InvoiceScannerData) -> Unit
)

/**
 * Creates and remembers a InvoiceScannerPlugin composable.
 *
 * @param onInvoiceScanner A callback function that is invoked when a QR code is scanned.
 * @return A remembered instance of InvoiceScannerPlugin.
 */
@Composable
fun rememberInvoiceScannerPlugin(
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): InvoiceScannerPlugin {
    return remember {
        InvoiceScannerPlugin(coroutineScope)
    }
}