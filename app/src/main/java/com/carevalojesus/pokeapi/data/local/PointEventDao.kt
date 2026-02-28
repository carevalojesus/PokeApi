package com.carevalojesus.pokeapi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PointEventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: PointEventEntity): Long

    @Query("SELECT COUNT(*) FROM point_events WHERE eventKey LIKE :prefix || '%'")
    suspend fun countByPrefix(prefix: String): Int

    @Query("DELETE FROM point_events")
    suspend fun deleteAll()
}

