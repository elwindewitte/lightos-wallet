package com.thelightphone.wallet.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The tool's local database. Built once via `lightContext.buildDatabase(...)` and
 * wrapped by [CardRepository]; see the authenticator example for the same pattern.
 */
@Database(entities = [CardEntity::class], version = 1, exportSchema = true)
abstract class WalletDatabase : RoomDatabase() {
    internal abstract fun cardDao(): CardDao
}
