package com.carevalojesus.pokeapi.ui.screens.reward

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RewardPayloadParserTest {

    @Test
    fun parseRewardPayload_withCodeId_returnsCampaignAndCode() {
        val parsed = parseRewardPayload("pokeapi://reward?campaignId=camp123&codeId=code789")

        requireNotNull(parsed)
        assertEquals("camp123", parsed.campaignId)
        assertEquals("code789", parsed.codeId)
    }

    @Test
    fun parseRewardPayload_legacyPayload_returnsCampaignOnly() {
        val parsed = parseRewardPayload("legacy-campaign-id")

        requireNotNull(parsed)
        assertEquals("legacy-campaign-id", parsed.campaignId)
        assertNull(parsed.codeId)
    }

    @Test
    fun parseRewardPayload_missingCampaignId_returnsNull() {
        val parsed = parseRewardPayload("pokeapi://reward?codeId=abc123")
        assertNull(parsed)
    }

    @Test
    fun parseRewardPayload_urlEncodedValues_areDecoded() {
        val parsed = parseRewardPayload("pokeapi://reward?campaignId=camp%201&codeId=code%202")

        requireNotNull(parsed)
        assertEquals("camp 1", parsed.campaignId)
        assertEquals("code 2", parsed.codeId)
    }
}
