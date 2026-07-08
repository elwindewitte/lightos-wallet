package com.thelightphone.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.LightBarcodeScanner
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBarcodeFormat
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.wallet.code.CodeType

/** A code captured by scanning or manual entry, ready to be named and saved. */
data class ScannedCode(val code: String, val codeType: CodeType)

/**
 * The symbologies we ask the camera to read — exactly the ones the tool can render,
 * so any successful scan maps to a [CodeType]. Formats ML Kit could detect but we
 * can't draw (UPC-E, Code 93, PDF417, …) are left out, so the scanner keeps looking
 * rather than capturing something we couldn't display back.
 */
private val WALLET_SCAN_FORMATS = setOf(
    LightBarcodeFormat.QR_CODE,
    LightBarcodeFormat.CODE_128,
    LightBarcodeFormat.EAN_13,
    LightBarcodeFormat.EAN_8,
    LightBarcodeFormat.UPC_A,
    LightBarcodeFormat.CODE_39,
    LightBarcodeFormat.ITF,
)

private fun LightBarcodeFormat.toCodeType(): CodeType? = when (this) {
    LightBarcodeFormat.QR_CODE -> CodeType.QR
    LightBarcodeFormat.CODE_128 -> CodeType.CODE_128
    LightBarcodeFormat.EAN_13 -> CodeType.EAN_13
    LightBarcodeFormat.EAN_8 -> CodeType.EAN_8
    LightBarcodeFormat.UPC_A -> CodeType.UPC_A
    LightBarcodeFormat.CODE_39 -> CodeType.CODE_39
    LightBarcodeFormat.ITF -> CodeType.ITF
    else -> null
}

/**
 * First step of adding a card: point the camera at the card's QR or barcode. The
 * built-in scanner auto-detects the symbology, so there's no "is this a QR or a
 * barcode?" prompt — the detected [LightBarcodeFormat] maps straight to a [CodeType].
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
                resultCallback = { code ->
                    navigateTo(
                        screenFactory = { PickCodeTypeScreen(it) },
                        resultCallback = { type -> goBack(ScannedCode(code, type)) },
                    )
                },
            )
        }

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LightBarcodeScanner(
                        title = "Scan card",
                        formats = WALLET_SCAN_FORMATS,
                        onScanned = { value, format ->
                            format.toCodeType()?.let { pendingScan = ScannedCode(value, it) }
                        },
                        onBack = { goBack() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                LightBottomBar(
                    items = listOf(
                        LightBarButton.Text(text = "ENTER MANUALLY", onClick = onEnterManually),
                    ),
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
