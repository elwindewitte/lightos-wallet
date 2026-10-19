package com.thelightphone.wallet.data

import com.thelightphone.wallet.LoyaltyCard
import com.thelightphone.wallet.code.CodeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The single source of truth for stored cards, wrapping [CardDao] and translating
 * between the persisted [CardEntity] and the UI's [LoyaltyCard]. Held as a process
 * singleton so every screen shares one database instance; build it with
 * [getInstance] from a screen's `lightContext.buildDatabase(...)`.
 */
class CardRepository private constructor(database: WalletDatabase) {

    private val dao = database.cardDao()

    /** Live list of cards, ordered by name; re-emits on every add / rename / delete. */
    fun observeCards(): Flow<List<LoyaltyCard>> =
        dao.observeCards().map { entities -> entities.map { it.toCard() } }

    /** Add a card. Blank names are the caller's responsibility to reject upstream. */
    suspend fun addCard(name: String, code: String, codeType: CodeType) {
        dao.insert(CardEntity(name = name.trim(), code = code, codeType = codeType.name))
    }

    /**
     * Whether another card already has [name] (trimmed, case-insensitive). Pass the
     * card being renamed as [excludingId] so keeping its own name isn't a clash.
     */
    suspend fun isNameTaken(name: String, excludingId: Long = NO_CARD): Boolean =
        dao.nameExists(name.trim(), excludingId)

    /** Rename the card with [id]; a blank new name is ignored. */
    suspend fun renameCard(id: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        dao.rename(id, trimmed)
    }

    /** Delete the cards with the given [ids]; a no-op when empty. */
    suspend fun deleteCards(ids: List<Long>) {
        if (ids.isEmpty()) return
        dao.deleteByIds(ids)
    }

    private fun CardEntity.toCard(): LoyaltyCard =
        LoyaltyCard(id = id, name = name, code = code, codeType = CodeType.valueOf(codeType))

    companion object {
        const val DATABASE_NAME = "wallet.db"

        /** Matches no card (Room ids start at 1). */
        const val NO_CARD = 0L

        @Volatile
        private var instance: CardRepository? = null

        fun getInstance(databaseProvider: () -> WalletDatabase): CardRepository =
            instance ?: synchronized(this) {
                instance ?: CardRepository(databaseProvider()).also { instance = it }
            }
    }
}
