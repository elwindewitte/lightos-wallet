package com.thelightphone.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.LightQrCodeScanner
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBarcodeFormats
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.wallet.code.CodeType

/**
 * A code captured by scanning or manual entry. [name] is already filled in when
 * manual entry ran the name step itself; a camera scan leaves it null, and the
 * caller asks for a name after the scanner has closed.
 */
data class ScannedCode(val code: String, val codeType: CodeType, val name: String? = null)

/**
 * The symbologies we ask the camera to read — exactly the ones the tool can render,
 * so any successful scan maps to a [CodeType]. Formats ML Kit could detect but we
 * can't draw (UPC-E, Code 93, PDF417, …) are left out, so the scanner keeps looking
 * rather than capturing something we couldn't display back.
 */
private val WALLET_SCAN_FORMATS = listOf(
    LightBarcodeFormats.QR,
    LightBarcodeFormats.CODE_128,
    LightBarcodeFormats.EAN_13,
    LightBarcodeFormats.EAN_8,
    LightBarcodeFormats.UPC_A,
    LightBarcodeFormats.CODE_39,
    LightBarcodeFormats.ITF,
)

/** Maps a detected [LightBarcodeFormats] constant to the [CodeType] we render it as. */
private fun codeTypeForFormat(format: Int): CodeType? = when (format) {
    LightBarcodeFormats.QR -> CodeType.QR
    LightBarcodeFormats.CODE_128 -> CodeType.CODE_128
    LightBarcodeFormats.EAN_13 -> CodeType.EAN_13
    LightBarcodeFormats.EAN_8 -> CodeType.EAN_8
    LightBarcodeFormats.UPC_A -> CodeType.UPC_A
    LightBarcodeFormats.CODE_39 -> CodeType.CODE_39
    LightBarcodeFormats.ITF -> CodeType.ITF
    else -> null
}

/**
 * First step of adding a card: point the camera at the card's QR or barcode. The
 * built-in scanner auto-detects the symbology, so there's no "is this a QR or a
 * barcode?" prompt — the detected format maps straight to a [CodeType].
 *
 * Returns the captured [ScannedCode] via [goBack]; the caller then asks for a name
 * and saves. Backing out (top-bar or hardware back) returns nothing, cancelling the
 * add. The bottom bar's "Enter manually" is the fallback for cards the camera can't
 * read (damaged codes, unusual symbologies, or no physical card to hand); it walks
 * through typing the value and picking the type, resolving to the same [ScannedCode].
 */
class ScanCardScreen(
    sealedActivity: SealedLightActivity,
) : SimpleLightScreen<ScannedCode>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        var pendingScan by remember { mutableStateOf<ScannedCode?>(null) }

        val onEnterManually: () -> Unit = {
            navigateTo(
                screenFactory = { EnterCodeScreen(it) },
                resultCallback = { card -> goBack(card) },
            )
        }

        LightTheme(colors = themeColors) {
            // The scanner (camera + dimming scrim) fills the whole screen and the bottom
            // bar overlays it, so the scrim bleeds behind the transparent bar instead of
            // stopping at a solid strip above it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightQrCodeScanner(
                    title = "Scan card",
                    formats = WALLET_SCAN_FORMATS,
                    // onScanned only fires for QR when onScannedBarcode is unset; we use
                    // the rich callback so every format reports its detected symbology.
                    onScanned = {},
                    onScannedBarcode = { scanned ->
                        codeTypeForFormat(scanned.format)?.let {
                            pendingScan = ScannedCode(scanned.value, it)
                        }
                    },
                    onBack = { goBack() },
                    modifier = Modifier.fillMaxSize(),
                )

                LightBottomBar(
                    items = listOf(
                        LightBarButton.Text(text = "ENTER MANUALLY", onClick = onEnterManually),
                    ),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        // Pop the scanner (releasing the camera) before the caller pushes the name
        // screen, per the SDK's camera-scanner guidance.
        LaunchedEffect(pendingScan) {
            val scanned = pendingScan ?: return@LaunchedEffect
            goBack(scanned)
        }
    }
}
