package com.thelightphone.wallet.code

/**
 * Turns a stored value + [CodeType] into drawable [CodeGeometry]. This is the single
 * entry point the UI uses; it never touches an image.
 */
object CodeEncoder {

    fun encode(code: String, type: CodeType): CodeGeometry = when (type) {
        CodeType.QR -> QrEncoder.encode(code)
        CodeType.CODE_128 -> Barcode1DEncoders.encodeCode128(code)
        CodeType.EAN_13 -> Barcode1DEncoders.encodeEan13(code)
        CodeType.EAN_8 -> Barcode1DEncoders.encodeEan8(code)
        CodeType.UPC_A -> Barcode1DEncoders.encodeUpcA(code)
        CodeType.CODE_39 -> Barcode1DEncoders.encodeCode39(code)
        CodeType.ITF -> Barcode1DEncoders.encodeItf(code)
    }
}
