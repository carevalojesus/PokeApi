package com.carevalojesus.pokeapi.data.repository

import com.carevalojesus.pokeapi.data.firebase.MarketplaceItemFirebase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketplaceRepositorySyncPlanTest {

    @Test
    fun buildMarketplaceSyncPlan_reconcilesStaleAndEquippedItems() {
        val localIds = setOf("bg_forest", "avatar_red", "stale_item")
        val remoteItems = listOf(
            MarketplaceItemFirebase(
                itemId = "bg_forest",
                category = "BACKGROUND",
                equipped = false,
                purchasedAt = 1_000L
            ),
            MarketplaceItemFirebase(
                itemId = "avatar_red",
                category = "AVATAR",
                equipped = false,
                purchasedAt = 1_500L
            ),
            MarketplaceItemFirebase(
                itemId = "avatar_blue",
                category = "AVATAR",
                equipped = true,
                purchasedAt = 2_000L
            )
        )

        val plan = buildMarketplaceSyncPlan(localIds, remoteItems)

        assertEquals(setOf("stale_item"), plan.idsToDelete)
        assertEquals("avatar_blue", plan.equippedByCategory["AVATAR"])
        assertTrue(plan.remoteEntities.any { it.itemId == "avatar_red" && !it.equipped })
        assertTrue(plan.remoteEntities.any { it.itemId == "avatar_blue" && it.equipped })
    }
}
