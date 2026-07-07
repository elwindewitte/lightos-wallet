package com.thelightphone.sdk.ui

import android.Manifest
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The symbologies the camera scanner can be asked to read. A tool-facing mirror of
 * ML Kit's `Barcode.FORMAT_*` constants so callers (which can't depend on ML Kit)
 * pick formats and receive the detected one without touching the ML Kit types.
 */
enum class LightBarcodeFormat {
    QR_CODE,
    CODE_128,
    CODE_39,
    CODE_93,
    CODABAR,
    EAN_13,
    EAN_8,
    ITF,
    UPC_A,
    UPC_E,
    DATA_MATRIX,
    PDF417,
    AZTEC,
}

private fun LightBarcodeFormat.toMlKitFormat(): Int = when (this) {
    LightBarcodeFormat.QR_CODE -> Barcode.FORMAT_QR_CODE
    LightBarcodeFormat.CODE_128 -> Barcode.FORMAT_CODE_128
    LightBarcodeFormat.CODE_39 -> Barcode.FORMAT_CODE_39
    LightBarcodeFormat.CODE_93 -> Barcode.FORMAT_CODE_93
    LightBarcodeFormat.CODABAR -> Barcode.FORMAT_CODABAR
    LightBarcodeFormat.EAN_13 -> Barcode.FORMAT_EAN_13
    LightBarcodeFormat.EAN_8 -> Barcode.FORMAT_EAN_8
    LightBarcodeFormat.ITF -> Barcode.FORMAT_ITF
    LightBarcodeFormat.UPC_A -> Barcode.FORMAT_UPC_A
    LightBarcodeFormat.UPC_E -> Barcode.FORMAT_UPC_E
    LightBarcodeFormat.DATA_MATRIX -> Barcode.FORMAT_DATA_MATRIX
    LightBarcodeFormat.PDF417 -> Barcode.FORMAT_PDF417
    LightBarcodeFormat.AZTEC -> Barcode.FORMAT_AZTEC
}

private fun mlKitFormatToLight(format: Int): LightBarcodeFormat? = when (format) {
    Barcode.FORMAT_QR_CODE -> LightBarcodeFormat.QR_CODE
    Barcode.FORMAT_CODE_128 -> LightBarcodeFormat.CODE_128
    Barcode.FORMAT_CODE_39 -> LightBarcodeFormat.CODE_39
    Barcode.FORMAT_CODE_93 -> LightBarcodeFormat.CODE_93
    Barcode.FORMAT_CODABAR -> LightBarcodeFormat.CODABAR
    Barcode.FORMAT_EAN_13 -> LightBarcodeFormat.EAN_13
    Barcode.FORMAT_EAN_8 -> LightBarcodeFormat.EAN_8
    Barcode.FORMAT_ITF -> LightBarcodeFormat.ITF
    Barcode.FORMAT_UPC_A -> LightBarcodeFormat.UPC_A
    Barcode.FORMAT_UPC_E -> LightBarcodeFormat.UPC_E
    Barcode.FORMAT_DATA_MATRIX -> LightBarcodeFormat.DATA_MATRIX
    Barcode.FORMAT_PDF417 -> LightBarcodeFormat.PDF417
    Barcode.FORMAT_AZTEC -> LightBarcodeFormat.AZTEC
    else -> null
}

/**
 * Full-screen QR scanner: live camera preview, dimmed overlay with viewfinder,
 * top-bar back, and [onScanned] with the decoded string.
 *
 * Host apps must declare [Manifest.permission.CAMERA].
 *
 * When handling [onScanned], defer navigation to a [LaunchedEffect] (or similar)
 * and pop the scanner before pushing the next screen, e.g. `goBack()` then `navigateTo(...)`.
 *
 * This reads only QR codes; use [LightBarcodeScanner] to read 1D barcodes or a mix
 * of formats and learn which one was detected.
 */
