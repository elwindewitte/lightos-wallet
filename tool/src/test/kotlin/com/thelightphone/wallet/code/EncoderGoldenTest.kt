package com.thelightphone.wallet.code

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests pinning each encoder's output to a golden module pattern.
 *
 * The golden values were verified bit-for-bit against independent reference
 * implementations — `qrcode` (Python) for QR in byte mode, and `python-barcode`
 * for the 1D symbologies — so a scanner will decode them to the stored value. These
 * assertions guard against future regressions without needing those tools present.
 */
class EncoderGoldenTest {

    // 1D pattern expanded to a 1/0 module string (1 = bar), starting with a bar.
    private fun bars(code: String, type: CodeType): String {
        val p = CodeEncoder.encode(code, type) as CodeGeometry.BarPattern
        val sb = StringBuilder()
        var bar = true
        for (run in p.runs) {
            repeat(run) { sb.append(if (bar) '1' else '0') }
            bar = !bar
        }
        return sb.toString()
    }

    // QR matrix flattened row-major to a 1/0 string (1 = dark module).
    private fun qr(code: String): String {
        val m = CodeEncoder.encode(code, CodeType.QR) as CodeGeometry.QrMatrix
        val sb = StringBuilder()
        for (y in 0 until m.size) for (x in 0 until m.size) sb.append(if (m.isDark(x, y)) '1' else '0')
        return sb.toString()
    }

    @Test fun ean13() = assertEquals(
        "10101011110011001000010101100110011001011100101010111001010111001011100101000010100001101100101",
        bars("2616115044662", CodeType.EAN_13),
    )

    @Test fun ean8() = assertEquals(
        "1010001011010111101111010110111010101001110111001010001001011100101",
        bars("96385074", CodeType.EAN_8),
    )

    @Test fun upcA() = assertEquals(
        "10100011010111101010111100011010001101000110101010110110011101001100110101110010011101101100101",
        bars("036000291452", CodeType.UPC_A),
    )

    @Test fun code128Numeric() = assertEquals(
        "110100111001001100001010001100010110001010001100010010011011100010111000110101100011101011",
        bars("7238401152", CodeType.CODE_128),
    )

    @Test fun code128Alphanumeric() = assertEquals(
        "1101001000010100011000100010110001000100011010011011100100111001101100111001011001011100101100001001100011101011",
        bars("ABC-123", CodeType.CODE_128),
    )

    // Real Shell GO+ card: 17 digits (odd), exercising the Code C + trailing Code B switch.
    @Test fun code128OddDigits() = assertEquals(
        "1101001110010110000100100100011001100111001010010001100110000100101011001110011001011100110001000101011110111010011101100111011000101100011101011",
        bars("70041804751219410", CodeType.CODE_128),
    )

    // Real Albert Heijn bonuskaart: valid EAN-13.
    @Test fun ean13Real() = assertEquals(
        "10101011110010011010011100001010001011001110101010111010010010001110100100010011011001010000101",
        bars("2620694989726", CodeType.EAN_13),
    )

    @Test fun code39() = assertEquals(
        "1000101110111010101011100011101011101110101000101011101000101110111010001010111010111000101011101110111000101010101000111010111011101000111010101011100011101010100010111011101",
        bars("JMB123456", CodeType.CODE_39),
    )

    // Real Bouwmaat card: Code 39, no checksum.
    @Test fun code39Real() = assertEquals(
        "10001011101110101011100010111010101110001011101010100011101110101110100010101110111011100010101011101000101011101110100011101010101000111011101010100011101110101110100010101110100010111011101",
        bars("9901315001", CodeType.CODE_39),
    )

    @Test fun itf() = assertEquals(
        "101011101000101011100011101110100010100011101000111000101010001010111000111011101",
        bars("12345678", CodeType.ITF),
    )

    @Test fun qrByteMode() {
        val expected =
            "111111100000001111111100000100011001000001101110101000101011101101110101010101011101101110101010101011101100000101001001000001111111101010101111111000000001100000000000101111100101001111100011011000000100011111101000110000110101000011111011001000100101010011101001111001000000000001011000011111111111100010100100100100000101010000100110101110101110111101011101110101111010010100101110101000101100000100000100001100100100111111101110011101010"
        assertEquals(21 * 21, expected.length)
        assertEquals(expected, qr("2742004106927"))
    }
}
