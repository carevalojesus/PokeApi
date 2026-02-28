package com.carevalojesus.pokeapi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PointEventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: PointEventEntity): Long

    @Query("SELECT * FROM point_events ORDER BY createdAt DESC")
    fun getAll(): Flow<List<PointEventEntity>>

    @Query("SELECT COUNT(*) FROM point_events WHERE eventKey LIKE :prefix || '%'")
    suspend fun countByPrefix(prefix: String): Int

    @Query("DELETE FROM point_events")
    suspend fun deleteAll()
}

