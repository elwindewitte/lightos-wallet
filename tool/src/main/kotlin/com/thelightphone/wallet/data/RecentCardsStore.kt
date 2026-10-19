package com.thelightphone.wallet.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Card ids in most-recently-opened order, kept in the tool's DataStore so Search's
 * recents survive a restart. Stored as a comma-separated string; ids of deleted cards
 * are dropped by the caller (and ignored on display anyway).
 */
class RecentCardsStore(private val dataStore: DataStore<Preferences>) {

    suspend fun load(): List<Long> =
        dataStore.data.first()[KEY].orEmpty()
            .split(',')
            .mapNotNull { it.toLongOrNull() }

    suspend fun save(ids: List<Long>) {
        dataStore.edit { it[KEY] = ids.joinToString(",") }
    }

    private companion object {
        val KEY = stringPreferencesKey("recent_card_ids")
    }
}
