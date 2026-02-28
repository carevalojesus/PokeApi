package com.carevalojesus.pokeapi.data.repository

enum class MissionBalancePreset {
    CASUAL,
    NORMAL,
    HARDCORE,
    EVENTO
}

data class MissionPointsConfig(
    val viewPokemonUnique: Int,
    val viewMilestone10: Int,
    val tradeCompleted: Int,
    val rewardQrScan: Int,
    val dailyLogin: Int,
    val profileCompleted: Int,
    val favoriteUnique: Int,
    val marketplaceCompleteBonus: Int
)

object MissionPoints {
    // Cambia este preset para rebalancear todo el sistema en un solo lugar.
    val activePreset: MissionBalancePreset = MissionBalancePreset.NORMAL

    private val casual = MissionPointsConfig(
        viewPokemonUnique = 2,
        viewMilestone10 = 10,
        tradeCompleted = 12,
        rewardQrScan = 8,
        dailyLogin = 5,
        profileCompleted = 15,
        favoriteUnique = 3,
        marketplaceCompleteBonus = 40
    )

    private val normal = MissionPointsConfig(
        viewPokemonUnique = 1,
        viewMilestone10 = 6,
        tradeCompleted = 8,
        rewardQrScan = 5,
        dailyLogin = 3,
        profileCompleted = 10,
        favoriteUnique = 2,
        marketplaceCompleteBonus = 30
    )

    private val hardcore = MissionPointsConfig(
        viewPokemonUnique = 1,
        viewMilestone10 = 4,
        tradeCompleted = 6,
        rewardQrScan = 3,
        dailyLogin = 2,
        profileCompleted = 7,
        favoriteUnique = 1,
        marketplaceCompleteBonus = 20
    )

    // Preset temporal para campañas: prioriza interacción social y escaneos.
    private val evento = MissionPointsConfig(
        viewPokemonUnique = 1,
        viewMilestone10 = 6,
        tradeCompleted = 16,
        rewardQrScan = 10,
        dailyLogin = 4,
        profileCompleted = 10,
        favoriteUnique = 2,
        marketplaceCompleteBonus = 50
    )

    val current: MissionPointsConfig
        get() = when (activePreset) {
            MissionBalancePreset.CASUAL -> casual
            MissionBalancePreset.NORMAL -> normal
            MissionBalancePreset.HARDCORE -> hardcore
            MissionBalancePreset.EVENTO -> evento
        }
}
