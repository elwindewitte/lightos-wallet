package com.thelightphone.wallet

import com.thelightphone.wallet.code.CodeType

/**
 * A stored loyalty / membership card.
 *
 * The card is pure data: [code] is the raw value and [codeType] its symbology, from
 * which the detail screen renders the scannable barcode / QR on the fly (see
 * [com.thelightphone.wallet.code.CodeEncoder]) — nothing is stored as an image.
 *
 * [id] is the stable database row id; the list keys rename / delete / selection on
 * it (rather than the name), so two cards may share a name without ambiguity.
 */
data class LoyaltyCard(
    val id: Long,
    val name: String,
    val code: String,
    val codeType: CodeType,
)
