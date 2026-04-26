package com.wrbug.polymarketbot.service.system

import com.fasterxml.jackson.databind.ObjectMapper
import com.wrbug.polymarketbot.dto.NotificationConfigData
import com.wrbug.polymarketbot.dto.NotificationConfigDto
import com.wrbug.polymarketbot.dto.TelegramConfigData
import com.wrbug.polymarketbot.repository.LargeBetMonitorConfigRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.context.support.StaticMessageSource
import java.math.BigDecimal
import java.util.Locale

class TelegramNotificationServiceTest {

    @Test
    fun `monitor messages should exclude large bet assigned telegram config`() {
        val configs = listOf(
            telegramConfig(id = 1, name = "charlie", monitorModeEnabled = true),
            telegramConfig(id = 2, name = "william", monitorModeEnabled = true)
        )

        val filtered = filterTelegramConfigsForAudience(
            configs = configs,
            audience = TelegramNotificationAudience.MONITOR_ONLY,
            excludedConfigIds = setOf(2L)
        )

        assertEquals(listOf(1L), filtered.map { it.id })
    }

    @Test
    fun `copy trading route should match category and notification type`() {
        val route = CopyTradingTelegramRoute(
            telegramConfigId = 1L,
            categories = listOf("sports"),
            notificationTypes = listOf("success")
        )

        assertTrue(route.matches("体育", "success"))
        assertFalse(route.matches("crypto", "success"))
        assertFalse(route.matches("sports", "filtered"))
    }

    @Test
    fun `telegram config route filters should match category and notification type`() {
        val config = TelegramConfigData(
            botToken = "token",
            chatIds = listOf("chat"),
            monitorModeEnabled = true,
            copyTradingCategories = listOf("sports"),
            copyTradingNotificationTypes = listOf("monitor")
        )

        assertTrue(hasCopyTradingRouteFilters(config))
        assertTrue(telegramConfigMatchesCopyTradingRoute(config, "体育", "monitor"))
        assertFalse(telegramConfigMatchesCopyTradingRoute(config, "crypto", "monitor"))
        assertFalse(telegramConfigMatchesCopyTradingRoute(config, "sports", "success"))
    }

    @Test
    fun `telegram config route filters should treat empty filters as all`() {
        val config = TelegramConfigData(
            botToken = "token",
            chatIds = listOf("chat"),
            copyTradingCategories = emptyList(),
            copyTradingNotificationTypes = emptyList()
        )

        assertFalse(hasCopyTradingRouteFilters(config))
        assertTrue(telegramConfigMatchesCopyTradingRoute(config, "crypto", "success"))
        assertTrue(telegramConfigMatchesCopyTradingRoute(config, "sports", "monitor"))
    }

    @Test
    fun `market betting query should use only enabled query bots`() {
        val configs = listOf(
            telegramConfig(id = 1, name = "query", monitorModeEnabled = false, marketBettingQueryEnabled = true),
            telegramConfig(id = 2, name = "normal", monitorModeEnabled = false, marketBettingQueryEnabled = false)
        )

        val filtered = filterMarketBettingQueryTelegramConfigs(configs)

        assertEquals(listOf(1L), filtered.map { it.id })
    }

    @Test
    fun `signal source display should include config name before leader name`() {
        assertEquals("crocodile-main / 3crocodile3", buildSignalSourceDisplay("crocodile-main", "3crocodile3"))
    }

    @Test
    fun `signal source display should fall back to leader name`() {
        assertEquals("3crocodile3", buildSignalSourceDisplay(null, "3crocodile3"))
    }

    @Test
    fun `signal source display should remove duplicate values`() {
        assertEquals("3crocodile3", buildSignalSourceDisplay("3crocodile3", "3crocodile3"))
    }

    @Test
    fun `order success notification should suppress amounts below configured minimum`() {
        assertTrue(shouldSuppressOrderNotificationAmount("9.9999", BigDecimal("10")))
        assertFalse(shouldSuppressOrderNotificationAmount("10", BigDecimal("10")))
        assertFalse(shouldSuppressOrderNotificationAmount("12.5", BigDecimal("10")))
        assertFalse(shouldSuppressOrderNotificationAmount(null, BigDecimal("10")))
    }

    @Test
    fun `signal source details should include current position value on next line`() {
        assertEquals(
            "\u2022 \u4fe1\u53f7\u6e90: crocodile-main / 3crocodile3\n\u2022 \u5f53\u524d\u6301\u4ed3\u91d1\u989d: 20000u",
            buildSignalSourceDetails(
                configName = "crocodile-main",
                leaderName = "3crocodile3",
                signalSourceLabel = "\u4fe1\u53f7\u6e90",
                currentPositionValueLabel = "\u5f53\u524d\u6301\u4ed3\u91d1\u989d",
                currentPositionValue = "20000.0000"
            )
        )
    }

