package com.thelightphone.wallet

/**
 * A stored loyalty / membership card.
 *
 * v1 only carries the display name; the code value and symbology (needed to
 * render the scannable barcode on the detail screen) will be added once the
 * barcode-rendering approach is settled — see CLAUDE.md "Open technical risks".
 */
data class LoyaltyCard(
    val name: String,
)
