package com.thelightphone.wallet.code

/**
 * Checks and tidies a typed card number before it's saved, so a saved card always
 * renders and its printed number matches its bars.
 *
 * Plain Kotlin, no Android APIs (see [Barcode1DEncoders] for why).
 */
object CodeInput {

    sealed interface Check {
        /** [CodeEncoder] can draw this value as this type. */
        data object Ok : Check

        /**
         * A full-length EAN/UPC whose last (check) digit doesn't match the others —
         * almost certainly a typo. [expected] is the digit the others call for.
         */
        data class WrongCheckDigit(val expected: Char) : Check

        /** This type can't encode the value at all (wrong characters or length). */
        data object Unencodable : Check
    }

    fun check(value: String, type: CodeType): Check {
        val dataLen = eanDataLength(type)
        if (dataLen != null && value.length == dataLen + 1 && value.all { it.isDigit() }) {
            val expected = Barcode1DEncoders.eanCheckDigit(value, dataLen)
            if (value.last() != expected) return Check.WrongCheckDigit(expected)
        }
        return if (runCatching { CodeEncoder.encode(value, type) }.isSuccess) Check.Ok
        else Check.Unencodable
    }

    /** The types worth offering for [value]: everything except those that can't encode it. */
    fun typesFor(value: String): List<CodeType> =
        CodeType.entries.filter { check(value, it) != Check.Unencodable }

    /**
     * The value exactly as the code carries it: EAN/UPC get their check digit appended
     * when it was left off, Code 39 is uppercased. Anything else (or anything that
     * doesn't fit) is returned unchanged.
     */
    fun normalize(value: String, type: CodeType): String {
        val dataLen = eanDataLength(type)
        return when {
            dataLen != null && value.length == dataLen && value.all { it.isDigit() } ->
                value + Barcode1DEncoders.eanCheckDigit(value, dataLen)
            type == CodeType.CODE_39 -> value.uppercase()
            else -> value
        }
    }

    /** Digits before the check digit, for the symbologies that have one. */
    private fun eanDataLength(type: CodeType): Int? = when (type) {
        CodeType.EAN_13 -> 12
        CodeType.EAN_8 -> 7
        CodeType.UPC_A -> 11
        else -> null
    }
}
