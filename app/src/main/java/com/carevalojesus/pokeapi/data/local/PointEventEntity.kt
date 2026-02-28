package com.carevalojesus.pokeapi.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "point_events")
data class PointEventEntity(
    @PrimaryKey val eventKey: String,
    val category: String,
    val pointsAwarded: Int,
    val createdAt: Long = System.currentTimeMillis()
)

