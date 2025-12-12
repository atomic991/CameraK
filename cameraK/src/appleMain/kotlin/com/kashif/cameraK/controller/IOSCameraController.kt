package com.kashif.cameraK.controller

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureAutoFocusRangeRestrictionNear
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureFocusModeContinuousAutoFocus
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset1280x720
import platform.AVFoundation.AVCaptureSessionPreset1920x1080
import platform.AVFoundation.AVCaptureSessionPreset640x480
import platform.AVFoundation.AVCaptureSessionPresetPhoto
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoOrientation
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeLeft
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeRight
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoOrientationPortraitUpsideDown
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.autoFocusRangeRestriction
import platform.AVFoundation.focusMode
import platform.AVFoundation.isAutoFocusRangeRestrictionSupported
import platform.AVFoundation.isFocusModeSupported
import platform.AVFoundation.isSmoothAutoFocusSupported
import platform.AVFoundation.position
import platform.AVFoundation.requestAccessForMediaType
import platform.AVFoundation.smoothAutoFocusEnabled
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.CMSampleBufferRef
import platform.Foundation.NSSelectorFromString
import platform.QuartzCore.CALayer
import platform.UIKit.UIApplication
import platform.UIKit.UIImageOrientation
import platform.UIKit.UIInterfaceOrientation
import platform.UIKit.UIInterfaceOrientationLandscapeLeft
import platform.UIKit.UIInterfaceOrientationLandscapeRight
import platform.UIKit.UIInterfaceOrientationPortrait
import platform.UIKit.UIInterfaceOrientationPortraitUpsideDown
import platform.UIKit.UIView
import platform.UIKit.interfaceOrientation
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_set_target_queue
import platform.posix.QOS_CLASS_USER_INITIATED
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class ImageData @OptIn(ExperimentalForeignApi::class) constructor(
    val buffer: CMSampleBufferRef,
    val orientation: UIImageOrientation
)

internal class IOSCameraController {
    private val session = AVCaptureSession()
    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    private var videoOutput: AVCaptureVideoDataOutput? = null
    private var videoDeviceInput: AVCaptureDeviceInput? = null


    @OptIn(ExperimentalForeignApi::class)
    private val captureQueue = run {
        val q = dispatch_queue_create("camera.capture.queue", null)
        val qos = dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0u)
        dispatch_set_target_queue(q, qos)
        q
    }

    interface CallbackListener: (ImageData) -> Unit

    private var frameListener: CallbackListener? = null

    @OptIn(ExperimentalForeignApi::class)
    private val sampleDelegate = SampleBufferDelegate { frameListener?.invoke(it) }

    fun setFrameListener(listener: CallbackListener?) {
        frameListener = listener
    }

    suspend fun startPreview(target: UIView) {
        ensureVideoPermission()
        configureSession()
        attachPreviewIfNeeded(target)
        session.startRunning()
    }

    fun stop() {
        if (session.running) session.stopRunning()
        previewLayer?.removeFromSuperlayer()
        previewLayer = null
        videoOutput?.let { out -> if (session.outputs.contains(out)) session.removeOutput(out) }
        videoDeviceInput?.let { inp -> if (session.inputs.contains(inp)) session.removeInput(inp) }
        videoOutput = null
        videoDeviceInput = null
    }

    @OptIn(ExperimentalForeignApi::class)
    fun updateOrientationForCurrentUI(orientation: UIInterfaceOrientation) {
        val av = orientation.toAVOrientation()
        previewLayer?.connection?.let { conn ->
            if (conn.supportsVideoOrientation()) conn.videoOrientation = av
        }
        videoOutput?.connectionWithMediaType(AVMediaTypeVideo)?.let { conn ->
            if (conn.supportsVideoOrientation()) conn.videoOrientation = av
            conn.setVideoMirroringIfSupported(shouldMirrorFront = isFrontCamera())
        }
        previewLayer?.frame = previewLayer?.superlayer()?.bounds ?: CGRectZero.readValue()
    }
    // ————————————————————————————————————————————————————————————————

    fun getPreviewLayer() = previewLayer

    @OptIn(ExperimentalForeignApi::class)
    private fun attachPreviewIfNeeded(target: UIView) {
        dispatch_async(dispatch_get_main_queue()) {
            val layer = AVCaptureVideoPreviewLayer(session = session)
            layer.setFrame(target.bounds)
            layer.setVideoGravity(AVLayerVideoGravityResizeAspectFill)
            target.layer.addSublayer(layer as CALayer)
            previewLayer = layer
            val current =
                UIApplication.sharedApplication.keyWindow?.rootViewController?.interfaceOrientation
                    ?: UIInterfaceOrientationPortrait
            updateOrientationForCurrentUI(current)
            resizePreview(to = target.bounds)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    fun resizePreview(to: CValue<CGRect>) {
        dispatch_async(dispatch_get_main_queue()) {
            previewLayer?.frame = to
        }
    }


    @OptIn(ExperimentalForeignApi::class)
    private fun configureSession() {
        session.beginConfiguration()
        try {
            session.sessionPreset = AVCaptureSessionPresetPhoto
            val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
                ?: error("No camera device")
            setDeviceConfiguration(device)
            val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error = null)
                ?: error("No camera input")
            if (session.canAddInput(input)) {
                session.addInput(input)
                videoDeviceInput = input
            }
            val settings = mapOf<Any?, Any>(
                platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey to platform.CoreVideo.kCVPixelFormatType_32BGRA
            )
            val out = AVCaptureVideoDataOutput().apply {
                videoSettings = settings
                alwaysDiscardsLateVideoFrames = false
                setSampleBufferDelegate(
                    sampleDelegate,
                    captureQueue
                ) // captureQueue = SERIAL
            }
            if (session.canAddOutput(out)) {
                session.addOutput(out)
                videoOutput = out
            }
            videoOutput?.connectionWithMediaType(AVMediaTypeVideo)?.let { conn ->
                if (conn.supportsVideoOrientation()) {
                    conn.videoOrientation = AVCaptureVideoOrientationPortrait
                }
                conn.setVideoMirroringIfSupported(shouldMirrorFront = isFrontCamera())
            }
        } finally {
            session.commitConfiguration()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun setDeviceConfiguration(device: AVCaptureDevice) {
        try {
            // 1. You MUST lock the device before changing settings
            if (device.lockForConfiguration(null)) {

                // 2. Enable Continuous Auto Focus
                // This makes the lens constantly adjust as you move closer/further
                if (device.isFocusModeSupported(AVCaptureFocusModeContinuousAutoFocus)) {
                    device.focusMode = AVCaptureFocusModeContinuousAutoFocus
                }

                // 3. Optimize for "Near" Objects (The "Silver Bullet" for scanning)
                // This tells the hardware to prioritize searching for focus on objects
                // that are close to the lens (0-30cm), speeding up the focus lock significantly.
                if (device.isAutoFocusRangeRestrictionSupported()) {
                    device.autoFocusRangeRestriction = AVCaptureAutoFocusRangeRestrictionNear
                }

                // 4. Smooth Focus (Optional)
                // Reduces the "pulsing" effect, making the video feed look stable
                if (device.isSmoothAutoFocusSupported()) {
                    device.smoothAutoFocusEnabled = true
                }

                device.unlockForConfiguration()
            }
        } catch (e: Exception) {
            println("Error configuring focus: $e")
        }
    }

    private suspend fun ensureVideoPermission() {
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> return
            AVAuthorizationStatusNotDetermined -> {
                val granted = suspendCoroutine { cont ->
                    AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { ok ->
                        cont.resume(ok)
                    }
                }
                if (granted) return
            }
        }
        error("Camera permission not granted")
    }

    private fun isFrontCamera(): Boolean =
        videoDeviceInput?.device?.position == AVCaptureDevicePositionFront
}

@OptIn(ExperimentalForeignApi::class)
private class SampleBufferDelegate(
    private val onFrame: (ImageData) -> Unit
) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {


    @OptIn(BetaInteropApi::class)
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection
    ) {
        didOutputSampleBuffer?.let {
            onFrame(ImageData(it, fromConnection.videoOrientation.toUIImageOrientation()))
        }
    }
}

