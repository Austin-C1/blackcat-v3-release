# Large Bet Monitor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an independent large bet monitor for football and basketball Polymarket filled trades with Telegram alerts and persistent watch records.

**Architecture:** Add a standalone backend module for configuration, live filled-trade ingestion, rolling aggregation, watch records, and Telegram alerts. Add a standalone frontend page and menu entry so the feature does not mix with existing order notification settings.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, Flyway, Retrofit/Gson, React, TypeScript, Ant Design, Vitest.

---

## File Structure

- Create `backend/src/main/kotlin/com/wrbug/polymarketbot/entity/LargeBetMonitorConfig.kt` for persisted monitor settings.
- Create `backend/src/main/kotlin/com/wrbug/polymarketbot/entity/LargeBetWatchRecord.kt` for triggered user records.
- Create `backend/src/main/kotlin/com/wrbug/polymarketbot/repository/LargeBetMonitorConfigRepository.kt`.
- Create `backend/src/main/kotlin/com/wrbug/polymarketbot/repository/LargeBetWatchRecordRepository.kt`.
- Create `backend/src/main/kotlin/com/wrbug/polymarketbot/dto/LargeBetMonitorDto.kt`.
- Create `backend/src/main/kotlin/com/wrbug/polymarketbot/controller/system/LargeBetMonitorController.kt`.
- Create `backend/src/main/kotlin/com/wrbug/polymarketbot/service/largebet/LargeBetMonitorConfigService.kt`.
- Create `backend/src/main/kotlin/com/wrbug/polymarketbot/service/largebet/LargeBetMarketClassifier.kt`.
- Create `backend/src/main/kotlin/com/wrbug/polymarketbot/service/largebet/LargeBetRollingAggregator.kt`.
- Create `backend/src/main/kotlin/com/wrbug/polymarketbot/service/largebet/LargeBetWatchRecordService.kt`.
- Create `backend/src/main/kotlin/com/wrbug/polymarketbot/service/largebet/LargeBetTelegramAlertService.kt`.
- Create `backend/src/main/kotlin/com/wrbug/polymarketbot/service/largebet/LargeBetActivityMonitorService.kt`.
- Create `backend/src/main/resources/db/migration/V51__create_large_bet_monitor.sql`.
- Modify `backend/src/main/kotlin/com/wrbug/polymarketbot/api/PolymarketGammaApi.kt` only if market list queries need `tag_id`, `category`, or `slug` parameters.
- Modify `backend/src/main/kotlin/com/wrbug/polymarketbot/service/system/TelegramNotificationService.kt` to expose one monitor-message method if the current private `sendMonitorMessage` cannot be reused.
- Create backend tests under `backend/src/test/kotlin/com/wrbug/polymarketbot/service/largebet/`.
- Create `frontend/src/pages/LargeBetMonitor.tsx`.
- Modify `frontend/src/App.tsx` to add the `/large-bet-monitor` route.
- Modify `frontend/src/components/Layout.tsx` to add the new menu item.
- Modify `frontend/src/services/api.ts` to add large bet monitor API methods.
- Modify `frontend/src/types/index.ts` or create `frontend/src/types/largeBetMonitor.ts` for page types.
- Modify `frontend/src/locales/zh-CN/common.json`, `frontend/src/locales/zh-TW/common.json`, and `frontend/src/locales/en/common.json` for menu and page text.
- Create frontend tests under `frontend/tests/largeBetMonitor.test.tsx`.

---

### Task 1: Database and Backend DTOs

**Files:**
- Create: `backend/src/main/resources/db/migration/V51__create_large_bet_monitor.sql`
- Create: `backend/src/main/kotlin/com/wrbug/polymarketbot/entity/LargeBetMonitorConfig.kt`
- Create: `backend/src/main/kotlin/com/wrbug/polymarketbot/entity/LargeBetWatchRecord.kt`
- Create: `backend/src/main/kotlin/com/wrbug/polymarketbot/repository/LargeBetMonitorConfigRepository.kt`
- Create: `backend/src/main/kotlin/com/wrbug/polymarketbot/repository/LargeBetWatchRecordRepository.kt`
- Create: `backend/src/main/kotlin/com/wrbug/polymarketbot/dto/LargeBetMonitorDto.kt`

