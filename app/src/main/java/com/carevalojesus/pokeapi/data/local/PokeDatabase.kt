package com.carevalojesus.pokeapi.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FavoriteEntity::class,
        UserProfileEntity::class,
        OwnedPokemonEntity::class,
        UnlockedPokemonEntity::class,
        TradeEntity::class,
        PointEventEntity::class,
        MarketplaceItemEntity::class
    ],
    version = 7
)
abstract class PokeDatabase : RoomDatabase() {

    abstract fun favoriteDao(): FavoriteDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun ownedPokemonDao(): OwnedPokemonDao
    abstract fun unlockedPokemonDao(): UnlockedPokemonDao
    abstract fun tradeDao(): TradeDao
    abstract fun pointEventDao(): PointEventDao
    abstract fun marketplaceItemDao(): MarketplaceItemDao

    companion object {
        @Volatile
        private var INSTANCE: PokeDatabase? = null

        fun getInstance(context: Context): PokeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PokeDatabase::class.java,
                    "poke_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
