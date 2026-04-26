package com.wrbug.polymarketbot.service.market

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wrbug.polymarketbot.api.GammaEventMarketItem
import com.wrbug.polymarketbot.api.GammaSearchEventItem
import com.wrbug.polymarketbot.api.OrderbookEntry
import com.wrbug.polymarketbot.dto.*
import com.wrbug.polymarketbot.util.RetrofitFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

@Service
class MarketBettingQueryService(
    retrofitFactory: RetrofitFactory,
    private val gson: Gson
) {
    private val logger = LoggerFactory.getLogger(MarketBettingQueryService::class.java)
    private val gammaApi = retrofitFactory.createGammaApi()
    private val dataApi = retrofitFactory.createDataApi()
    private val clobApi = retrofitFactory.createClobApiWithoutAuth()

    suspend fun search(query: String, limit: Int = 5): Result<MarketBettingSearchResponse> = runCatching {
        val normalizedQuery = query.trim()
        require(normalizedQuery.isNotEmpty()) { "请输入比赛或盘口名称" }

        val response = gammaApi.publicSearch(
            query = normalizedQuery,
            limitPerType = limit.coerceIn(1, 20),
            eventsStatus = "active",
            keepClosedMarkets = 0
        )
        if (!response.isSuccessful) {
            throw IllegalStateException("Polymarket 搜索失败: HTTP ${response.code()}")
        }

        val events = response.body()?.events.orEmpty()
            .filter { !it.slug.isNullOrBlank() && !it.title.isNullOrBlank() }
            .map { it.toSummary() }

        MarketBettingSearchResponse(normalizedQuery, events)
    }

    suspend fun detail(query: String? = null, slug: String? = null, marketLimit: Int = 30): Result<MarketBettingEventDetail> =
        runCatching {
            val event = if (!slug.isNullOrBlank()) {
                val response = gammaApi.getEventBySlug(slug.trim())
                if (!response.isSuccessful) {
                    throw IllegalStateException("Polymarket 事件详情失败: HTTP ${response.code()}")
                }
                val body = response.body() ?: throw IllegalStateException("Polymarket 未返回事件详情")
                GammaSearchEventItem(
                    id = body.id,
                    slug = body.slug,
                    title = body.title,
                    active = true,
                    closed = false,
                    startDate = body.startDate,
                    endDate = body.endDate,
                    markets = body.markets
                )
            } else {
                val searchResult = search(query.orEmpty(), 1).getOrThrow()
                val first = searchResult.events.firstOrNull() ?: throw IllegalArgumentException("未找到相关盘口")
                val response = gammaApi.getEventBySlug(first.slug)
                if (!response.isSuccessful) {
                    throw IllegalStateException("Polymarket 事件详情失败: HTTP ${response.code()}")
                }
                val body = response.body() ?: throw IllegalStateException("Polymarket 未返回事件详情")
                GammaSearchEventItem(
                    id = body.id,
                    slug = body.slug,
                    title = body.title,
                    active = true,
                    closed = false,
                    startDate = body.startDate,
                    endDate = body.endDate,
                    markets = body.markets
                )
            }

            val summary = event.toSummary()
            val markets = event.markets.orEmpty()
                .filter { !it.conditionId.isNullOrBlank() }
                .take(marketLimit.coerceIn(1, 100))

            val holderMap = loadHolders(markets.mapNotNull { it.conditionId }.distinct())
            val details = coroutineScope {
                markets.map { market ->
                    async { market.toDetail(holderMap) }
                }.awaitAll()
            }

            summary.copy(marketsCount = event.markets?.size ?: details.size).let { resolvedSummary ->
                MarketBettingEventDetail(resolvedSummary, details)
            }
        }

    private suspend fun loadHolders(conditionIds: List<String>): Map<String, List<MarketBettingHolder>> {
        if (conditionIds.isEmpty()) return emptyMap()
        return withContext(Dispatchers.IO) {
            try {
                val response = dataApi.getHolders(conditionIds.joinToString(","), limit = 5, minBalance = 0)
                if (!response.isSuccessful) return@withContext emptyMap()
                response.body().orEmpty().associate { token ->
                    token.token.orEmpty() to token.holders.orEmpty()
                        .sortedByDescending { it.amount ?: 0.0 }
                        .take(5)
                        .map {
                            MarketBettingHolder(
                                wallet = it.proxyWallet.orEmpty(),
                                name = it.name?.takeIf { name -> name.isNotBlank() } ?: it.pseudonym?.takeIf { name -> name.isNotBlank() },
                                shares = formatDecimal(it.amount)
                            )
                        }
                }
            } catch (e: Exception) {
                logger.warn("load holders failed: {}", e.message)
                emptyMap()
            }
        }
    }

    private suspend fun GammaEventMarketItem.toDetail(
        holderMap: Map<String, List<MarketBettingHolder>>
    ): MarketBettingMarketDetail {
        val outcomeNames = parseStringArray(outcomes).ifEmpty { listOf("Yes", "No") }
        val prices = parseStringArray(outcomePrices)
        val tokenIds = parseStringArray(clobTokenIds ?: clob_token_ids)
        val outcomeDetails = coroutineScope {
            outcomeNames.mapIndexed { index, name ->
                async {
                    val tokenId = tokenIds.getOrNull(index).orEmpty()
                    val orderbook = runCatching {
                        if (tokenId.isNotBlank()) clobApi.getOrderbook(tokenId = tokenId).body() else null
                    }.getOrNull()
                    MarketBettingOutcomeDetail(
                        name = name,
                        tokenId = tokenId,
                        odds = formatOdds(prices.getOrNull(index) ?: lastTradePrice?.toString()),
                        bidOrderAmount = formatDecimal(orderbook?.bids?.sumOrderAmount()),
                        askOrderAmount = formatDecimal(orderbook?.asks?.sumOrderAmount()),
                        topHolders = holderMap[tokenId].orEmpty()
                    )
                }
            }.awaitAll()
        }

        return MarketBettingMarketDetail(
            id = id.orEmpty(),
            conditionId = conditionId.orEmpty(),
            slug = slug.orEmpty(),
            question = question.orEmpty(),
            marketType = sportsMarketType?.takeIf { it.isNotBlank() }
                ?: marketType?.takeIf { it.isNotBlank() }
                ?: "market",
            line = line?.takeIf { it.isNotBlank() },
            groupItemTitle = groupItemTitle?.takeIf { it.isNotBlank() },
            volume = formatDecimal(volumeNum ?: volume?.toDoubleOrNull()),
            liquidity = formatDecimal(liquidityNum ?: liquidity?.toDoubleOrNull()),
            outcomes = outcomeDetails
        )
    }

    private fun GammaSearchEventItem.toSummary(): MarketBettingEventSummary {
        val slugValue = slug.orEmpty()
        return MarketBettingEventSummary(
            id = id.orEmpty(),
            slug = slugValue,
            title = title.orEmpty(),
            volume = formatDecimal(volume ?: markets.orEmpty().sumOf { it.volumeNum ?: it.volume?.toDoubleOrNull() ?: 0.0 }),
            liquidity = formatDecimal(liquidity ?: markets.orEmpty().sumOf { it.liquidityNum ?: it.liquidity?.toDoubleOrNull() ?: 0.0 }),
            openInterest = formatDecimal(openInterest),
            active = active ?: false,
            closed = closed ?: false,
            marketsCount = markets?.size ?: 0,
            url = "https://polymarket.com/event/$slugValue",
            category = category,
            startDate = startDate,
            endDate = endDate
        )
    }

    private fun parseStringArray(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<String>>(raw, object : TypeToken<List<String>>() {}.type)
        }.getOrElse {
            raw.trim('[', ']').split(',').map { item -> item.trim().trim('"') }.filter { item -> item.isNotBlank() }
        }
    }
}

