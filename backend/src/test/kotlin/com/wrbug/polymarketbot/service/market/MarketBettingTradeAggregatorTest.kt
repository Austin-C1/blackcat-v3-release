package com.wrbug.polymarketbot.service.market

import com.wrbug.polymarketbot.api.UserActivityResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarketBettingTradeAggregatorTest {

    @Test
    fun `summarizes traded shares by asset instead of current holder balance`() {
        val trades = listOf(
            trade(asset = "token-a", size = 100.0, price = 0.25),
            trade(asset = "token-a", size = 50.5, price = 0.30),
            trade(asset = "token-b", size = 20.25, price = 0.70),
            trade(asset = "token-b", size = null, price = 0.70)
        )

        val summary = MarketBettingTradeAggregator.summarizeByAsset(trades)

        assertEquals("150.5", summary["token-a"]?.tradedShares)
        assertEquals("40.15", summary["token-a"]?.tradedAmount)
        assertEquals("20.25", summary["token-b"]?.tradedShares)
        assertEquals("14.175", summary["token-b"]?.tradedAmount)
    }

    private fun trade(asset: String, size: Double?, price: Double): UserActivityResponse =
        UserActivityResponse(
            proxyWallet = "0x0000000000000000000000000000000000000000",
            timestamp = 1,
            conditionId = "0x1",
            type = "TRADE",
            size = size,
            price = price,
            asset = asset
        )
}
