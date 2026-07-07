package com.thelightphone.wallet.code

/**
 * The drawable shape of a code, produced by [CodeEncoder] and consumed by the
 * Canvas views. Pure geometry — no colours, no sizing — so the same result scales
 * to whatever the screen allows.
 */
sealed interface CodeGeometry {

    /**
     * A square QR grid. [size] is the side length in modules; [modules] is a
     * row-major [size] x [size] grid where `true` is a dark module. Does not
     * include the surrounding quiet zone — the view adds margin as needed.
     */
    class QrMatrix(
        val size: Int,
        val modules: BooleanArray,
    ) : CodeGeometry {
        /** Dark/light state of the module at ([x], [y]); out-of-bounds reads light. */
        fun isDark(x: Int, y: Int): Boolean =
            x in 0 until size && y in 0 until size && modules[y * size + x]

        init {
            require(modules.size == size * size) { "modules must be size*size" }
        }
    }

    /**
     * A 1D barcode as a run-length list of bar/space widths in modules. The first
     * run is a bar; runs then alternate space/bar. [totalModules] is their sum —
     * the natural width the view scales to fill the available space.
     */
    class BarPattern(
        val runs: IntArray,
    ) : CodeGeometry {
        val totalModules: Int = runs.sum()

        init {
            require(runs.isNotEmpty()) { "barcode pattern is empty" }
        }
    }
}