object MarketBettingTelegramCommandParser {
    fun parse(text: String?): MarketBettingTelegramCommand? {
        val trimmed = text?.trim().orEmpty()
        val prefixes = listOf("/盘口", "盘口", "/pan", "pan", "/market", "market")
        val prefix = prefixes.firstOrNull { trimmed.startsWith("$it ", ignoreCase = true) } ?: return null
        val query = trimmed.removePrefix(prefix).trim()
        return query.takeIf { it.isNotBlank() }?.let { MarketBettingTelegramCommand(it) }
    }
}

data class MarketBettingTelegramCommand(val query: String)

object MarketBettingQueryFormatter {
    fun formatSearch(response: MarketBettingSearchResponse): String {
        if (response.events.isEmpty()) return "未找到相关盘口：${response.query}"
        return buildString {
            appendLine("盘口投注额查询")
            appendLine("关键词: ${escape(response.query)}")
            response.events.forEachIndexed { index, event ->
                appendLine()
                appendLine("${index + 1}. ${escape(event.title)}")
                appendLine("总成交额: ${formatUsdc(event.volume)}")
                appendLine("盘口数: ${event.marketsCount} | ${if (event.closed) "已关闭" else "交易中"}")
                appendLine(event.url)
            }
            appendLine()
            append("发送：盘口 具体比赛名，可查看明细。")
        }
    }

