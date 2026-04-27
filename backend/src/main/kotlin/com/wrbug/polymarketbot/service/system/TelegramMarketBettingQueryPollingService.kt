package com.wrbug.polymarketbot.service.system

import com.wrbug.polymarketbot.dto.NotificationConfigData
import com.wrbug.polymarketbot.service.market.MarketBettingQueryFormatter
import com.wrbug.polymarketbot.service.market.MarketBettingTelegramCommandParser
import com.wrbug.polymarketbot.service.market.MarketBettingQueryService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

data class TelegramIncomingUpdate(
    val updateId: Long,
    val chatId: String,
    val text: String
)

@Service
class TelegramMarketBettingQueryPollingService(
    private val notificationConfigService: NotificationConfigService,
    private val telegramNotificationService: TelegramNotificationService,
    private val marketBettingQueryService: MarketBettingQueryService
) {
    private val logger = LoggerFactory.getLogger(TelegramMarketBettingQueryPollingService::class.java)
    private val offsets = ConcurrentHashMap<String, Long>()

    @Scheduled(fixedDelay = 5_000)
    fun poll() {
        runBlocking {
            val configs = filterMarketBettingQueryTelegramConfigs(notificationConfigService.getEnabledConfigsByType("telegram"))
            configs.forEach { config ->
                val telegram = config.config as? NotificationConfigData.Telegram ?: return@forEach
                val token = telegram.data.botToken.takeIf { it.isNotBlank() } ?: return@forEach
                val updates = telegramNotificationService.getUpdates(token, offsets[token]).getOrElse {
                    logger.warn("Telegram 盘口查询轮询失败: {}", it.message)
                    return@forEach
                }
                if (updates.isEmpty()) return@forEach

                val nextOffset = updates.maxOf { it.updateId } + 1
                offsets[token] = nextOffset

                updates.forEach updateLoop@{ update ->
                    val command = MarketBettingTelegramCommandParser.parse(update.text) ?: return@updateLoop
                    val message = marketBettingQueryService.detail(query = command.query, marketLimit = 100, date = command.date)
                        .fold(
                            onSuccess = { MarketBettingQueryFormatter.formatEventDetail(it) },
                            onFailure = {
                                val search = marketBettingQueryService.search(command.query, 5, command.date)
                                search.fold(
                                    onSuccess = { result -> MarketBettingQueryFormatter.formatSearch(result) },
                                    onFailure = { error -> "查询失败：${error.message ?: "未找到相关盘口"}" }
                                )
                            }
                        )
                    sendChunks(token, update.chatId, message)
                }
            }
        }
    }

    private suspend fun sendChunks(botToken: String, chatId: String, message: String) {
        message.chunked(3500).forEach { chunk ->
            telegramNotificationService.sendMessage(botToken, chatId, chunk)
        }
    }
}
