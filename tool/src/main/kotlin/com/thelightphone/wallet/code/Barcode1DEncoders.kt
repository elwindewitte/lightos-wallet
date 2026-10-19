package com.thelightphone.wallet.code

import com.thelightphone.wallet.code.CodeGeometry.BarPattern

/**
 * Encoders for the 1D symbologies this tool supports. Each produces a [BarPattern]
 * (run-length bar/space widths starting with a bar) from a stored value; the Canvas
 * view scales that to fill the screen.
 *
 * Everything is plain Kotlin — no dependency, no Android APIs — so it stays within
 * the SDK allow-list. See [QrEncoder] for the 2D case.
 *
 * Symbology references are the public specs (GS1 for EAN/UPC, ISO/IEC 15417 for
 * Code 128, ISO/IEC 16388 for Code 39 and Interleaved 2 of 5).
 */
object Barcode1DEncoders {

    /** Thrown when a value can't be represented in the requested symbology. */
    class EncodeException(message: String) : IllegalArgumentException(message)

    // ---- EAN / UPC --------------------------------------------------------------

    // 1 = bar, 0 = space. Left "odd" (L), left "even" (G), and right (R) digit codes.
    private val L_CODE = arrayOf(
        "0001101", "0011001", "0010011", "0111101", "0100011",
        "0110001", "0101111", "0111011", "0110111", "0001011",
    )
    private val G_CODE = arrayOf(
        "0100111", "0110011", "0011011", "0100001", "0011101",
        "0111001", "0000101", "0010001", "0001001", "0010111",
    )
    private val R_CODE = arrayOf(
        "1110010", "1100110", "1101100", "1000010", "1011100",
        "1001110", "1010000", "1000100", "1001000", "1110100",
    )

    // For EAN-13, the first digit selects which of the left six digits use L vs G.
    private val EAN13_PARITY = arrayOf(
        "LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG",
        "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL",
    )

    fun encodeEan13(value: String): BarPattern {
        val d = normalizeEan(value, dataLen = 12, name = "EAN-13")
        val parity = EAN13_PARITY[d[0] - '0']
        val sb = StringBuilder()
        sb.append("101") // start guard
        for (i in 0 until 6) {
            val digit = d[1 + i] - '0'
            sb.append(if (parity[i] == 'L') L_CODE[digit] else G_CODE[digit])
        }
        sb.append("01010") // center guard
        for (i in 0 until 6) sb.append(R_CODE[d[7 + i] - '0'])
        sb.append("101") // end guard
        return BarPattern(bitsToRuns(sb))
    }

    fun encodeEan8(value: String): BarPattern {
        val d = normalizeEan(value, dataLen = 7, name = "EAN-8")
        val sb = StringBuilder()
        sb.append("101")
        for (i in 0 until 4) sb.append(L_CODE[d[i] - '0'])
        sb.append("01010")
        for (i in 4 until 8) sb.append(R_CODE[d[i] - '0'])
        sb.append("101")
        return BarPattern(bitsToRuns(sb))
    }

    fun encodeUpcA(value: String): BarPattern {
        val d = normalizeEan(value, dataLen = 11, name = "UPC-A")
        val sb = StringBuilder()
        sb.append("101")
        for (i in 0 until 6) sb.append(L_CODE[d[i] - '0'])
        sb.append("01010")
        for (i in 6 until 12) sb.append(R_CODE[d[i] - '0'])
        sb.append("101")
        return BarPattern(bitsToRuns(sb))
    }

    // Validates digits, appends the check digit if the caller omitted it, and returns
    // the full code (dataLen + 1 digits). EAN/UPC all share the mod-10 check with
    // alternating 3/1 weights; the leftmost weight differs by symbology length.
    private fun normalizeEan(value: String, dataLen: Int, name: String): String {
        if (value.any { !it.isDigit() }) throw EncodeException("$name accepts digits only")
        val d = when (value.length) {
            dataLen -> value + eanCheckDigit(value, dataLen)
            dataLen + 1 -> value
            else -> throw EncodeException("$name needs $dataLen or ${dataLen + 1} digits, got ${value.length}")
        }
        return d
    }

