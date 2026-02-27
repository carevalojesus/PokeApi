package com.carevalojesus.pokeapi.data.repository

import com.carevalojesus.pokeapi.data.local.UserProfileDao
import com.carevalojesus.pokeapi.data.local.UserProfileEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class UserRepository(private val dao: UserProfileDao) {

    fun getProfile(): Flow<UserProfileEntity?> = dao.getProfile()

    suspend fun ensureProfileExists(): UserProfileEntity {
        val existing = dao.getProfileOnce()
        if (existing != null) return existing
        val profile = UserProfileEntity(userId = UUID.randomUUID().toString())
        dao.insert(profile)
        return dao.getProfileOnce()!!
    }

    suspend fun setStarter(pokemonId: Int) {
        ensureProfileExists()
        dao.setStarter(pokemonId)
    }

    suspend fun addPointsAndGetTotal(amount: Int): Int {
        ensureProfileExists()
        dao.addPoints(amount)
        return dao.getPoints() ?: 0
    }

    suspend fun updatePersonalInfo(firstName: String, lastName: String, birthDate: String, gender: String) {
        ensureProfileExists()
        dao.updatePersonalInfo(firstName, lastName, birthDate, gender)
    }

    suspend fun updateProfilePhoto(uri: String) {
        ensureProfileExists()
        dao.updateProfilePhoto(uri)
    }
}
