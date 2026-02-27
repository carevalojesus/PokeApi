package com.carevalojesus.pokeapi.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val userId: String,
    val starterChosen: Boolean = false,
    val starterPokemonId: Int = 0,
    val points: Int = 0,
    val firstName: String = "",
    val lastName: String = "",
    val birthDate: String = "",
    val gender: String = "",
    val profilePhotoUri: String = ""
)