- [ ] **Step 1: Add migration**

```sql
CREATE TABLE IF NOT EXISTS large_bet_monitor_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    football_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    basketball_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    single_trade_threshold DECIMAL(20, 8) NOT NULL DEFAULT 5000.00000000,
    cumulative_trade_threshold DECIMAL(20, 8) NOT NULL DEFAULT 15000.00000000,
    rolling_window_minutes INT NOT NULL DEFAULT 60,
    check_interval_seconds INT NOT NULL DEFAULT 30,
    telegram_config_id BIGINT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大额投注监控配置';

CREATE TABLE IF NOT EXISTS large_bet_watch_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trader_address VARCHAR(42) NOT NULL,
    trader_name VARCHAR(255) NULL,
    profile_url VARCHAR(500) NOT NULL,
    market_id VARCHAR(100) NOT NULL,
    market_slug VARCHAR(255) NULL,
    market_title VARCHAR(500) NOT NULL,
    sport_type VARCHAR(30) NOT NULL,
    outcome VARCHAR(100) NOT NULL,
    trigger_reason VARCHAR(30) NOT NULL,
    last_single_amount DECIMAL(20, 8) NOT NULL DEFAULT 0.00000000,
    last_cumulative_amount DECIMAL(20, 8) NOT NULL DEFAULT 0.00000000,
    first_triggered_at BIGINT NOT NULL,
    last_triggered_at BIGINT NOT NULL,
    trigger_count INT NOT NULL DEFAULT 1,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    UNIQUE KEY uk_large_bet_record (trader_address, market_id, outcome),
    INDEX idx_large_bet_last_triggered_at (last_triggered_at),
    INDEX idx_large_bet_sport_type (sport_type),
    INDEX idx_large_bet_trader_address (trader_address)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大额投注触发备案';
```

- [ ] **Step 2: Add entities and repositories**

Use `@Entity`, `@Table`, `@Id`, and `@GeneratedValue` matching the existing entity style. `LargeBetMonitorConfig` fields mirror the config table. `LargeBetWatchRecord` fields mirror the record table. Repositories extend `JpaRepository<..., Long>` and include:

```kotlin
interface LargeBetMonitorConfigRepository : JpaRepository<LargeBetMonitorConfig, Long>

interface LargeBetWatchRecordRepository : JpaRepository<LargeBetWatchRecord, Long> {
    fun findByTraderAddressAndMarketIdAndOutcome(
        traderAddress: String,
        marketId: String,
        outcome: String
    ): LargeBetWatchRecord?

    fun findAllByOrderByLastTriggeredAtDesc(): List<LargeBetWatchRecord>
}
```

- [ ] **Step 3: Add DTOs**

Create request and response types for config, record list, status, and test push:

```kotlin
data class LargeBetMonitorConfigDto(
    val id: Long?,
    val enabled: Boolean,
    val footballEnabled: Boolean,
    val basketballEnabled: Boolean,
    val singleTradeThreshold: String,
    val cumulativeTradeThreshold: String,
    val rollingWindowMinutes: Int,
    val checkIntervalSeconds: Int,
    val telegramConfigId: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

data class LargeBetMonitorConfigUpdateRequest(
    val enabled: Boolean,
    val footballEnabled: Boolean,
    val basketballEnabled: Boolean,
    val singleTradeThreshold: String,
    val cumulativeTradeThreshold: String,
    val rollingWindowMinutes: Int,
    val checkIntervalSeconds: Int,
    val telegramConfigId: Long?
)

data class LargeBetWatchRecordDto(
    val id: Long?,
    val traderName: String?,
    val traderAddress: String,
    val profileUrl: String,
    val marketTitle: String,
    val marketSlug: String?,
    val sportType: String,
    val outcome: String,
    val triggerReason: String,
    val lastSingleAmount: String,
    val lastCumulativeAmount: String,
    val firstTriggeredAt: Long,
    val lastTriggeredAt: Long,
    val triggerCount: Int
)

data class LargeBetMonitorStatusDto(
    val enabled: Boolean,
    val connected: Boolean,
    val trackedBuckets: Int
)
```

