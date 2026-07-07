package com.thelightphone.sdk

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.shared.LightServiceMethod
import com.thelightphone.sdk.shared.asKotlinResult
import com.thelightphone.sdk.ui.LightBarcodeFormat
import com.thelightphone.sdk.ui.LightBarcodeScanner
import com.thelightphone.sdk.ui.LightQrCodeScanner

/**
 * Wrapper for the UI library's LightQrCodeScanner that include the client library's functions for
 * checking and requesting the camera permission for the SDK server
 */
@Composable
fun LightQrCodeScanner(
    onScanned: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Scan QR Code",
) {
    val permissionLauncher = rememberPermissionRequestLauncher(Manifest.permission.CAMERA)
    LightQrCodeScanner(
        title = title,
        onScanned = onScanned,
        onBack = onBack,
        modifier = modifier,
        checkCameraPermission = {
            checkPermission(Manifest.permission.CAMERA).asKotlinResult
                .map { it.permissionResult == LightServiceMethod.GetPermission.Result.Granted }
        },
        launchCameraPermissionRequest = {
            permissionLauncher?.launch()
        }
    )
}

/**
 * Wrapper for the UI library's LightBarcodeScanner that includes the client library's
 * functions for checking and requesting the camera permission for the SDK server.
 * Reads any of the requested [formats] and reports which one was detected.
 */
@Composable
fun LightBarcodeScanner(
    onScanned: (value: String, format: LightBarcodeFormat) -> Unit,
    onBack: () -> Unit,
    formats: Set<LightBarcodeFormat>,
    modifier: Modifier = Modifier,
    title: String = "Scan barcode",
) {
    val permissionLauncher = rememberPermissionRequestLauncher(Manifest.permission.CAMERA)
    LightBarcodeScanner(
        title = title,
        onScanned = onScanned,
        onBack = onBack,
        formats = formats,
        modifier = modifier,
        checkCameraPermission = {
            checkPermission(Manifest.permission.CAMERA).asKotlinResult
                .map { it.permissionResult == LightServiceMethod.GetPermission.Result.Granted }
        },
        launchCameraPermissionRequest = {
            permissionLauncher?.launch()
        }
    )
}