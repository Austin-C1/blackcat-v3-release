package com.wrbug.polymarketbot.service.market

import com.wrbug.polymarketbot.api.GammaEventMarketItem
import com.wrbug.polymarketbot.api.GammaSearchEventItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarketBettingDateFilterTest {

    @Test
    fun `extracts date from plain telegram query`() {
        val parsedIso = MarketBettingTelegramCommandParser.parse("Celtics vs 76ers 2026-04-26")
        val parsedShort = MarketBettingTelegramCommandParser.parse("Celtics vs 76ers 4/26")

        assertEquals("Celtics vs 76ers", parsedIso?.query)
        assertEquals("2026-04-26", parsedIso?.date)
        assertEquals("Celtics vs 76ers", parsedShort?.query)
        assertEquals("2026-04-26", parsedShort?.date)
    }

    @Test
    fun `matches event by exact date`() {
        val event = GammaSearchEventItem(
            slug = "nba-bos-phi-2026-04-26",
            startDate = "2026-04-20T14:05:37.802584Z",
            endDate = "2026-04-26T23:00:00Z"
        )

        assertTrue(MarketBettingDateFilter.matches(event, "2026-04-26"))
        assertFalse(MarketBettingDateFilter.matches(event, "2026-04-28"))
    }

    @Test
    fun `matches market by exact date`() {
        val market = GammaEventMarketItem(
            slug = "nba-bos-phi-2026-04-26-moneyline",
            startDate = "2026-04-26T23:00:00Z",
            endDate = "2026-04-27T02:00:00Z"
        )

        assertTrue(MarketBettingDateFilter.matches(market, "2026-04-26"))
        assertFalse(MarketBettingDateFilter.matches(market, "2026-04-28"))
    }
}
