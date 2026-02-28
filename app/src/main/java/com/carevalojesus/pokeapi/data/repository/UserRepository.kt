package com.carevalojesus.pokeapi.data.repository

import com.carevalojesus.pokeapi.data.firebase.FirebaseRepository
import com.carevalojesus.pokeapi.data.local.UserProfileDao
import com.carevalojesus.pokeapi.data.local.UserProfileEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class UserRepository(
    private val dao: UserProfileDao,
    private val firebaseRepository: FirebaseRepository
) {

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
        val total = firebaseRepository.addTrainerPoints(amount)
        dao.setPoints(total)
        return total
    }

    suspend fun addPoints(amount: Int) {
        ensureProfileExists()
        val total = firebaseRepository.addTrainerPoints(amount)
        dao.setPoints(total)
    }

    suspend fun getPoints(): Int {
        ensureProfileExists()
        val total = firebaseRepository.getTrainerPoints()
        dao.setPoints(total)
        return total
    }

    suspend fun spendPoints(amount: Int): Boolean {
        ensureProfileExists()
        if (amount <= 0) return true
        val (success, total) = firebaseRepository.spendTrainerPoints(amount)
        dao.setPoints(total)
        return success
    }

    suspend fun refundPoints(amount: Int) {
        if (amount <= 0) return
        val total = firebaseRepository.addTrainerPoints(amount)
        dao.setPoints(total)
    }

    suspend fun syncPointsFromRemote(): Int {
        ensureProfileExists()
        val total = firebaseRepository.getTrainerPoints()
        dao.setPoints(total)
        return total
    }

    suspend fun setLocalPoints(points: Int) {
        ensureProfileExists()
        dao.setPoints(points)
    }

    suspend fun updatePersonalInfo(firstName: String, lastName: String, birthDate: String, gender: String) {
        ensureProfileExists()
        dao.updatePersonalInfo(firstName, lastName, birthDate, gender)
    }

    suspend fun updateProfilePhoto(uri: String) {
        ensureProfileExists()
        dao.updateProfilePhoto(uri)
    }

    suspend fun getStarterChangesRemaining(): Int {
        ensureProfileExists()
        return dao.getStarterChangesRemaining() ?: 3
    }

    suspend fun changeStarter(pokemonId: Int): Boolean {
        val remaining = dao.getStarterChangesRemaining() ?: 3
        if (remaining <= 0) return false
        dao.changeStarter(pokemonId)
        return true
    }
}