    internal fun eanCheckDigit(data: String, dataLen: Int): Char {
        // Weight is 3 for the rightmost data digit and alternates leftward.
        var sum = 0
        for (i in 0 until dataLen) {
            val fromRight = dataLen - 1 - i
            val weight = if (fromRight % 2 == 0) 3 else 1
            sum += (data[i] - '0') * weight
        }
        return '0' + (10 - sum % 10) % 10
    }

    // ---- Code 128 ---------------------------------------------------------------

    // Element widths (bar,space,...) for each of the 107 symbol values, sharing the
    // standard Code 128 table. Values 103/104/105 are Start A/B/C; 106 is Stop.
    private val CODE128 = arrayOf(
        "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312",
        "132212", "221213", "221312", "231212", "112232", "122132", "122231", "113222",
        "123122", "123221", "223211", "221132", "221231", "213212", "223112", "312131",
        "311222", "321122", "321221", "312212", "322112", "322211", "212123", "212321",
        "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
        "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121",
        "313121", "211331", "231131", "213113", "213311", "213131", "311123", "311321",
        "331121", "312113", "312311", "332111", "314111", "221411", "431111", "111224",
        "111422", "121124", "121421", "141122", "141221", "112214", "112412", "122114",
        "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
        "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112",
        "421211", "212141", "214121", "412121", "111143", "111341", "131141", "114113",
        "114311", "411113", "411311", "113141", "114131", "311141", "411131", "211412",
        "211214", "211232", "2331112",
    )

    private const val CODE128_START_B = 104
    private const val CODE128_START_C = 105
    private const val CODE128_CODE_B = 100
    private const val CODE128_STOP = 106

    fun encodeCode128(value: String): BarPattern {
        if (value.isEmpty()) throw EncodeException("Code 128 needs a non-empty value")
        val values = ArrayList<Int>()

        if (value.all { it.isDigit() } && value.length >= 2) {
            // Code C packs digit pairs two-per-symbol; an odd tail falls back to Code B.
            values.add(CODE128_START_C)
            val evenLen = value.length - value.length % 2
            var i = 0
            while (i < evenLen) {
                values.add((value[i] - '0') * 10 + (value[i + 1] - '0'))
                i += 2
            }
            if (i < value.length) {
                values.add(CODE128_CODE_B)
                values.add(codeBValue(value[i]))
            }
        } else {
            values.add(CODE128_START_B)
            for (c in value) values.add(codeBValue(c))
        }

        // Modulo-103 checksum: start weight 1, then each value weighted by position.
        var sum = values[0]
        for (p in 1 until values.size) sum += values[p] * p
        values.add(sum % 103)
        values.add(CODE128_STOP)

        val sb = StringBuilder()
        for (v in values) appendWidths(sb, CODE128[v], startBar = true)
        return BarPattern(bitsToRuns(sb))
    }

    private fun codeBValue(c: Char): Int {
        if (c.code !in 32..126) throw EncodeException("Code 128 can't encode '$c'")
        return c.code - 32
    }

    // ---- Code 39 ----------------------------------------------------------------

    // 9 elements per character (bar,space,...); N = narrow, W = wide. '*' is the
    // start/stop guard and can't appear in the data.
    private val CODE39 = mapOf(
        '0' to "NNNWWNWNN", '1' to "WNNWNNNNW", '2' to "NNWWNNNNW", '3' to "WNWWNNNNN",
        '4' to "NNNWWNNNW", '5' to "WNNWWNNNN", '6' to "NNWWWNNNN", '7' to "NNNWNNWNW",
        '8' to "WNNWNNWNN", '9' to "NNWWNNWNN",
        'A' to "WNNNNWNNW", 'B' to "NNWNNWNNW", 'C' to "WNWNNWNNN", 'D' to "NNNNWWNNW",
        'E' to "WNNNWWNNN", 'F' to "NNWNWWNNN", 'G' to "NNNNNWWNW", 'H' to "WNNNNWWNN",
        'I' to "NNWNNWWNN", 'J' to "NNNNWWWNN",
        'K' to "WNNNNNNWW", 'L' to "NNWNNNNWW", 'M' to "WNWNNNNWN", 'N' to "NNNNWNNWW",
        'O' to "WNNNWNNWN", 'P' to "NNWNWNNWN", 'Q' to "NNNNNNWWW", 'R' to "WNNNNNWWN",
        'S' to "NNWNNNWWN", 'T' to "NNNNWNWWN",
        'U' to "WWNNNNNNW", 'V' to "NWWNNNNNW", 'W' to "WWWNNNNNN", 'X' to "NWNNWNNNW",
        'Y' to "WWNNWNNNN", 'Z' to "NWWNWNNNN",
        '-' to "NWNNNNWNW", '.' to "WWNNNNWNN", ' ' to "NWWNNNNWN", '$' to "NWNWNWNNN",
        '/' to "NWNWNNNWN", '+' to "NWNNNWNWN", '%' to "NNNWNWNWN", '*' to "NWNNWNWNN",
    )

