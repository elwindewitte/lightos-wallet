package com.thelightphone.wallet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.wallet.code.CodeEncoder
import com.thelightphone.wallet.code.CodeGeometry
import com.thelightphone.wallet.code.CodeType
import kotlin.math.floor

// The code is always drawn dark-on-light — that's what scanners expect — so these
// are fixed rather than themed. Only the screen around the surface follows the theme.
private val CODE_SURFACE = Color.White
private val CODE_INK = Color.Black

// Layout constants, in 40px grid units (the whole design sits on a 40px grid).
private const val PADDING_UNITS = 1f // 40px around the code, and code-to-label gap
private const val BARCODE_HEIGHT_UNITS = 6f // 240px
private const val QR_MAX_UNITS = 16f // 640px square cap

/**
 * The white code surface shown on the detail screen: the scannable code with its
 * human-readable value below it. Encodes [code] as [codeType] on the fly (never an
 * image); an unencodable value shows just the label so the screen still makes sense.
 */
@Composable
fun CardCodeSurface(
    code: String,
    codeType: CodeType,
    modifier: Modifier = Modifier,
) {
    val geometry = remember(code, codeType) {
        runCatching { CodeEncoder.encode(code, codeType) }.getOrNull()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CODE_SURFACE)
            .padding(PADDING_UNITS.gridUnitsAsDp()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (geometry) {
            is CodeGeometry.QrMatrix -> QrCodeView(geometry)
            is CodeGeometry.BarPattern -> BarcodeView(geometry)
            null -> {}
        }
        Spacer(Modifier.height(PADDING_UNITS.gridUnitsAsDp()))
        LightText(
            text = code,
            variant = LightTextVariant.Detail,
            color = CODE_INK,
        )
    }
}

/**
 * Draws a QR matrix as filled black squares, filling the canvas. The surrounding
 * 40px surface padding is the code's quiet zone — same as the barcode — so the white
 * margin around the QR is a consistent 40px.
 */
@Composable
private fun QrCodeView(matrix: CodeGeometry.QrMatrix) {
    Canvas(
        modifier = Modifier
            .widthIn(max = QR_MAX_UNITS.gridUnitsAsDp())
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        val moduleSize = floor(size.minDimension / matrix.size)
        if (moduleSize < 1f) return@Canvas
        val drawn = moduleSize * matrix.size
        val originX = (size.width - drawn) / 2f
        val originY = (size.height - drawn) / 2f
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix.isDark(x, y)) {
                    drawRect(
                        color = CODE_INK,
                        topLeft = Offset(originX + x * moduleSize, originY + y * moduleSize),
                        size = Size(moduleSize, moduleSize),
                    )
                }
            }
        }
    }
}

/** Draws a 1D barcode: vertical black bars filling the width, snapped to whole pixels. */
@Composable
private fun BarcodeView(pattern: CodeGeometry.BarPattern) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(BARCODE_HEIGHT_UNITS.gridUnitsAsDp()),
    ) {
        val total = pattern.totalModules
        val exact = size.width / total
        // Snap each module to a whole pixel for crisp edges; fall back to exact if the
        // code is so dense that a whole pixel per module would overflow the width.
        val moduleWidth = if (exact >= 1f) floor(exact) else exact
        var x = (size.width - moduleWidth * total) / 2f
        var isBar = true
        for (run in pattern.runs) {
            val runWidth = run * moduleWidth
            if (isBar) {
                drawRect(
                    color = CODE_INK,
                    topLeft = Offset(x, 0f),
                    size = Size(runWidth, size.height),
                )
            }
            x += runWidth
            isBar = !isBar
        }
    }
}