    fun formatEventDetail(detail: MarketBettingEventDetail): String {
        return buildString {
            appendLine("盘口投注额查询")
            appendLine(escape(detail.event.title))
            appendLine("总成交额: ${formatUsdc(detail.event.volume)}")
            appendLine("流动性: ${formatUsdc(detail.event.liquidity)}")
            appendLine("盘口数: ${detail.event.marketsCount}")
            appendLine(detail.event.url)
            detail.markets.forEachIndexed { index, market ->
                appendLine()
                val type = listOfNotNull(market.marketType, market.line).joinToString(" ")
                appendLine("${index + 1}. ${escape(market.groupItemTitle ?: market.question)}")
                appendLine("类型: $type | 成交额: ${formatUsdc(market.volume)}")
                market.outcomes.forEach { outcome ->
                    appendLine("- ${escape(outcome.name)} ${formatPercent(outcome.odds)}")
                    appendLine("  挂单: 买 ${formatUsdc(outcome.bidOrderAmount)} / 卖 ${formatUsdc(outcome.askOrderAmount)}")
                    appendLine("  Top 5 shares:")
                    if (outcome.topHolders.isEmpty()) {
                        appendLine("  无")
                    } else {
                        outcome.topHolders.forEachIndexed { holderIndex, holder ->
                            val holderName = holder.name?.takeIf { it.isNotBlank() }
                                ?: shortenWallet(holder.wallet)
                            appendLine("  ${holderIndex + 1}. ${escape(holderName)} ${holder.shares} shares")
                        }
                    }
                }
            }
        }.trim()
    }

    private fun formatUsdc(value: String): String {
        val amount = value.toBigDecimalOrNull() ?: return "$value USDC"
        return "${DecimalFormat("#,##0.####").format(amount)} USDC"
    }

    private fun formatPercent(value: String): String {
        val amount = value.toBigDecimalOrNull() ?: return value
        return "${amount.multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()}%"
    }

    private fun shortenWallet(wallet: String): String {
        return if (wallet.length > 10) "${wallet.take(6)}...${wallet.takeLast(4)}" else wallet
    }

    private fun escape(value: String): String = value.replace("<", "&lt;").replace(">", "&gt;")
}

private fun List<OrderbookEntry>.sumOrderAmount(): Double {
    return sumOf { entry ->
        val price = entry.price.toDoubleOrNull() ?: 0.0
        val size = entry.size.toDoubleOrNull() ?: 0.0
        price * size
    }
}

private fun formatOdds(value: String?): String {
    return formatDecimal(value?.toDoubleOrNull())
}

private fun formatDecimal(value: Double?): String {
    if (value == null || value.isNaN() || value.isInfinite()) return "0"
    return BigDecimal.valueOf(value)
        .setScale(4, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}