@Composable
fun LightQrCodeScanner(
    onScanned: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Scan QR Code",
    checkCameraPermission: suspend () -> Result<Boolean>,
    launchCameraPermissionRequest: suspend () -> Unit,
) {
    LightCodeScanner(
        onScanned = { value, _ -> onScanned(value) },
        onBack = onBack,
        formats = QR_ONLY_FORMATS,
        modifier = modifier,
        title = title,
        checkCameraPermission = checkCameraPermission,
        launchCameraPermissionRequest = launchCameraPermissionRequest,
    )
}

/**
 * Full-screen barcode scanner that reads any of the requested [formats] — QR and/or
 * 1D symbologies — and reports the decoded value together with the [LightBarcodeFormat]
 * it was detected as, so callers can pick the right handling automatically without a
 * "which kind of code is this?" prompt. Only the [formats] passed are looked for, so
 * a scan always resolves to one of them.
 *
 * Otherwise behaves like [LightQrCodeScanner]: host apps must declare
 * [Manifest.permission.CAMERA], and navigation on a hit should be deferred to a
 * [LaunchedEffect] that pops the scanner before pushing the next screen.
 */
@Composable
fun LightBarcodeScanner(
    onScanned: (value: String, format: LightBarcodeFormat) -> Unit,
    onBack: () -> Unit,
    formats: Set<LightBarcodeFormat>,
    modifier: Modifier = Modifier,
    title: String = "Scan barcode",
    checkCameraPermission: suspend () -> Result<Boolean>,
    launchCameraPermissionRequest: suspend () -> Unit,
) {
    LightCodeScanner(
        onScanned = onScanned,
        onBack = onBack,
        formats = formats,
        modifier = modifier,
        title = title,
        checkCameraPermission = checkCameraPermission,
        launchCameraPermissionRequest = launchCameraPermissionRequest,
    )
}

private val QR_ONLY_FORMATS = setOf(LightBarcodeFormat.QR_CODE)

/**
 * Shared scanner body behind [LightQrCodeScanner] and [LightBarcodeScanner]: permission
 * gating, the camera preview restricted to [formats], the viewfinder overlay, and the
 * back-bar. [onScanned] fires at most once with the decoded value and detected format.
 */
