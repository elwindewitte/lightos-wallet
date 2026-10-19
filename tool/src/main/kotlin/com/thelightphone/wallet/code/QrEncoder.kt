/*
 * Portions of this file are a port of the QR Code generator library.
 *
 * Copyright (c) Project Nayuki. (MIT License)
 * https://www.nayuki.io/page/qr-code-generator-library
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * - The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 * - The Software is provided "as is", without warranty of any kind, express or
 *   implied, including but not limited to the warranties of merchantability,
 *   fitness for a particular purpose and noninfringement. In no event shall the
 *   authors or copyright holders be liable for any claim, damages or other
 *   liability, whether in an action of contract, tort or otherwise, arising from,
 *   out of or in connection with the Software or the use or other dealings in the
 *   Software.
 */
package com.thelightphone.wallet.code

import kotlin.math.abs
import kotlin.math.max

/**
 * A minimal, dependency-free QR Code generator producing a [CodeGeometry.QrMatrix].
 *
 * This is a compact Kotlin port of Nayuki's "QR Code generator library" reference
 * implementation, trimmed to what this tool needs: a single byte-mode segment
 * (UTF-8), automatic version selection, error-correction boosting, and full mask
 * selection. Byte mode encodes any string, which is all a loyalty code requires.
 *
 * Vendored as source (not a Gradle dependency) because no barcode/QR generation
 * library is on the Light SDK allow-list, and this file uses only plain Kotlin —
 * no Android APIs — so it passes the plugin guardrails.
 *
 * Source: https://www.nayuki.io/page/qr-code-generator-library
 * License: MIT (Copyright © Project Nayuki) — full notice at the top of this file.
 */
object QrEncoder {

    /** Error-correction level; higher levels tolerate more damage but hold less data. */
    enum class Ecc(val formatBits: Int) {
        LOW(1),
        MEDIUM(0),
        QUARTILE(3),
        HIGH(2),
    }

    private const val MIN_VERSION = 1
    private const val MAX_VERSION = 40

    private const val PENALTY_N1 = 3
    private const val PENALTY_N2 = 3
    private const val PENALTY_N3 = 40
    private const val PENALTY_N4 = 10

    /**
     * Encodes [text] as a QR Code, preferring [minEcc] but boosting the level for
     * free whenever the chosen version has spare capacity. Throws if the text is
     * too long to fit in the largest QR version.
     */
    fun encode(text: String, minEcc: Ecc = Ecc.MEDIUM): CodeGeometry.QrMatrix {
        val data = text.toByteArray(Charsets.UTF_8)

        // Pick the smallest version that fits the data at the requested ECC level.
        var version = MIN_VERSION
        while (true) {
            val capacityBits = numDataCodewords(version, minEcc) * 8
            val usedBits = 4 + charCountBits(version) + data.size * 8
            if (usedBits <= capacityBits) break
            require(version < MAX_VERSION) { "Data too long for a QR code" }
            version++
        }

        // Boost the ECC level as far as the data still fits (better scannability).
        var ecc = minEcc
        for (candidate in listOf(Ecc.MEDIUM, Ecc.QUARTILE, Ecc.HIGH)) {
            val usedBits = 4 + charCountBits(version) + data.size * 8
            if (candidate.ordinal > ecc.ordinal && usedBits <= numDataCodewords(version, candidate) * 8) {
                ecc = candidate
            }
        }

        val dataCodewords = buildDataCodewords(data, version, ecc)
        return buildMatrix(version, ecc, dataCodewords)
    }

    // Byte-mode character-count indicator width for the given version.
    private fun charCountBits(version: Int): Int = if (version <= 9) 8 else 16

    private fun buildDataCodewords(data: ByteArray, version: Int, ecc: Ecc): ByteArray {
        val bb = BitBuffer()
        bb.appendBits(0x4, 4) // byte mode indicator
        bb.appendBits(data.size, charCountBits(version))
        for (b in data) bb.appendBits(b.toInt() and 0xFF, 8)

        val capacityBits = numDataCodewords(version, ecc) * 8
        bb.appendBits(0, minOf(4, capacityBits - bb.size)) // terminator
        bb.appendBits(0, (8 - bb.size % 8) % 8) // pad to a byte boundary
        var pad = 0xEC
        while (bb.size < capacityBits) {
            bb.appendBits(pad, 8)
            pad = pad xor (0xEC xor 0x11)
        }

        val result = ByteArray(bb.size / 8)
        for (i in 0 until bb.size) {
            if (bb[i]) result[i ushr 3] = (result[i ushr 3].toInt() or (0x80 ushr (i and 7))).toByte()
        }
        return result
    }

