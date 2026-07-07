package com.thelightphone.wallet

import com.thelightphone.wallet.code.CodeType

/**
 * A stored loyalty / membership card.
 *
 * The card is pure data: [code] is the raw value and [codeType] its symbology, from
 * which the detail screen renders the scannable barcode / QR on the fly (see
 * [com.thelightphone.wallet.code.CodeEncoder]) — nothing is stored as an image.
 */
data class LoyaltyCard(
    val name: String,
    val code: String,
    val codeType: CodeType,
)