- [ ] **Step 4: Run backend compile**

Run: `cd backend; .\gradlew.bat compileKotlin`

Expected: Kotlin compilation passes.

---

### Task 2: Config API and Frontend Shell

**Files:**
- Create: `backend/src/main/kotlin/com/wrbug/polymarketbot/service/largebet/LargeBetMonitorConfigService.kt`
- Create: `backend/src/main/kotlin/com/wrbug/polymarketbot/controller/system/LargeBetMonitorController.kt`
- Modify: `frontend/src/services/api.ts`
- Create: `frontend/src/pages/LargeBetMonitor.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/components/Layout.tsx`

- [ ] **Step 1: Implement config service**

`LargeBetMonitorConfigService` should create a default config row if none exists. Validate:

- thresholds parse as positive decimals;
- rolling window is between 1 and 1440 minutes;
- check interval is between 5 and 3600 seconds;
- at least one sport is enabled when monitor is enabled.

- [ ] **Step 2: Implement controller endpoints**

Add endpoints:

- `POST /api/system/large-bet-monitor/config`
- `POST /api/system/large-bet-monitor/config/update`
- `POST /api/system/large-bet-monitor/records/list`
- `POST /api/system/large-bet-monitor/status`
- `POST /api/system/large-bet-monitor/test`

All responses use `ApiResponse.success(...)` or `ApiResponse.paramError(...)`.

- [ ] **Step 3: Add frontend API methods**

In `apiService`, add:

```ts
largeBetMonitor: {
  getConfig: () => apiClient.post<ApiResponse<LargeBetMonitorConfig>>('/system/large-bet-monitor/config', {}),
  updateConfig: (data: LargeBetMonitorConfigUpdateRequest) =>
    apiClient.post<ApiResponse<LargeBetMonitorConfig>>('/system/large-bet-monitor/config/update', data),
  listRecords: () => apiClient.post<ApiResponse<LargeBetWatchRecord[]>>('/system/large-bet-monitor/records/list', {}),
  getStatus: () => apiClient.post<ApiResponse<LargeBetMonitorStatus>>('/system/large-bet-monitor/status', {}),
  test: () => apiClient.post<ApiResponse<boolean>>('/system/large-bet-monitor/test', {}),
}
```

- [ ] **Step 4: Add page shell and route**

`LargeBetMonitor.tsx` initially renders a form with switch, sport checkboxes, threshold inputs, rolling window input, check interval input, save button, test button, status text, and records table.

Add lazy route in `App.tsx`:

```tsx
const LargeBetMonitor = lazy(() => import('./pages/LargeBetMonitor'))
...
<Route path="/large-bet-monitor" element={<ProtectedRoute><LazyRoute><LargeBetMonitor /></LazyRoute></ProtectedRoute>} />
```

Add menu item in `Layout.tsx` near statistics:

```tsx
{
  key: '/large-bet-monitor',
  icon: <AlertOutlined />,
  label: t('menu.largeBetMonitor') || '大额投注监控',
}
```

- [ ] **Step 5: Run frontend checks**

Run: `cd frontend; npm test -- --runInBand`

Expected: existing tests pass or unrelated existing failures are documented before continuing.

---

### Task 3: Trade Classification and Rolling Aggregation

