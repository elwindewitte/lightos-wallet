package com.thelightphone.wallet

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persistent per-card usage stats, backed by `lightContext.dataStore`.
 *
 * For now we only track how many times each card has been opened, so a future
 * view can sort by most-used. Counts survive across sessions (unlike the
 * in-memory recents order). Cards are keyed by name — the only stable identifier
 * the v1 [LoyaltyCard] model has; a rename would start a fresh count.
 */
class CardStatsStore(private val dataStore: DataStore<Preferences>) {

    /** Map of card name -> number of times it has been opened. */
    val openCounts: Flow<Map<String, Int>> = dataStore.data.map { prefs ->
        prefs.asMap()
            .mapNotNull { (key, value) ->
                val name = key.name
                    .takeIf { it.startsWith(OPEN_COUNT_PREFIX) }
                    ?.removePrefix(OPEN_COUNT_PREFIX)
                val count = value as? Int
                if (name != null && count != null) name to count else null
            }
            .toMap()
    }

    /** Increment the open count for [cardName] by one. */
    suspend fun recordOpen(cardName: String) {
        dataStore.edit { prefs ->
            val key = intPreferencesKey(OPEN_COUNT_PREFIX + cardName)
            prefs[key] = (prefs[key] ?: 0) + 1
        }
    }

    private companion object {
        const val OPEN_COUNT_PREFIX = "card_open_count."
    }
}