private fun UIInterfaceOrientation.toAVOrientation(): AVCaptureVideoOrientation =
    when (this) {
        UIInterfaceOrientationPortrait -> AVCaptureVideoOrientationPortrait
        UIInterfaceOrientationPortraitUpsideDown -> AVCaptureVideoOrientationPortraitUpsideDown
        UIInterfaceOrientationLandscapeLeft -> AVCaptureVideoOrientationLandscapeLeft
        UIInterfaceOrientationLandscapeRight -> AVCaptureVideoOrientationLandscapeRight
        else -> AVCaptureVideoOrientationPortrait
    }


private fun AVCaptureVideoOrientation.toUIImageOrientation(): UIImageOrientation =
    when (this) {
        AVCaptureVideoOrientationPortrait -> UIImageOrientation.UIImageOrientationUp
        AVCaptureVideoOrientationPortraitUpsideDown -> UIImageOrientation.UIImageOrientationDown
        AVCaptureVideoOrientationLandscapeLeft -> UIImageOrientation.UIImageOrientationLeft
        AVCaptureVideoOrientationLandscapeRight -> UIImageOrientation.UIImageOrientationRight
        else -> UIImageOrientation.UIImageOrientationUp
    }

@OptIn(ExperimentalForeignApi::class)
private fun AVCaptureConnection.supportsVideoOrientation(): Boolean =
    respondsToSelector(NSSelectorFromString("isVideoOrientationSupported")) ||
            respondsToSelector(NSSelectorFromString("videoOrientation"))

//@OptIn(ExperimentalForeignApi::class)
//private fun AVCaptureConnection.safeIsVideoMirrored(): Boolean {
//    return when {
//        respondsToSelector(NSSelectorFromString("isVideoMirrored")) -> AVCaptureConnection.isVideoMirrored()
//        respondsToSelector(NSSelectorFromString("videoMirrored")!!) -> AVCaptureConnection.videoMirrored
//        else -> false
//    }
//}

@OptIn(ExperimentalForeignApi::class)
private fun AVCaptureConnection.setVideoMirroringIfSupported(shouldMirrorFront: Boolean) {
    if (!respondsToSelector(NSSelectorFromString("isVideoMirroringSupported"))) return
    when {
        respondsToSelector(NSSelectorFromString("setVideoMirrored:")) -> this.setVideoMirrored(
            shouldMirrorFront
        )

        respondsToSelector(NSSelectorFromString("setAutomaticallyAdjustsVideoMirroring:")) -> {
            this.setAutomaticallyAdjustsVideoMirroring(false)
            if (respondsToSelector(NSSelectorFromString("setVideoMirrored:"))) {
                this.setVideoMirrored(shouldMirrorFront)
            }
        }
    }
}
