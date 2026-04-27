package com.wrbug.polymarketbot.service.market

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wrbug.polymarketbot.api.GammaEventMarketItem
import com.wrbug.polymarketbot.api.GammaSearchEventItem
import com.wrbug.polymarketbot.api.OrderbookEntry
import com.wrbug.polymarketbot.api.UserActivityResponse
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

    suspend fun search(query: String, limit: Int = 5, date: String? = null): Result<MarketBettingSearchResponse> = runCatching {
        val normalizedQuery = query.trim()
        val normalizedDate = MarketBettingDateFilter.normalize(date)
        require(normalizedQuery.isNotEmpty()) { "\u8bf7\u8f93\u5165\u6bd4\u8d5b\u6216\u76d8\u53e3\u540d\u79f0" }

        val response = gammaApi.publicSearch(
            query = normalizedQuery,
            limitPerType = limit.coerceIn(1, 20),
            eventsStatus = "active",
            keepClosedMarkets = 0
        )
        if (!response.isSuccessful) {
            throw IllegalStateException("Polymarket \u641c\u7d22\u5931\u8d25: HTTP ${response.code()}")
        }

        val events = response.body()?.events.orEmpty()
            .filter { !it.slug.isNullOrBlank() && !it.title.isNullOrBlank() }
            .filter { event -> normalizedDate == null || MarketBettingDateFilter.matches(event, normalizedDate) }
            .map { it.toSummary() }

        MarketBettingSearchResponse(normalizedQuery, events)
    }

    suspend fun detail(query: String? = null, slug: String? = null, marketLimit: Int = 30, date: String? = null): Result<MarketBettingEventDetail> =
        runCatching {
            val event = if (!slug.isNullOrBlank()) {
                val response = gammaApi.getEventBySlug(slug.trim())
                if (!response.isSuccessful) {
                    throw IllegalStateException("Polymarket \u4e8b\u4ef6\u8be6\u60c5\u5931\u8d25: HTTP ${response.code()}")
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
                    volume = body.volume,
                    volumeClob = body.volumeClob,
                    liquidity = body.liquidity,
                    liquidityClob = body.liquidityClob,
                    openInterest = body.openInterest,
                    markets = body.markets
                )
            } else {
                val searchResult = search(query.orEmpty(), 20, date).getOrThrow()
                val first = searchResult.events.firstOrNull() ?: throw IllegalArgumentException("未找到相关盘口")
                val response = gammaApi.getEventBySlug(first.slug)
                if (!response.isSuccessful) {
                    throw IllegalStateException("Polymarket \u4e8b\u4ef6\u8be6\u60c5\u5931\u8d25: HTTP ${response.code()}")
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
                    volume = body.volume,
                    volumeClob = body.volumeClob,
                    liquidity = body.liquidity,
                    liquidityClob = body.liquidityClob,
                    openInterest = body.openInterest,
                    markets = body.markets
                )
            }

            val normalizedDate = MarketBettingDateFilter.normalize(date)
            val summary = event.toSummary()
            val markets = event.markets.orEmpty()
                .filter { !it.conditionId.isNullOrBlank() }
                .filter { market -> MarketBettingMarketFilter.belongsToEvent(market, event) }
                .filter { market -> MarketBettingMarketFilter.isMainGameMarket(market) }
                .filter { market -> normalizedDate == null || MarketBettingDateFilter.matches(market, normalizedDate) }
                .take(marketLimit.coerceIn(1, 100))

            val holderMap = loadHolders(markets.mapNotNull { it.conditionId }.distinct())
            val tradeMap = loadTrades(markets.mapNotNull { it.conditionId }.distinct())
            val details = coroutineScope {
                markets.map { market ->
                    async { market.toDetail(holderMap, tradeMap) }
                }.awaitAll()
            }

            summary.copy(marketsCount = details.size).let { resolvedSummary ->
                MarketBettingEventDetail(resolvedSummary, details)
        }
    }

    private data class TradeSnapshot(
        val tradedShares: String,
        val tradedAmount: String
    )

    private data class HolderSnapshot(
        val totalShares: String,
        val topHolders: List<MarketBettingHolder>
    )

    private suspend fun loadTrades(conditionIds: List<String>): Map<String, TradeSnapshot> {
        if (conditionIds.isEmpty()) return emptyMap()
        return withContext(Dispatchers.IO) {
            try {
                val trades = mutableListOf<UserActivityResponse>()
                val marketQuery = conditionIds.joinToString(",")
                listOf(0, 1000, 2000, 3000).forEach { offset ->
                    val response = dataApi.getTrades(
                        market = marketQuery,
                        limit = 1000,
                        offset = offset,
                        takerOnly = true
                    )
                    if (!response.isSuccessful) return@forEach
                    val page = response.body().orEmpty()
                    trades += page
                    if (page.size < 1000) return@withContext MarketBettingTradeAggregator.summarizeByAsset(trades)
                        .mapValues { (_, value) -> TradeSnapshot(value.tradedShares, value.tradedAmount) }
                }
                MarketBettingTradeAggregator.summarizeByAsset(trades)
                    .mapValues { (_, value) -> TradeSnapshot(value.tradedShares, value.tradedAmount) }
            } catch (e: Exception) {
                logger.warn("load trades failed: {}", e.message)
                emptyMap()
            }
        }
    }

    private suspend fun loadHolders(conditionIds: List<String>): Map<String, HolderSnapshot> {
        if (conditionIds.isEmpty()) return emptyMap()
        return withContext(Dispatchers.IO) {
            try {
                val response = dataApi.getHolders(conditionIds.joinToString(","), limit = 500, minBalance = 0)
                if (!response.isSuccessful) return@withContext emptyMap()
                response.body().orEmpty().associate { token ->
                    val holders = token.holders.orEmpty()
                        .sortedByDescending { it.amount ?: 0.0 }
                    val totalShares = holders.sumOf { it.amount ?: 0.0 }
                    token.token.orEmpty() to HolderSnapshot(
                        totalShares = formatDecimal(totalShares),
                        topHolders = holders.take(5).map {
                            MarketBettingHolder(
                                wallet = it.proxyWallet.orEmpty(),
                                name = it.name?.takeIf { name -> name.isNotBlank() } ?: it.pseudonym?.takeIf { name -> name.isNotBlank() },
                                shares = formatDecimal(it.amount)
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                logger.warn("load holders failed: {}", e.message)
                emptyMap()
            }
        }
    }

    private suspend fun GammaEventMarketItem.toDetail(
        holderMap: Map<String, HolderSnapshot>,
        tradeMap: Map<String, TradeSnapshot>
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
                        tradedShares = tradeMap[tokenId]?.tradedShares
                            ?: holderMap[tokenId]?.totalShares
                            ?: "0",
                        tradedAmount = tradeMap[tokenId]?.tradedAmount ?: "0",
                        bidOrderAmount = formatDecimal(orderbook?.bids?.sumOrderAmount()),
                        askOrderAmount = formatDecimal(orderbook?.asks?.sumOrderAmount()),
                        topHolders = holderMap[tokenId]?.topHolders.orEmpty()
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
            volume = formatDecimal(volumeClob ?: volumeNum ?: volume?.toDoubleOrNull()),
            liquidity = formatDecimal(liquidityClob ?: liquidityNum ?: liquidity?.toDoubleOrNull()),
            outcomes = outcomeDetails
        )
    }

    private fun GammaSearchEventItem.toSummary(): MarketBettingEventSummary {
        val slugValue = slug.orEmpty()
        return MarketBettingEventSummary(
            id = id.orEmpty(),
            slug = slugValue,
            title = title.orEmpty(),
            volume = formatDecimal(volumeClob ?: volume ?: markets.orEmpty().sumOf { it.volumeClob ?: it.volumeNum ?: it.volume?.toDoubleOrNull() ?: 0.0 }),
            liquidity = formatDecimal(liquidityClob ?: liquidity ?: markets.orEmpty().sumOf { it.liquidityClob ?: it.liquidityNum ?: it.liquidity?.toDoubleOrNull() ?: 0.0 }),
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

object MarketBettingTradeAggregator {
    data class TradeSummary(
        val tradedShares: String,
        val tradedAmount: String
    )

    fun summarizeByAsset(trades: List<UserActivityResponse>): Map<String, TradeSummary> {
        return trades
            .filter { !it.asset.isNullOrBlank() }
            .groupBy { it.asset.orEmpty() }
            .mapValues { (_, assetTrades) ->
                TradeSummary(
                    tradedShares = formatDecimal(assetTrades.sumOf { it.size ?: 0.0 }),
                    tradedAmount = formatDecimal(assetTrades.sumOf { (it.size ?: 0.0) * (it.price ?: 0.0) })
                )
            }
    }
}

object MarketBettingDateFilter {
    private val isoDate = Regex("""\b(20\d{2})[-/](\d{1,2})[-/](\d{1,2})\b""")
    private val monthDay = Regex("""\b(\d{1,2})/(\d{1,2})\b""")

    fun normalize(date: String?): String? {
        val value = date?.trim().orEmpty()
        if (value.isBlank()) return null
        isoDate.find(value)?.let { match ->
            val year = match.groupValues[1]
            val month = match.groupValues[2].padStart(2, '0')
            val day = match.groupValues[3].padStart(2, '0')
            return "$year-$month-$day"
        }
        monthDay.find(value)?.let { match ->
            val month = match.groupValues[1].padStart(2, '0')
            val day = match.groupValues[2].padStart(2, '0')
            return "2026-$month-$day"
        }
        return null
    }

    fun extractFromQuery(query: String): Pair<String, String?> {
        val isoMatch = isoDate.find(query)
        val monthDayMatch = if (isoMatch == null) monthDay.find(query) else null
        val match = isoMatch ?: monthDayMatch ?: return query.trim() to null
        val normalizedDate = normalize(match.value)
        val cleanedQuery = query.removeRange(match.range).trim().replace(Regex("""\s+"""), " ")
        return cleanedQuery to normalizedDate
    }

    fun matches(event: GammaSearchEventItem, date: String): Boolean {
        val eventDates = listOfNotNull(
            event.startDate?.take(10),
            event.endDate?.take(10),
            event.slug?.let { extractDateFromSlug(it) }
        )
        if (eventDates.any { it == date }) return true
        return event.markets.orEmpty().any { market -> matches(market, date) }
    }

    fun matches(market: GammaEventMarketItem, date: String): Boolean {
        return listOfNotNull(
            market.startDate?.take(10),
            market.endDate?.take(10),
            market.slug?.let { extractDateFromSlug(it) }
        ).any { it == date }
    }

    private fun extractDateFromSlug(value: String): String? {
        val match = Regex("""(20\d{2})-(\d{2})-(\d{2})""").find(value) ?: return null
        return "${match.groupValues[1]}-${match.groupValues[2]}-${match.groupValues[3]}"
    }
}

object MarketBettingTelegramCommandParser {
    fun parse(text: String?): MarketBettingTelegramCommand? {
        val trimmed = text?.trim().orEmpty()
        val prefixes = listOf(
            "/\u76d8\u53e3",
            "\u76d8\u53e3",
            "/pan",
            "pan",
            "/market",
            "market"
        )
        val prefix = prefixes.firstOrNull { trimmed.startsWith("$it ", ignoreCase = true) }
        val rawQuery = if (prefix != null) {
            trimmed.removePrefix(prefix).trim()
        } else {
            val firstToken = trimmed.substringBefore(" ")
            when {
                trimmed.isBlank() -> return null
                trimmed.startsWith("/") && " " !in trimmed -> return null
                trimmed.startsWith("/") && firstToken.lowercase() in setOf("/start", "/help") -> return null
                trimmed.startsWith("/") -> trimmed.substringAfter(" ").trim()
                firstToken != trimmed && firstToken.any { it.code > 127 } -> trimmed.substringAfter(" ").trim()
                else -> trimmed
            }
        }
        val (query, date) = MarketBettingDateFilter.extractFromQuery(rawQuery)
        return query.takeIf { it.isNotBlank() }?.let { MarketBettingTelegramCommand(it, date) }
    }
}

data class MarketBettingTelegramCommand(val query: String, val date: String? = null)

object MarketBettingMarketFilter {
    private val mainGameMarketTypes = setOf("moneyline", "spreads", "totals")

    fun belongsToEvent(market: GammaEventMarketItem, event: GammaSearchEventItem): Boolean {
        val eventSlug = event.slug.orEmpty().trim().lowercase()
        val marketSlug = market.slug.orEmpty().trim().lowercase()
        if (eventSlug.isNotBlank() && marketSlug.isNotBlank() && marketSlug.startsWith(eventSlug)) {
            return true
        }

        val eventTitle = normalizeText(event.title)
        val marketQuestion = normalizeText(market.question)
        return eventTitle.isNotBlank() && eventTitle == marketQuestion
    }

    fun isMainGameMarket(market: GammaEventMarketItem): Boolean {
        val type = market.sportsMarketType?.takeIf { it.isNotBlank() }
            ?: market.marketType?.takeIf { it.isNotBlank() }
            ?: return false
        return type.trim().lowercase() in mainGameMarketTypes
    }

    private fun normalizeText(value: String?): String =
        value.orEmpty()
            .lowercase()
            .replace(Regex("""\s+"""), " ")
            .replace(" vs. ", " vs ")
            .trim()
}

object MarketBettingMarketText {
    private val overUnderMarketTypes = setOf(
        "totals",
        "first_half_totals",
        "points",
        "rebounds",
        "assists",
        "threes",
        "blocks",
        "steals"
    )

    fun displayTitle(value: String): String =
        value.replace(Regex("""\bO/U\b""", RegexOption.IGNORE_CASE), "\u5927\u5c0f")

    fun displayType(marketType: String, line: String?): String =
        listOfNotNull(marketType.takeIf { it.isNotBlank() }, line?.takeIf { it.isNotBlank() }).joinToString(" ")

    fun displayOutcomeName(name: String, title: String, marketType: String): String {
        if (!isOverUnderMarket(title, marketType)) return name
        return when (name.trim().lowercase()) {
            "yes", "over" -> "\u5927"
            "no", "under" -> "\u5c0f"
            else -> name
        }
    }

    private fun isOverUnderMarket(title: String, marketType: String): Boolean {
        val type = marketType.trim().lowercase()
        return type in overUnderMarketTypes || title.contains("O/U", ignoreCase = true)
    }
}

object MarketBettingQueryFormatter {
    fun formatSearch(response: MarketBettingSearchResponse): String {
        if (response.events.isEmpty()) return "\u672a\u627e\u5230\u76f8\u5173\u76d8\u53e3\uff1a${response.query}"
        return buildString {
            appendLine("\u76d8\u53e3\u6295\u6ce8\u67e5\u8be2")
            appendLine("\u5173\u952e\u8bcd: ${escape(response.query)}")
            response.events.forEachIndexed { index, event ->
                appendLine()
                appendLine("${index + 1}. ${escape(event.title)}")
                appendLine("\u603b\u6210\u4ea4\u989d: ${formatUsdc(event.volume)}")
                appendLine("\u76d8\u53e3\u6570: ${event.marketsCount} | ${if (event.closed) "\u5df2\u5173\u95ed" else "\u4ea4\u6613\u4e2d"}")
                appendLine(event.url)
            }
            appendLine()
            append("\u53d1\u9001\uff1a\u76d8\u53e3 \u5177\u4f53\u6bd4\u8d5b\u540d\uff0c\u53ef\u67e5\u770b\u660e\u7ec6\u3002")
        }

    }

    fun formatEventDetail(detail: MarketBettingEventDetail): String {
        return buildString {
            appendLine("\u76d8\u53e3\u6295\u6ce8\u67e5\u8be2")
            appendLine(escape(detail.event.title))
            appendLine("\u603b\u6210\u4ea4\u989d: ${formatUsdc(detail.event.volume)}")
            appendLine("\u603b\u6302\u5355\u91d1\u989d: ${formatUsdc(detail.event.liquidity)}")
            appendLine("\u76d8\u53e3\u6570: ${detail.event.marketsCount}")
            appendLine(detail.event.url)
            detail.markets.forEachIndexed { index, market ->
                appendLine()
                val title = MarketBettingMarketText.displayTitle(market.groupItemTitle ?: market.question)
                val type = MarketBettingMarketText.displayType(market.marketType, market.line)
                appendLine("${index + 1}. ${escape(title)}")
                appendLine("\u7c7b\u578b: $type | \u6210\u4ea4\u989d: ${formatUsdc(market.volume)} | \u6302\u5355\u91d1\u989d: ${formatUsdc(market.liquidity)}")
                market.outcomes.forEach { outcome ->
                    val outcomeName = MarketBettingMarketText.displayOutcomeName(
                        outcome.name,
                        market.groupItemTitle ?: market.question,
                        market.marketType
                    )
                    appendLine("- ${escape(outcomeName)} ${formatPercent(outcome.odds)}")
                    appendLine("  \u65b9\u5411\u6210\u4ea4\u989d: ${formatUsdc(outcome.tradedAmount)}")
                    appendLine("  \u5df2\u6210\u4ea4 shares: ${formatShares(outcome.tradedShares)}")
                    appendLine("  \u6302\u5355: \u4e70 ${formatUsdc(outcome.bidOrderAmount)} / \u5356 ${formatUsdc(outcome.askOrderAmount)}")
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

    private fun formatShares(value: String): String {
        val amount = value.toBigDecimalOrNull() ?: return value
        return DecimalFormat("#,##0.####").format(amount)
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