@Composable
private fun LightCodeScanner(
    onScanned: (value: String, format: LightBarcodeFormat) -> Unit,
    onBack: () -> Unit,
    formats: Set<LightBarcodeFormat>,
    modifier: Modifier,
    title: String,
    checkCameraPermission: suspend () -> Result<Boolean>,
    launchCameraPermissionRequest: suspend () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = LightThemeTokens.colors
    var launchedPermissionRequest by remember { mutableStateOf(false) }
    var uiState by remember { mutableStateOf(LightQrUiState.Loading) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val permissionCheck = checkCameraPermission()
            if (permissionCheck.isFailure) {
                uiState = LightQrUiState.PermissionError
            } else if (permissionCheck.getOrNull() == false) {
                if (!launchedPermissionRequest) {
                    launchCameraPermissionRequest()
                    launchedPermissionRequest = true
                } else {
                    uiState = LightQrUiState.PermissionDenied
                }
            } else {
                uiState = LightQrUiState.Active
            }
        }
    }

    val scannedOnce = remember { AtomicBoolean(false) }
    val onScannedState = rememberUpdatedState(onScanned)
    val onBackState = rememberUpdatedState(onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        if (uiState == LightQrUiState.Active) {
            CodeCameraPreview(
                onCode = { value, format ->
                    if (scannedOnce.compareAndSet(false, true)) {
                        onScannedState.value(value, format)
                    }
                },
                formats = formats,
                lifecycleOwner = lifecycleOwner,
                modifier = Modifier.fillMaxSize(),
            )
            QrViewfinderOverlay(
                frameColor = colors.content,
                scrimColor = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { onBackState.value() },
                    ),
                    center = LightTopBarCenter.Text(title),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )
            }

            if (uiState != LightQrUiState.Active) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (uiState == LightQrUiState.Loading) {
                        CircularProgressIndicator()
                    } else {
                        val message = if (uiState == LightQrUiState.PermissionDenied) {
                            "Camera permission is required to scan codes."
                        } else {
                            "Error: unable to request camera permission!"
                        }
                        LightText(
                            text = message,
                            variant = LightTextVariant.Copy,
                            align = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp()),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeCameraPreview(
    onCode: (value: String, format: LightBarcodeFormat) -> Unit,
    formats: Set<LightBarcodeFormat>,
    lifecycleOwner: LifecycleOwner,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val onCodeState = rememberUpdatedState(onCode)

    val barcodeScanner = remember(formats) {
        val mlKitFormats = formats.map { it.toMlKitFormat() }
        val options = BarcodeScannerOptions.Builder().apply {
            if (mlKitFormats.isNotEmpty()) {
                setBarcodeFormats(mlKitFormats.first(), *mlKitFormats.drop(1).toIntArray())
            }
        }.build()
        BarcodeScanning.getClient(options)
    }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        }
    }

    DisposableEffect(lifecycleOwner, cameraController, barcodeScanner) {
        val analyzer = MlKitAnalyzer(
            listOf(barcodeScanner),
            CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED,
            mainExecutor,
        ) { result ->
            if (result.getThrowable(barcodeScanner) != null) {
                return@MlKitAnalyzer
            }

            val barcodes = result.getValue(barcodeScanner) ?: return@MlKitAnalyzer
            if (barcodes.isEmpty()) {
                return@MlKitAnalyzer
            }

            val match = barcodes
                .asSequence()
                .mapNotNull { barcode ->
                    val value = (barcode.rawValue ?: barcode.displayValue)?.takeIf { it.isNotBlank() }
                    val format = mlKitFormatToLight(barcode.format)
                    if (value != null && format != null) value to format else null
                }
                .firstOrNull()

            if (match != null) {
                onCodeState.value(match.first, match.second)
            }
        }
        cameraController.setImageAnalysisAnalyzer(mainExecutor, analyzer)

        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
            barcodeScanner.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                controller = cameraController
                addOnAttachStateChangeListener(
                    object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(view: View) {
                            bindCamera(cameraController, lifecycleOwner)
                        }

                        override fun onViewDetachedFromWindow(view: View) {
                            cameraController.unbind()
                        }
                    },
                )
            }
        },
        update = { previewView ->
            if (previewView.isAttachedToWindow) {
                previewView.post {
                    bindCamera(cameraController, lifecycleOwner)
                }
            }
        },
    )
}

private fun bindCamera(
    cameraController: LifecycleCameraController,
    lifecycleOwner: LifecycleOwner,
) {
    runCatching {
        cameraController.bindToLifecycle(lifecycleOwner)
    }
}

@Composable
private fun QrViewfinderOverlay(
    frameColor: Color,
    scrimColor: Color,
    modifier: Modifier = Modifier,
    frameSizeFraction: Float = 0.62f,
) {
    Canvas(modifier = modifier) {
        val frameSize = size.minDimension * frameSizeFraction
        val left = (size.width - frameSize) / 2f
        val top = (size.height - frameSize) / 2f
        val right = left + frameSize
        val bottom = top + frameSize

        drawRect(scrimColor, topLeft = Offset.Zero, size = Size(size.width, top))
        drawRect(scrimColor, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
        drawRect(scrimColor, topLeft = Offset(0f, top), size = Size(left, frameSize))
        drawRect(scrimColor, topLeft = Offset(right, top), size = Size(size.width - right, frameSize))

        drawRoundRect(
            color = frameColor,
            topLeft = Offset(left, top),
            size = Size(frameSize, frameSize),
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 3f),
        )
    }
}

private enum class LightQrUiState {
    Loading, PermissionError, PermissionDenied, Active
}
