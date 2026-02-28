package com.carevalojesus.pokeapi.data.repository

enum class MarketplaceCategory {
    BACKGROUND,
    FRAME,
    AVATAR,
    TITLE,
    BADGE,
    CARD_THEME
}

data class MarketplaceItem(
    val id: String,
    val name: String,
    val description: String,
    val cost: Int,
    val category: MarketplaceCategory
)

object MarketplaceCatalog {
    val items: List<MarketplaceItem> = listOf(
        MarketplaceItem("item_01", "Poción Mini", "Cosmético de colección para tu vitrina de perfil.", 5, MarketplaceCategory.BADGE),
        MarketplaceItem("item_02", "Poké Llavero", "Coleccionable digital tipo llavero para inventario visual.", 5, MarketplaceCategory.BADGE),
        MarketplaceItem("item_03", "Sticker Eevee", "Sticker decorativo desbloqueado para tu colección.", 5, MarketplaceCategory.BADGE),
        MarketplaceItem("item_04", "Pulsera Trainer", "Accesorio cosmético de entrenador (solo visual).", 5, MarketplaceCategory.BADGE),
        MarketplaceItem("item_05", "Tarjeta Retro", "Tarjeta de colección estilo Kanto clásico.", 6, MarketplaceCategory.CARD_THEME),
        MarketplaceItem("item_06", "Pin Team Valor", "Pin digital del Team Valor para tu galería.", 6, MarketplaceCategory.BADGE),
        MarketplaceItem("item_07", "Pin Team Mystic", "Pin digital del Team Mystic para tu galería.", 6, MarketplaceCategory.BADGE),
        MarketplaceItem("item_08", "Pin Team Instinct", "Pin digital del Team Instinct para tu galería.", 6, MarketplaceCategory.BADGE),
        MarketplaceItem("item_09", "Fondo Pixel", "Tema de fondo estilo pixel art para pantalla de perfil.", 7, MarketplaceCategory.BACKGROUND),
        MarketplaceItem("item_10", "Marco Plateado", "Marco cosmético plateado para avatar de perfil.", 7, MarketplaceCategory.FRAME),
        MarketplaceItem("item_11", "Avatar Squirtle", "Avatar especial de Squirtle para tu perfil.", 7, MarketplaceCategory.AVATAR),
        MarketplaceItem("item_12", "Avatar Charmander", "Avatar especial de Charmander para tu perfil.", 7, MarketplaceCategory.AVATAR),
        MarketplaceItem("item_13", "Avatar Bulbasaur", "Avatar especial de Bulbasaur para tu perfil.", 7, MarketplaceCategory.AVATAR),
        MarketplaceItem("item_14", "Insignia Novato", "Insignia de colección visible en perfil.", 8, MarketplaceCategory.BADGE),
        MarketplaceItem("item_15", "Insignia Estratega", "Insignia de colección visible en perfil.", 8, MarketplaceCategory.BADGE),
        MarketplaceItem("item_16", "Insignia Leyenda", "Insignia de colección visible en perfil.", 8, MarketplaceCategory.BADGE),
        MarketplaceItem("item_17", "Marco Dorado", "Marco premium dorado para avatar de perfil.", 9, MarketplaceCategory.FRAME),
        MarketplaceItem("item_18", "Tema Nocturno", "Tema visual nocturno para personalización del perfil.", 9, MarketplaceCategory.BACKGROUND),
        MarketplaceItem("item_19", "Título Maestro", "Título de estatus para mostrar en tu perfil.", 10, MarketplaceCategory.TITLE),
        MarketplaceItem("item_20", "Trofeo Kanto", "Trofeo digital de temporada para tu colección.", 10, MarketplaceCategory.BADGE)
    )

    fun categoryLabel(category: MarketplaceCategory): String = when (category) {
        MarketplaceCategory.BACKGROUND -> "Fondo"
        MarketplaceCategory.FRAME -> "Marco"
        MarketplaceCategory.AVATAR -> "Avatar"
        MarketplaceCategory.TITLE -> "Título"
        MarketplaceCategory.BADGE -> "Emblema"
        MarketplaceCategory.CARD_THEME -> "Tarjeta"
    }
}