**Files:**
- Create: `backend/src/main/kotlin/com/wrbug/polymarketbot/service/largebet/LargeBetMarketClassifier.kt`
- Create: `backend/src/main/kotlin/com/wrbug/polymarketbot/service/largebet/LargeBetRollingAggregator.kt`
- Create: `backend/src/test/kotlin/com/wrbug/polymarketbot/service/largebet/LargeBetRollingAggregatorTest.kt`

- [ ] **Step 1: Write aggregation tests**

Test cases:

- one trade over 5000 triggers `SINGLE`;
- three 6000 trades in 60 minutes trigger `CUMULATIVE`;
- a trade older than 60 minutes is not included;
- same user and market but different outcome uses separate buckets.

- [ ] **Step 2: Implement aggregator**

Create a normalized event:

```kotlin
data class LargeBetTradeEvent(
    val tradeId: String,
    val traderAddress: String,
    val traderName: String?,
    val marketId: String,
    val marketSlug: String?,
    val marketTitle: String,
    val sportType: String,
    val outcome: String,
    val price: BigDecimal,
    val size: BigDecimal,
    val timestampMillis: Long
) {
    val amount: BigDecimal get() = price.multiply(size)
}
```

`LargeBetRollingAggregator` stores events per key `traderAddress + marketId + outcome`, removes events older than `rollingWindowMinutes`, and returns:

```kotlin
data class LargeBetTriggerResult(
    val singleTriggered: Boolean,
    val cumulativeTriggered: Boolean,
    val singleAmount: BigDecimal,
    val cumulativeAmount: BigDecimal
)
```

- [ ] **Step 3: Implement market classifier**

Resolve market metadata from `PolymarketGammaApi.listMarkets(conditionIds = listOf(conditionId), includeTag = true)`. Classify as:

- `FOOTBALL` when category, event title, or market title includes football/soccer;
- `BASKETBALL` when category, event title, or market title includes basketball/nba/ncaab;
- null otherwise.

Cache classification by condition id for at least 30 minutes using Caffeine.

- [ ] **Step 4: Run tests**

Run: `cd backend; .\gradlew.bat test --tests "*LargeBetRollingAggregatorTest"`

Expected: all new aggregation tests pass.

---

### Task 4: Watch Records and Telegram Alerts

**Files:**
- Create: `backend/src/main/kotlin/com/wrbug/polymarketbot/service/largebet/LargeBetWatchRecordService.kt`
- Create: `backend/src/main/kotlin/com/wrbug/polymarketbot/service/largebet/LargeBetTelegramAlertService.kt`
- Modify: `backend/src/main/kotlin/com/wrbug/polymarketbot/service/system/TelegramNotificationService.kt`
- Create: `backend/src/test/kotlin/com/wrbug/polymarketbot/service/largebet/LargeBetTelegramAlertServiceTest.kt`

- [ ] **Step 1: Implement watch record upsert**

Upsert by `traderAddress + marketId + outcome`. On first trigger set `firstTriggeredAt` and `triggerCount = 1`. On later trigger update last amounts, reason, name, link, `lastTriggeredAt`, and increment `triggerCount`.

- [ ] **Step 2: Add Telegram alert method**

Add a public method in `TelegramNotificationService`:

```kotlin
suspend fun sendLargeBetMonitorMessage(message: String, configId: Long?): Boolean
```

If `configId` is present, send only to that config. Otherwise send to `MONITOR_ONLY` Telegram configs.

- [ ] **Step 3: Implement alert message builder**

Message format:

```html
🚨 <b>大额投注监控</b>

盘口：<a href="{market_link}">{market_title}</a>
类型：{sport_type}
用户：<a href="{profile_url}">{trader_display}</a>
方向：<b>{outcome}</b>
单笔成交：<code>{single_amount}</code> USDC
窗口累计：<code>{cumulative_amount}</code> USDC
触发原因：{trigger_reason}
时间：<code>{time}</code>
```

Escape HTML for user name, market title, sport type, outcome, and reason.

- [ ] **Step 4: Test alert content**

