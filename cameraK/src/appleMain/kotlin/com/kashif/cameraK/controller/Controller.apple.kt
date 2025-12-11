package com.kashif.cameraK.controller

import com.kashif.cameraK.enums.CameraLens
import com.kashif.cameraK.enums.Directory
import com.kashif.cameraK.enums.FlashMode
import com.kashif.cameraK.enums.ImageFormat
import com.kashif.cameraK.enums.QualityPrioritization
import com.kashif.cameraK.enums.TorchMode
import com.kashif.cameraK.plugins.CameraPlugin
import com.kashif.cameraK.result.ImageCaptureResult
import com.kashif.cameraK.utils.MemoryManager
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVCaptureFlashMode
import platform.AVFoundation.AVCaptureFlashModeAuto
import platform.AVFoundation.AVCaptureFlashModeOff
import platform.AVFoundation.AVCaptureFlashModeOn
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureTorchMode
import platform.AVFoundation.AVCaptureTorchModeAuto
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.AVCaptureVideoOrientation
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeLeft
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeRight
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation
import platform.UIKit.UIViewController

actual class CameraController(
    internal var flashMode: FlashMode,
    internal var torchMode: TorchMode,
    internal var cameraLens: CameraLens,
    internal var imageFormat: ImageFormat,
    internal var qualityPriority: QualityPrioritization,
    internal var directory: Directory,
    internal var cameraDeviceType: String?,
    internal var plugins: MutableList<CameraPlugin>
) : UIViewController(null, null) {

    private val customCameraController = IOSCameraController()

    fun getCameraPreviewLayer() = customCameraController.getPreviewLayer()

    internal fun currentVideoOrientation(): AVCaptureVideoOrientation? {
        val orientation = UIDevice.currentDevice.orientation
        return when (orientation) {
            UIDeviceOrientation.UIDeviceOrientationPortrait -> AVCaptureVideoOrientationPortrait
            UIDeviceOrientation.UIDeviceOrientationLandscapeLeft -> AVCaptureVideoOrientationLandscapeRight
            UIDeviceOrientation.UIDeviceOrientationLandscapeRight -> AVCaptureVideoOrientationLandscapeLeft
            else -> null
        }
    }

    fun setMetadataObjectsDelegate(delegate: AVCaptureMetadataOutputObjectsDelegateProtocol) {

    }

    fun updateMetadataObjectTypes(newTypes: List<String>) {

    }

    fun setFrameObjectsDelegate(callback: CameraControllerCallback) {
        customCameraController.setFrameListener(object: IOSCameraController.CallbackListener {
            override fun invoke(imageData: ImageData) {
                callback.invoke(imageData)
            }
        })
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        getCameraPreviewLayer()?.setFrame(view.bounds)
    }

    actual suspend fun takePicture(): ImageCaptureResult = suspendCancellableCoroutine { continuation ->

    }

    actual fun toggleFlashMode() {
        flashMode = when (flashMode) {
            FlashMode.OFF -> FlashMode.ON
            FlashMode.ON -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.OFF
        }
//        customCameraController.setFlashMode(flashMode.toAVCaptureFlashMode())
    }

    actual fun setFlashMode(mode: FlashMode) {
        flashMode = mode
//        customCameraController.setFlashMode(mode.toAVCaptureFlashMode())
    }

    actual fun getFlashMode(): FlashMode? {
//        fun AVCaptureFlashMode.toCameraKFlashMode(): FlashMode? {
//            return when (this) {
//                AVCaptureFlashModeOn -> FlashMode.ON
//                AVCaptureFlashModeOff -> FlashMode.OFF
//                AVCaptureFlashModeAuto -> FlashMode.AUTO
//                else -> null
//            }
//        }
//
//        return customCameraController.flashMode.toCameraKFlashMode()
        return null
    }

    actual fun toggleTorchMode() {
        torchMode = when (torchMode) {
            TorchMode.OFF -> TorchMode.ON
            TorchMode.ON -> TorchMode.AUTO
            TorchMode.AUTO -> TorchMode.OFF
        }
//        customCameraController.setTorchMode(torchMode.toAVCaptureTorchMode())
    }

    actual fun setTorchMode(mode: TorchMode) {
        torchMode = mode
//        customCameraController.setTorchMode(mode.toAVCaptureTorchMode())
    }

    actual fun toggleCameraLens() {

    }

    actual fun startSession() {
        runBlocking {
            customCameraController.startPreview(view)
        }
    }

    actual fun stopSession() {
        customCameraController.stop()
    }

    actual fun addImageCaptureListener(listener: (ByteArray) -> Unit) {

    }

    actual fun initializeControllerPlugins() {
        plugins.forEach {
            it.initialize(this)
        }
    }

    private fun FlashMode.toAVCaptureFlashMode(): AVCaptureFlashMode = when (this) {
        FlashMode.ON -> AVCaptureFlashModeOn
        FlashMode.OFF -> AVCaptureFlashModeOff
        FlashMode.AUTO -> AVCaptureFlashModeAuto
    }

    private fun TorchMode.toAVCaptureTorchMode(): AVCaptureTorchMode = when (this) {
        TorchMode.ON -> AVCaptureTorchModeOn
        TorchMode.OFF -> AVCaptureTorchModeOff
        TorchMode.AUTO -> AVCaptureTorchModeAuto
    }
}