package com.thelightphone.wallet.code

/**
 * The symbology a card's stored value is drawn in. We render the visual code from
 * the value + this type at display time (see [CodeEncoder]); nothing is stored as
 * an image.
 *
 * [QR] is the only 2D symbology so far; the rest are 1D barcodes. The set is
 * deliberately small for v1 and grows as real loyalty cards turn up types we don't
 * yet handle.
 */
enum class CodeType {
    QR,
    CODE_128,
    EAN_13,
    EAN_8,
    UPC_A,
    CODE_39,
    ITF,
}

/** True for 2D symbologies, which render as a module matrix rather than bars. */
val CodeType.is2d: Boolean
    get() = this == CodeType.QR
