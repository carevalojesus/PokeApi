package com.carevalojesus.pokeapi.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FavoriteEntity::class], version = 1)
abstract class PokeDatabase : RoomDatabase() {

    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile
        private var INSTANCE: PokeDatabase? = null

        fun getInstance(context: Context): PokeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PokeDatabase::class.java,
                    "poke_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
