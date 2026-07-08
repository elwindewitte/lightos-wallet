package com.thelightphone.wallet.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A stored loyalty card, one row per card. [codeType] holds a
 * [com.thelightphone.wallet.code.CodeType] name (e.g. "EAN_13"); the value is
 * rendered into a barcode / QR on the fly at display time, so nothing is stored as
 * an image. Mapped to/from the UI's `LoyaltyCard` by [CardRepository].
 */
@Entity(tableName = "cards")
internal data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val code: String,
    val codeType: String,
)
