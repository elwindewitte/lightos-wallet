package com.thelightphone.wallet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access for stored cards. Reads are a [Flow] so the list updates reactively
 * as cards are added, renamed, or deleted; writes are `suspend` and run on Room's
 * own dispatcher. Cards are ordered case-insensitively by name to match the browse
 * list's sort.
 */
@Dao
internal interface CardDao {

    @Query("SELECT * FROM cards ORDER BY name COLLATE NOCASE, id")
    fun observeCards(): Flow<List<CardEntity>>

    @Insert
    suspend fun insert(card: CardEntity): Long

    @Query("UPDATE cards SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM cards WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