    // ---- Codeword capacity math -------------------------------------------------

    private fun numRawDataModules(version: Int): Int {
        var result = (16 * version + 128) * version + 64
        if (version >= 2) {
            val numAlign = version / 7 + 2
            result -= (25 * numAlign - 10) * numAlign - 55
            if (version >= 7) result -= 36
        }
        return result
    }

    private fun numDataCodewords(version: Int, ecc: Ecc): Int =
        numRawDataModules(version) / 8 -
            ECC_CODEWORDS_PER_BLOCK[ecc.ordinal][version] *
            NUM_ERROR_CORRECTION_BLOCKS[ecc.ordinal][version]

    // ---- Reed-Solomon error correction & interleaving --------------------------

    private fun addEccAndInterleave(data: ByteArray, version: Int, ecc: Ecc): ByteArray {
        val numBlocks = NUM_ERROR_CORRECTION_BLOCKS[ecc.ordinal][version]
        val blockEccLen = ECC_CODEWORDS_PER_BLOCK[ecc.ordinal][version]
        val rawCodewords = numRawDataModules(version) / 8
        val numShortBlocks = numBlocks - rawCodewords % numBlocks
        val shortBlockLen = rawCodewords / numBlocks

        val blocks = Array(numBlocks) { ByteArray(0) }
        val rsDiv = reedSolomonComputeDivisor(blockEccLen)
        var k = 0
        for (i in 0 until numBlocks) {
            val datLen = shortBlockLen - blockEccLen + (if (i < numShortBlocks) 0 else 1)
            val dat = data.copyOfRange(k, k + datLen)
            k += datLen
            val block = dat.copyOf(shortBlockLen + 1)
            val eccBytes = reedSolomonComputeRemainder(dat, rsDiv)
            System.arraycopy(eccBytes, 0, block, block.size - blockEccLen, eccBytes.size)
            blocks[i] = block
        }

        val result = ByteArray(rawCodewords)
        var idx = 0
        for (i in blocks[0].indices) {
            for (j in blocks.indices) {
                // The gap cell of a short block sits at index (shortBlockLen - blockEccLen); skip it.
                if (i != shortBlockLen - blockEccLen || j >= numShortBlocks) {
                    result[idx] = blocks[j][i]
                    idx++
                }
            }
        }
        return result
    }

