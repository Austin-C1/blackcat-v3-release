# Large Bet Monitor Design

## Goal

Add an independent Blackcat module that monitors filled Polymarket sports trades and sends Telegram alerts when any user crosses configurable single-trade or rolling cumulative thresholds.

## Scope

- Monitor football and basketball markets only.
- Count filled trades only. Open unmatched orders are ignored.
- Do not require a preconfigured user list. Any Polymarket user can trigger an alert.
- Trigger conditions:
  - single filled trade amount is greater than or equal to the configured single-trade threshold;
  - the same user, market, and outcome reaches the configured cumulative threshold inside the configured rolling time window.
- Store every triggering user as a watch record for later review.
- Keep existing copy trading, order push, position push, and notification behavior unchanged.

## User-Facing Page

Add a new independent menu item named "大额投注监控". The page contains:

- master enable switch;
- sport filters for football and basketball;
- single-trade threshold input, default 5000 USDC;
- cumulative threshold input, default 15000 USDC;
- rolling window input in minutes, default 60;
- polling or stream health status;
- Telegram bot settings for this module;
- test push button;
- watch record table showing triggered users, market, sport type, outcome, trigger reason, last single amount, rolling cumulative amount, and last trigger time.

## Alert Content

Telegram alerts show:

- market title;
- sport type;
- Polymarket user display name;
- Polymarket profile link;
- outcome / direction;
- single filled amount;
- rolling cumulative filled amount;
- trigger reason: single threshold, cumulative threshold, or both;
- time.

If the user display name is unavailable, the alert falls back to a shortened address while still including the profile link.

## Backend Design

Create a separate large-bet-monitor package under the existing backend. It owns:

- configuration entity and API;
- triggered user record entity and API;
- trade normalization from Activity WebSocket payloads;
- sport market filtering;
- rolling aggregation by user + market + outcome;
- alert deduplication;
- Telegram alert rendering.

The existing Activity WebSocket service is not converted into this module. Instead, extract or share only the generic subscription and parsing pieces needed to observe platform-wide filled trades. Existing leader copy trading keeps its current address-filtered path.

## Data Model

Add two tables:

- `large_bet_monitor_config`: stores enabled flag, sport filters, thresholds, rolling window, check interval, and selected Telegram config id.
- `large_bet_watch_record`: stores profile address, display name, profile link, market id/title/slug, sport type, outcome, trigger reason, last single amount, last rolling amount, first triggered time, last triggered time, and trigger count.

The rolling in-memory aggregation is sufficient for live detection. The watch record table is the persistent audit trail.

## Data Source

Use Polymarket activity trade events for filled trades. Ignore order events that do not represent fills. Market metadata is resolved through existing Gamma market APIs and cached by condition id / slug. Football and basketball filtering uses market event category/tags/title metadata, with a conservative fallback that ignores markets that cannot be classified.

## Telegram Separation

Large bet alerts use a monitor-specific Telegram selection. Existing order and copy trading templates remain unchanged. The current monitor-only Telegram config behavior can be reused, but the large-bet module must expose its own test push and must not route through standard order notification methods.

## Error Handling

- If the monitor is disabled, the background stream is stopped.
- If Telegram is not configured, records are still stored but alerts are skipped with a log entry.
- If market classification fails, the event is ignored rather than alerted.
- Duplicate trade events are ignored by transaction hash or a deterministic fallback key.
- If profile name is missing, fallback display is address abbreviation.

## Testing

Backend unit tests cover:

- single threshold triggering;
- cumulative threshold triggering within a rolling window;
- old trades falling out of the rolling window;
- football and basketball filtering;
- ignored unmatched/non-fill events;
- duplicate event suppression;
- watch record upsert behavior;
- Telegram message content.

Frontend tests cover:

- config loading and saving;
- validation for positive numeric thresholds and time window;
- watch record rendering;
- test push button behavior.

Manual verification covers:

- page appears in the new menu;
- original notification page still works;
- monitor can be enabled and disabled;
- a sample filled trade over 5000 USDC creates a record and sends the expected Telegram message.