    fun encodeCode39(value: String): BarPattern {
        val data = value.uppercase()
        val sb = StringBuilder()
        appendCode39Char(sb, '*')
        for (c in data) {
            if (c == '*' || c !in CODE39) throw EncodeException("Code 39 can't encode '$c'")
            sb.append('0') // narrow inter-character space
            appendCode39Char(sb, c)
        }
        sb.append('0')
        appendCode39Char(sb, '*')
        return BarPattern(bitsToRuns(sb))
    }

    private fun appendCode39Char(sb: StringBuilder, c: Char) {
        val pattern = CODE39.getValue(c)
        var bar = true
        for (nw in pattern) {
            val width = if (nw == 'W') 3 else 1
            repeat(width) { sb.append(if (bar) '1' else '0') }
            bar = !bar
        }
    }

    // ---- Interleaved 2 of 5 -----------------------------------------------------

    // 5 elements per digit; two wide, three narrow. Odd digits ride the bars, even
    // digits the spaces, interleaved pair by pair.
    private val ITF = arrayOf(
        "NNWWN", "WNNNW", "NWNNW", "WWNNN", "NNWNW",
        "WNWNN", "NWWNN", "NNNWW", "WNNWN", "NWNWN",
    )

    fun encodeItf(value: String): BarPattern {
        if (value.any { !it.isDigit() }) throw EncodeException("ITF accepts digits only")
        if (value.length % 2 != 0) throw EncodeException("ITF needs an even number of digits")
        val sb = StringBuilder()
        sb.append("1010") // start: narrow bar, space, bar, space
        var i = 0
        while (i < value.length) {
            val barsPat = ITF[value[i] - '0']
            val spacesPat = ITF[value[i + 1] - '0']
            for (k in 0 until 5) {
                val barW = if (barsPat[k] == 'W') 3 else 1
                repeat(barW) { sb.append('1') }
                val spaceW = if (spacesPat[k] == 'W') 3 else 1
                repeat(spaceW) { sb.append('0') }
            }
            i += 2
        }
        sb.append("111") // stop: wide bar (3)...
        sb.append("0")   // ...narrow space...
        sb.append("1")   // ...narrow bar
        return BarPattern(bitsToRuns(sb))
    }

    // ---- Shared helpers ---------------------------------------------------------

    // Expands a Code 128 style width string (e.g. "212222") into 1/0 bits, starting
    // with a bar and alternating.
    private fun appendWidths(sb: StringBuilder, widths: String, startBar: Boolean) {
        var bar = startBar
        for (ch in widths) {
            val w = ch - '0'
            repeat(w) { sb.append(if (bar) '1' else '0') }
            bar = !bar
        }
    }

    // Converts a 1/0 module string (starting with a bar) into alternating run widths.
    private fun bitsToRuns(bits: CharSequence): IntArray {
        require(bits.isNotEmpty() && bits[0] == '1') { "pattern must start with a bar" }
        val runs = ArrayList<Int>()
        var count = 1
        for (i in 1 until bits.length) {
            if (bits[i] == bits[i - 1]) {
                count++
            } else {
                runs.add(count)
                count = 1
            }
        }
        runs.add(count)
        return runs.toIntArray()
    }
}
