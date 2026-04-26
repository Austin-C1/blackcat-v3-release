package com.wrbug.polymarketbot.service.largebet

import com.wrbug.polymarketbot.service.system.TelegramNotificationService
import com.wrbug.polymarketbot.util.DateUtils
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class LargeBetTelegramAlertService(
    private val telegramNotificationService: TelegramNotificationService
) {

    suspend fun sendAlert(
        event: LargeBetTradeEvent,
        triggerReason: String,
        singleAmount: BigDecimal,
        cumulativeAmount: BigDecimal,
        telegramConfigId: Long?
    ): Boolean {
        return telegramNotificationService.sendLargeBetMonitorMessage(
            message = buildMessage(event, triggerReason, singleAmount, cumulativeAmount),
            configId = telegramConfigId
        )
    }

    fun buildMessage(
        event: LargeBetTradeEvent,
        triggerReason: String,
        singleAmount: BigDecimal,
        cumulativeAmount: BigDecimal
    ): String {
        val address = event.traderAddress.lowercase()
        val profileUrl = "https://polymarket.com/profile/$address"
        val displayName = event.traderName?.trim()?.takeIf { it.isNotEmpty() } ?: shortAddress(address)
        val marketLink = if (!event.marketSlug.isNullOrBlank()) {
            "https://polymarket.com/event/${event.marketSlug}"
        } else {
            "https://polymarket.com/condition/${event.marketId}"
        }
        val time = DateUtils.formatDateTime(event.timestampMillis)

        return """<b>大额投注监控</b>

盘口: <a href="$marketLink">${escapeHtml(event.marketTitle)}</a>
类型: ${escapeHtml(event.sportType)}
用户: <a href="$profileUrl">${escapeHtml(displayName)}</a>
方向: <b>${escapeHtml(event.outcome)}</b>
单笔成交: <code>${formatAmount(singleAmount)}</code> USDC
窗口累计: <code>${formatAmount(cumulativeAmount)}</code> USDC
触发原因: ${escapeHtml(triggerReason)}
时间: <code>$time</code>"""
    }

    private fun formatAmount(amount: BigDecimal): String {
        return amount.setScale(4, RoundingMode.DOWN).toPlainString()
    }

    private fun shortAddress(address: String): String {
        return if (address.length <= 10) address else "${address.take(6)}...${address.takeLast(4)}"
    }

    private fun escapeHtml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
