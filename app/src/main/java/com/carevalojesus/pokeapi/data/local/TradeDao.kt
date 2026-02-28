package com.carevalojesus.pokeapi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {

    @Query("SELECT * FROM trades ORDER BY createdAt DESC")
    fun getAll(): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE tradeId = :tradeId")
    suspend fun getById(tradeId: String): TradeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: TradeEntity)

    @Update
    suspend fun update(entity: TradeEntity)

    @Query("DELETE FROM trades")
    suspend fun deleteAll()
}
