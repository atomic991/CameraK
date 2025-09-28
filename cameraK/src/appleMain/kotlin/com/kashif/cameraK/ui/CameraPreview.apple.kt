package com.kashif.cameraK.ui


import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import com.kashif.cameraK.builder.CameraControllerBuilder
import com.kashif.cameraK.builder.createIOSCameraControllerBuilder
import com.kashif.cameraK.controller.CameraController
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplication
import platform.UIKit.UIDeviceOrientationDidChangeNotification
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.zlib.uLong

/**
 * iOS-specific implementation of [CameraPreview].
 *
 * @param modifier Modifier to be applied to the camera preview.
 * @param cameraConfiguration Lambda to configure the [CameraControllerBuilder].
 * @param onCameraControllerReady Callback invoked with the initialized [CameraController].
 */
@Composable
actual fun expectCameraPreview(
    modifier: Modifier,
    cameraConfiguration: CameraControllerBuilder.() -> Unit,
    onCameraControllerReady: (CameraController) -> Unit
) {

//    lockScreen(UIInterfaceOrientationMaskPortrait)

    val cameraController = remember {
        createIOSCameraControllerBuilder()
            .apply(cameraConfiguration)
            .build()
    }

    LaunchedEffect(cameraController) {
        onCameraControllerReady(cameraController)
    }

    DisposableEffect(Unit) {
        val notificationCenter = NSNotificationCenter.defaultCenter
        val observer = notificationCenter.addObserverForName(
            UIDeviceOrientationDidChangeNotification,
            null,
            null
        ) { _ ->
            cameraController.currentVideoOrientation()?.let {
                cameraController.getCameraPreviewLayer()?.connection?.videoOrientation = it
            }
        }

        onDispose {
            notificationCenter.removeObserver(observer)
        }
    }

    UIKitViewController(
        factory = { cameraController },
        modifier = modifier,
    )
}

fun lockScreen(orientationIOS: ULong) {
    val scenes = UIApplication.sharedApplication.connectedScenes
    val scene = scenes.firstOrNull { it is UIWindowScene } as? UIWindowScene
    if (scene == null) {
        println("No active UIWindowScene found. Orientation update skipped.")
        return
    }

    val geometryPreferences = UIWindowSceneGeometryPreferencesIOS(orientationIOS)
    scene.requestGeometryUpdateWithPreferences(geometryPreferences) { error ->
        if (error != null) {
            println(error.localizedDescription)
        }
    }
}