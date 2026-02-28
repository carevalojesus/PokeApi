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
    val category: MarketplaceCategory,
    val imageUrl: String,
    val animatedImageUrl: String
)

object MarketplaceCatalog {
    private const val STATIC_ARTWORK_BASE =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/home"
    private const val ANIMATED_ARTWORK_BASE =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/versions/generation-v/black-white/animated"

    val items: List<MarketplaceItem> = listOf(
        MarketplaceItem("item_01", "Poción Mini", "Cosmético de colección para tu vitrina de perfil.", 5, MarketplaceCategory.BADGE, "$STATIC_ARTWORK_BASE/174.png", "$ANIMATED_ARTWORK_BASE/174.gif"),
        MarketplaceItem("item_02", "Poké Llavero", "Coleccionable digital tipo llavero para inventario visual.", 5, MarketplaceCategory.BADGE, "$STATIC_ARTWORK_BASE/25.png", "$ANIMATED_ARTWORK_BASE/25.gif"),
        MarketplaceItem("item_03", "Sticker Eevee", "Sticker decorativo desbloqueado para tu colección.", 5, MarketplaceCategory.BADGE, "$STATIC_ARTWORK_BASE/133.png", "$ANIMATED_ARTWORK_BASE/133.gif"),
        MarketplaceItem("item_04", "Pulsera Trainer", "Accesorio cosmético de entrenador (solo visual).", 5, MarketplaceCategory.BADGE, "$STATIC_ARTWORK_BASE/52.png", "$ANIMATED_ARTWORK_BASE/52.gif"),
        MarketplaceItem("item_05", "Tarjeta Retro", "Tarjeta de colección estilo Kanto clásico.", 6, MarketplaceCategory.CARD_THEME, "$STATIC_ARTWORK_BASE/143.png", "$ANIMATED_ARTWORK_BASE/143.gif"),
        MarketplaceItem("item_06", "Pin Team Valor", "Pin digital del Team Valor para tu galería.", 6, MarketplaceCategory.BADGE, "$STATIC_ARTWORK_BASE/146.png", "$ANIMATED_ARTWORK_BASE/146.gif"),
        MarketplaceItem("item_07", "Pin Team Mystic", "Pin digital del Team Mystic para tu galería.", 6, MarketplaceCategory.BADGE, "$STATIC_ARTWORK_BASE/144.png", "$ANIMATED_ARTWORK_BASE/144.gif"),
        MarketplaceItem("item_08", "Pin Team Instinct", "Pin digital del Team Instinct para tu galería.", 6, MarketplaceCategory.BADGE, "$STATIC_ARTWORK_BASE/145.png", "$ANIMATED_ARTWORK_BASE/145.gif"),
        MarketplaceItem("item_09", "Fondo Pixel", "Tema de fondo estilo pixel art para pantalla de perfil.", 7, MarketplaceCategory.BACKGROUND, "$STATIC_ARTWORK_BASE/94.png", "$ANIMATED_ARTWORK_BASE/94.gif"),
        MarketplaceItem("item_10", "Marco Plateado", "Marco cosmético plateado para avatar de perfil.", 7, MarketplaceCategory.FRAME, "$STATIC_ARTWORK_BASE/91.png", "$ANIMATED_ARTWORK_BASE/91.gif"),
        MarketplaceItem("item_11", "Avatar Squirtle", "Avatar especial de Squirtle para tu perfil.", 7, MarketplaceCategory.AVATAR, "$STATIC_ARTWORK_BASE/7.png", "$ANIMATED_ARTWORK_BASE/7.gif"),
        MarketplaceItem("item_12", "Avatar Charmander", "Avatar especial de Charmander para tu perfil.", 7, MarketplaceCategory.AVATAR, "$STATIC_ARTWORK_BASE/4.png", "$ANIMATED_ARTWORK_BASE/4.gif"),
        MarketplaceItem("item_13", "Avatar Bulbasaur", "Avatar especial de Bulbasaur para tu perfil.", 7, MarketplaceCategory.AVATAR, "$STATIC_ARTWORK_BASE/1.png", "$ANIMATED_ARTWORK_BASE/1.gif"),
        MarketplaceItem("item_14", "Insignia Novato", "Insignia de colección visible en perfil.", 8, MarketplaceCategory.BADGE, "$STATIC_ARTWORK_BASE/16.png", "$ANIMATED_ARTWORK_BASE/16.gif"),
        MarketplaceItem("item_15", "Insignia Estratega", "Insignia de colección visible en perfil.", 8, MarketplaceCategory.BADGE, "$STATIC_ARTWORK_BASE/65.png", "$ANIMATED_ARTWORK_BASE/65.gif"),
        MarketplaceItem("item_16", "Insignia Leyenda", "Insignia de colección visible en perfil.", 8, MarketplaceCategory.BADGE, "$STATIC_ARTWORK_BASE/150.png", "$ANIMATED_ARTWORK_BASE/150.gif"),
        MarketplaceItem("item_17", "Marco Dorado", "Marco premium dorado para avatar de perfil.", 9, MarketplaceCategory.FRAME, "$STATIC_ARTWORK_BASE/6.png", "$ANIMATED_ARTWORK_BASE/6.gif"),
        MarketplaceItem("item_18", "Tema Nocturno", "Tema visual nocturno para personalización del perfil.", 9, MarketplaceCategory.BACKGROUND, "$STATIC_ARTWORK_BASE/197.png", "$ANIMATED_ARTWORK_BASE/197.gif"),
        MarketplaceItem("item_19", "Título Maestro", "Título de estatus para mostrar en tu perfil.", 10, MarketplaceCategory.TITLE, "$STATIC_ARTWORK_BASE/249.png", "$ANIMATED_ARTWORK_BASE/249.gif"),
        MarketplaceItem("item_20", "Trofeo Kanto", "Trofeo digital de temporada para tu colección.", 10, MarketplaceCategory.BADGE, "$STATIC_ARTWORK_BASE/151.png", "$ANIMATED_ARTWORK_BASE/151.gif")
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
