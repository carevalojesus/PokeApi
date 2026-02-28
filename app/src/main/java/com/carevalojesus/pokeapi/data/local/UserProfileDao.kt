package com.carevalojesus.pokeapi.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// Single-row table by design: all UPDATE queries intentionally omit WHERE userId
// because only one profile row exists at a time (the currently logged-in user).
@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profile LIMIT 1")
    fun getProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile LIMIT 1")
    suspend fun getProfileOnce(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(profile: UserProfileEntity)

    @Update
    suspend fun update(profile: UserProfileEntity)

    @Query("UPDATE user_profile SET points = points + :amount")
    suspend fun addPoints(amount: Int)

    @Query("UPDATE user_profile SET points = points - :amount WHERE points >= :amount")
    suspend fun spendPoints(amount: Int): Int

    @Query("UPDATE user_profile SET starterChosen = 1, starterPokemonId = :pokemonId")
    suspend fun setStarter(pokemonId: Int)

    @Query("SELECT points FROM user_profile LIMIT 1")
    suspend fun getPoints(): Int?

    @Query("UPDATE user_profile SET points = :points")
    suspend fun setPoints(points: Int)

    @Query("UPDATE user_profile SET firstName = :firstName, lastName = :lastName, birthDate = :birthDate, gender = :gender")
    suspend fun updatePersonalInfo(firstName: String, lastName: String, birthDate: String, gender: String)

    @Query("UPDATE user_profile SET profilePhotoUri = :uri")
    suspend fun updateProfilePhoto(uri: String)

    @Query("UPDATE user_profile SET starterPokemonId = :pokemonId, starterChangesRemaining = starterChangesRemaining - 1")
    suspend fun changeStarter(pokemonId: Int)

    @Query("SELECT starterChangesRemaining FROM user_profile LIMIT 1")
    suspend fun getStarterChangesRemaining(): Int?

    @Query("UPDATE user_profile SET starterChangesRemaining = :remaining")
    suspend fun setStarterChangesRemaining(remaining: Int)

    @Query("DELETE FROM user_profile")
    suspend fun deleteAll()
}