Test that user name becomes a clickable profile link and that missing names fall back to shortened address.

---

### Task 5: Live Activity Monitor

**Files:**
- Create: `backend/src/main/kotlin/com/wrbug/polymarketbot/service/largebet/LargeBetActivityMonitorService.kt`
- Modify: `backend/src/main/kotlin/com/wrbug/polymarketbot/dto/ActivityTradeMessageDto.kt` only if payload fields are missing.
- Create: `backend/src/test/kotlin/com/wrbug/polymarketbot/service/largebet/LargeBetActivityMonitorServiceTest.kt`

- [ ] **Step 1: Implement platform-wide subscription**

Subscribe to Polymarket activity trades. Do not filter by leader addresses. Ignore non-trade/non-fill events. Deduplicate by transaction hash; when absent use `traderAddress + conditionId + asset + side + price + size + timestamp`.

- [ ] **Step 2: Normalize filled trade**

From `ActivityTradePayload`, require:

- trader address from `payload.trader.address` or `payload.proxyWallet`;
- condition id;
- outcome or outcome index;
- price;
- size;
- timestamp.

Amount is `price * size`.

- [ ] **Step 3: Apply config and filters**

Skip when disabled. Skip when sport type is disabled. Skip if classification is null. Feed valid events into aggregator. Trigger alert and watch record when either threshold is met.

- [ ] **Step 4: Expose status**

Return enabled, websocket connected, and tracked bucket count through service and controller.

- [ ] **Step 5: Run backend tests**

Run: `cd backend; .\gradlew.bat test --tests "*LargeBet*"`

Expected: all large bet tests pass.

---

### Task 6: Frontend Completion

**Files:**
- Modify: `frontend/src/pages/LargeBetMonitor.tsx`
- Modify: `frontend/src/locales/zh-CN/common.json`
- Modify: `frontend/src/locales/zh-TW/common.json`
- Modify: `frontend/src/locales/en/common.json`
- Create: `frontend/tests/largeBetMonitor.test.tsx`

- [ ] **Step 1: Add form validation**

Rules:

- single threshold must be greater than 0;
- cumulative threshold must be greater than 0;
- rolling window must be 1 to 1440;
- check interval must be 5 to 3600;
- if enabled, at least one sport checkbox is selected.

- [ ] **Step 2: Add records table**

Columns:

- user;
- market;
- sport;
- outcome;
- trigger reason;
- single amount;
- cumulative amount;
- trigger count;
- last trigger time.

User and market are links.

- [ ] **Step 3: Add tests**

Test loading config, saving edited thresholds, rejecting invalid values, and rendering one watch record.

- [ ] **Step 4: Run frontend verification**

Run: `cd frontend; npm test -- largeBetMonitor`

Expected: new frontend tests pass.

---

### Task 7: Full Verification

**Files:**
- No new files unless verification exposes a defect.

- [ ] **Step 1: Run backend tests**

Run: `cd backend; .\gradlew.bat test`

Expected: all backend tests pass or unrelated existing failures are documented with exact failing test names.

- [ ] **Step 2: Run frontend tests and build**

Run:

```powershell
cd frontend
npm test
npm run build
```

Expected: tests and build pass.

- [ ] **Step 3: Manual browser verification**

Start the app using the existing launcher or dev servers. Open `/large-bet-monitor` and verify:

- menu entry appears;
- config loads with defaults 5000 / 15000 / 60;
- saving config persists values;
- test push sends to the selected monitor Telegram config;
- original `/system-settings/notification` page still loads.

---

## Self-Review

- Spec coverage: The plan covers independent menu, configurable thresholds, rolling window, football/basketball filters, filled-trade-only handling, profile links, Telegram alerts, and watch records.
- Placeholder scan: No implementation step relies on an unspecified placeholder.
- Type consistency: DTO, entity, repository, service, controller, and frontend route names use the `LargeBet` prefix consistently.