    @Test
    fun `signal source details should omit current position value when it is blank`() {
        assertEquals(
            "\u2022 \u4fe1\u53f7\u6e90: crocodile-main / 3crocodile3",
            buildSignalSourceDetails(
                configName = "crocodile-main",
                leaderName = "3crocodile3",
                signalSourceLabel = "\u4fe1\u53f7\u6e90",
                currentPositionValueLabel = "\u5f53\u524d\u6301\u4ed3\u91d1\u989d",
                currentPositionValue = "   "
            )
        )
    }

    @Test
    fun `order failure variables should include current position value under signal source`() {
        val messageSource = StaticMessageSource()
        val service = TelegramNotificationService(
            notificationConfigService = mock(NotificationConfigService::class.java),
            notificationTemplateService = mock(NotificationTemplateService::class.java),
            objectMapper = ObjectMapper(),
            messageSource = messageSource,
            largeBetMonitorConfigRepository = mock(LargeBetMonitorConfigRepository::class.java),
            systemConfigService = mock(SystemConfigService::class.java)
        )
        val method = TelegramNotificationService::class.java.getDeclaredMethod(
            "buildOrderFailureVariables",
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Locale::class.java,
            String::class.java,
            String::class.java,
            String::class.java
        )
        method.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val vars = method.invoke(
            service,
            "US-Iran nuclear deal by April 30?",
            null,
            "us-iran-nuclear-deal-by-april-30",
            "BUY",
            "No",
            "0.6693",
            "74.7",
            "49.99971",
            "code=403",
            "de",
            "0x1277000000004143",
            "debased",
            "\u8ddf\u5355-2026-04-19 12:19:49",
            Locale.SIMPLIFIED_CHINESE,
            "20000.0000",
            "\u672a\u77e5\u8d26\u6237",
            "\u8ba1\u7b97\u5931\u8d25"
        ) as Map<String, String>

        val accountInfo = vars["account_name"].orEmpty()
        assertTrue(accountInfo.contains("\u2022 \u4fe1\u53f7\u6e90: \u8ddf\u5355-2026-04-19 12:19:49 / debased"))
        assertTrue(accountInfo.contains("\u2022 \u5f53\u524d\u6301\u4ed3\u91d1\u989d: 20000u"))
    }

    @Test
    fun `order filtered variables should include account info`() {
        val messageSource = StaticMessageSource()
        val service = TelegramNotificationService(
            notificationConfigService = mock(NotificationConfigService::class.java),
            notificationTemplateService = mock(NotificationTemplateService::class.java),
            objectMapper = ObjectMapper(),
            messageSource = messageSource,
            largeBetMonitorConfigRepository = mock(LargeBetMonitorConfigRepository::class.java),
            systemConfigService = mock(SystemConfigService::class.java)
        )
        val method = TelegramNotificationService::class.java.getDeclaredMethod(
            "buildOrderFilteredVariables",
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Locale::class.java,
            String::class.java,
            String::class.java
        )
        method.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val vars = method.invoke(
            service,
            "US-Iran nuclear deal by April 30?",
            null,
            "us-iran-nuclear-deal-by-april-30",
            "BUY",
            "No",
            "0.6693",
            "74.7",
            "49.99971",
            "\u672a\u914d\u7f6e\u8ddf\u5355\u89c4\u5219\uff0c\u5df2\u8df3\u8fc7\u8ddf\u5355",
            "FOLLOW_RULE_MISSING",
            "de",
            "0x1277000000004143",
            Locale.SIMPLIFIED_CHINESE,
            "\u672a\u77e5\u8d26\u6237",
            "\u8ba1\u7b97\u5931\u8d25"
        ) as Map<String, String>

        val accountInfo = vars["account_name"].orEmpty()
        assertTrue(accountInfo.contains("de"))
        assertTrue(accountInfo.contains("0x1277...4143"))
    }

    private fun telegramConfig(
        id: Long,
        name: String,
        monitorModeEnabled: Boolean,
        marketBettingQueryEnabled: Boolean = false
    ) = NotificationConfigDto(
        id = id,
        type = "telegram",
        name = name,
        enabled = true,
        config = NotificationConfigData.Telegram(
            TelegramConfigData(
                botToken = "token-$id",
                chatIds = listOf("chat-$id"),
                monitorModeEnabled = monitorModeEnabled,
                marketBettingQueryEnabled = marketBettingQueryEnabled
            )
        )
    )
}