    private fun reedSolomonComputeDivisor(degree: Int): ByteArray {
        val result = ByteArray(degree)
        result[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in result.indices) {
                result[j] = reedSolomonMultiply(result[j].toInt() and 0xFF, root).toByte()
                if (j + 1 < result.size) result[j] = (result[j].toInt() xor result[j + 1].toInt()).toByte()
            }
            root = reedSolomonMultiply(root, 0x02)
        }
        return result
    }

    private fun reedSolomonComputeRemainder(data: ByteArray, divisor: ByteArray): ByteArray {
        val result = ByteArray(divisor.size)
        for (b in data) {
            val factor = (b.toInt() xor result[0].toInt()) and 0xFF
            System.arraycopy(result, 1, result, 0, result.size - 1)
            result[result.size - 1] = 0
            for (i in result.indices) {
                result[i] = (result[i].toInt() xor reedSolomonMultiply(divisor[i].toInt() and 0xFF, factor)).toByte()
            }
        }
        return result
    }

    private fun reedSolomonMultiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z and 0xFF
    }

    // ---- Matrix construction ----------------------------------------------------

    private class Grid(val size: Int) {
        val modules = BooleanArray(size * size)
        val isFunction = BooleanArray(size * size)
        fun get(x: Int, y: Int): Boolean = modules[y * size + x]
        fun set(x: Int, y: Int, dark: Boolean) { modules[y * size + x] = dark }
        fun fn(x: Int, y: Int, dark: Boolean) {
            modules[y * size + x] = dark
            isFunction[y * size + x] = true
        }
    }

    private fun buildMatrix(version: Int, ecc: Ecc, dataCodewords: ByteArray): CodeGeometry.QrMatrix {
        val size = version * 4 + 17
        val g = Grid(size)

        drawFunctionPatterns(g, version, ecc)
        val allCodewords = addEccAndInterleave(dataCodewords, version, ecc)
        drawCodewords(g, allCodewords)

        var bestMask = 0
        var minPenalty = Int.MAX_VALUE
        for (mask in 0 until 8) {
            applyMask(g, mask)
            drawFormatBits(g, ecc, mask)
            val penalty = penaltyScore(g)
            if (penalty < minPenalty) {
                minPenalty = penalty
                bestMask = mask
            }
            applyMask(g, mask) // undo (mask is its own inverse)
        }
        applyMask(g, bestMask)
        drawFormatBits(g, ecc, bestMask)

        return CodeGeometry.QrMatrix(size, g.modules)
    }

    private fun drawFunctionPatterns(g: Grid, version: Int, ecc: Ecc) {
        val size = g.size
        for (i in 0 until size) {
            g.fn(6, i, i % 2 == 0)
            g.fn(i, 6, i % 2 == 0)
        }
        drawFinderPattern(g, 3, 3)
        drawFinderPattern(g, size - 4, 3)
        drawFinderPattern(g, 3, size - 4)

        val alignPos = alignmentPatternPositions(version, size)
        val n = alignPos.size
        for (i in 0 until n) {
            for (j in 0 until n) {
                val corner = (i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)
                if (!corner) drawAlignmentPattern(g, alignPos[i], alignPos[j])
            }
        }

        drawFormatBits(g, ecc, 0)
        drawVersion(g, version)
    }

    private fun drawFinderPattern(g: Grid, x: Int, y: Int) {
        for (dy in -4..4) {
            for (dx in -4..4) {
                val dist = max(abs(dx), abs(dy))
                val xx = x + dx
                val yy = y + dy
                if (xx in 0 until g.size && yy in 0 until g.size) {
                    g.fn(xx, yy, dist != 2 && dist != 4)
                }
            }
        }
    }

    private fun drawAlignmentPattern(g: Grid, x: Int, y: Int) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                g.fn(x + dx, y + dy, max(abs(dx), abs(dy)) != 1)
            }
        }
    }

    private fun alignmentPatternPositions(version: Int, size: Int): IntArray {
        if (version == 1) return IntArray(0)
        val numAlign = version / 7 + 2
        val step = if (version == 32) 26 else (version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2
        val result = IntArray(numAlign)
        result[0] = 6
        var pos = size - 7
        var i = numAlign - 1
        while (i >= 1) {
            result[i] = pos
            pos -= step
            i--
        }
        return result
    }

    private fun drawFormatBits(g: Grid, ecc: Ecc, mask: Int) {
        val size = g.size
        val data = ecc.formatBits shl 3 or mask
        var rem = data
        for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        val bits = (data shl 10 or rem) xor 0x5412

        for (i in 0..5) g.fn(8, i, getBit(bits, i))
        g.fn(8, 7, getBit(bits, 6))
        g.fn(8, 8, getBit(bits, 7))
        g.fn(7, 8, getBit(bits, 8))
        for (i in 9..14) g.fn(14 - i, 8, getBit(bits, i))

        for (i in 0..7) g.fn(size - 1 - i, 8, getBit(bits, i))
        for (i in 8..14) g.fn(8, size - 15 + i, getBit(bits, i))
        g.fn(8, size - 8, true) // always dark
    }

    private fun drawVersion(g: Grid, version: Int) {
        if (version < 7) return
        val size = g.size
        var rem = version
        for (i in 0 until 12) rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
        val bits = version shl 12 or rem
        for (i in 0 until 18) {
            val bit = getBit(bits, i)
            val a = size - 11 + i % 3
            val b = i / 3
            g.fn(a, b, bit)
            g.fn(b, a, bit)
        }
    }

    private fun drawCodewords(g: Grid, data: ByteArray) {
        val size = g.size
        var i = 0
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) {
                for (j in 0 until 2) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!g.isFunction[y * size + x] && i < data.size * 8) {
                        g.set(x, y, getBit(data[i ushr 3].toInt(), 7 - (i and 7)))
                        i++
                    }
                }
            }
            right -= 2
        }
    }

    private fun applyMask(g: Grid, mask: Int) {
        val size = g.size
        for (y in 0 until size) {
            for (x in 0 until size) {
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    7 -> ((x + y) % 2 + x * y % 3) % 2 == 0
                    else -> false
                }
                if (invert && !g.isFunction[y * size + x]) {
                    g.modules[y * size + x] = !g.modules[y * size + x]
                }
            }
        }
    }

    // ---- Mask penalty scoring ---------------------------------------------------

    private fun penaltyScore(g: Grid): Int {
        val size = g.size
        var result = 0

        // Rule 1 (rows) + rule 3 finder-like patterns.
        for (y in 0 until size) {
            var runColor = false
            var runLen = 0
            val history = IntArray(7)
            for (x in 0 until size) {
                if (g.get(x, y) == runColor) {
                    runLen++
                    if (runLen == 5) result += PENALTY_N1 else if (runLen > 5) result++
                } else {
                    finderPenaltyAddHistory(runLen, history, size)
                    if (!runColor) result += finderPenaltyCountPatterns(history) * PENALTY_N3
                    runColor = g.get(x, y)
                    runLen = 1
                }
            }
            result += finderPenaltyTerminateAndCount(runColor, runLen, history, size) * PENALTY_N3
        }
        // Rule 1 (columns) + rule 3.
        for (x in 0 until size) {
            var runColor = false
            var runLen = 0
            val history = IntArray(7)
            for (y in 0 until size) {
                if (g.get(x, y) == runColor) {
                    runLen++
                    if (runLen == 5) result += PENALTY_N1 else if (runLen > 5) result++
                } else {
                    finderPenaltyAddHistory(runLen, history, size)
                    if (!runColor) result += finderPenaltyCountPatterns(history) * PENALTY_N3
                    runColor = g.get(x, y)
                    runLen = 1
                }
            }
            result += finderPenaltyTerminateAndCount(runColor, runLen, history, size) * PENALTY_N3
        }

        // Rule 2: 2x2 blocks of one color.
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val c = g.get(x, y)
                if (c == g.get(x + 1, y) && c == g.get(x, y + 1) && c == g.get(x + 1, y + 1)) {
                    result += PENALTY_N2
                }
            }
        }

        // Rule 4: overall dark/light balance.
        var dark = 0
        for (b in g.modules) if (b) dark++
        val total = size * size
        val k = (abs(dark * 20 - total * 10) + total - 1) / total - 1
        result += k * PENALTY_N4
        return result
    }

    private fun finderPenaltyCountPatterns(history: IntArray): Int {
        val n = history[1]
        val core = n > 0 && history[2] == n && history[3] == n * 3 && history[4] == n && history[5] == n
        return (if (core && history[0] >= n * 4 && history[6] >= n) 1 else 0) +
            (if (core && history[6] >= n * 4 && history[0] >= n) 1 else 0)
    }

    private fun finderPenaltyTerminateAndCount(currentRunColor: Boolean, currentRunLen: Int, history: IntArray, size: Int): Int {
        var runLen = currentRunLen
        if (currentRunColor) {
            finderPenaltyAddHistory(runLen, history, size)
            runLen = 0
        }
        runLen += size
        finderPenaltyAddHistory(runLen, history, size)
        return finderPenaltyCountPatterns(history)
    }

    private fun finderPenaltyAddHistory(currentRunLen: Int, history: IntArray, size: Int) {
        var runLen = currentRunLen
        if (history[0] == 0) runLen += size // add light border to the first run
        System.arraycopy(history, 0, history, 1, history.size - 1)
        history[0] = runLen
    }

    private fun getBit(x: Int, i: Int): Boolean = ((x ushr i) and 1) != 0

    // ---- A simple MSB-first bit buffer -----------------------------------------

    private class BitBuffer {
        private var bits = BooleanArray(64)
        var size = 0
            private set

        operator fun get(index: Int): Boolean = bits[index]

        fun appendBits(value: Int, len: Int) {
            require(len in 0..31 && value ushr len == 0) { "value out of range for $len bits" }
            if (size + len > bits.size) {
                var cap = bits.size
                while (cap < size + len) cap *= 2
                bits = bits.copyOf(cap)
            }
            for (i in len - 1 downTo 0) {
                bits[size] = ((value ushr i) and 1) != 0
                size++
            }
        }
    }

    // ---- Per-version ECC tables (QR spec, via Nayuki) --------------------------
    // Index [eccLevel][version]; version 0 is unused padding (-1).

    private val ECC_CODEWORDS_PER_BLOCK = arrayOf(
        intArrayOf(-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30), // Low
        intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28), // Medium
        intArrayOf(-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30), // Quartile
        intArrayOf(-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30), // High
    )

    private val NUM_ERROR_CORRECTION_BLOCKS = arrayOf(
        intArrayOf(-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25), // Low
        intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49), // Medium
        intArrayOf(-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68), // Quartile
        intArrayOf(-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81), // High
    )
}
