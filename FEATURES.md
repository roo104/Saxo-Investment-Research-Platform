# Features & Roadmap

The vision: grow this from an instrument-research sample into a **mini retail investing tool** on
top of the Saxo OpenAPI — search, research, watch, analyse, and (eventually) trade, with the same
safe simulation/live switch throughout.

This document is the running catalogue of *what the platform does and could do*. It is grounded in
what the [Saxo OpenAPI](https://www.developer.saxo/) actually exposes (Reference Data, Portfolio,
Trading, Chart, Root/streaming service groups), so every "planned" item has a real endpoint behind
it.

**Status legend:** ✅ built · 🟡 partial / in progress · ⬜ planned

---

## 1. Instruments & reference data

| Feature                                                     | Status | Notes / Saxo endpoint                          |
|-------------------------------------------------------------|--------|------------------------------------------------|
| Free-text instrument search                                 | ✅      | `GET /api/instruments` → `ref/v1/instruments`  |
| Filter by asset type & exchange                             | ✅      | query params on search                         |
| Instrument detail view (tick size, lot size, trading hours) | ⬜      | `ref/v1/instruments/details/{uic}/{assetType}` |
| Exchange directory & trading sessions                       | ⬜      | `ref/v1/exchanges`                             |
| Currencies, countries, cultures metadata                    | ⬜      | `ref/v1/currencies`, `ref/v1/countries`        |
| Option/futures roots & expiries                             | ⬜      | `ref/v1/instruments/contractoptionspaces`      |
| Related instruments / "people also watch"                   | ⬜      | derived from search + sectors                  |

## 2. Market data & pricing

| Feature                                       | Status | Notes                                                                                                             |
|-----------------------------------------------|--------|-------------------------------------------------------------------------------------------------------------------|
| Snapshot info-prices (bid/ask/mid)            | ✅      | `trade/v1/infoprices/list`, mapped in watchlist                                                                   |
| Honest "no quote" when not entitled           | ✅      | detects Saxo `NoAccess` price type                                                                                |
| Snapshot fallback poll in the UI              | ✅      | 15s reconcile poll; live prices come from streaming                                                               |
| Real-time **streaming** prices (WebSocket)    | ✅      | Saxo streaming WS + `trade/v1/prices/subscriptions`, pushed to the browser over SSE (`GET /api/watchlist/stream`) |
| Depth / order book                            | ⬜      | `trade/v1/prices` with `MarketDepth` field group                                                                  |
| Delayed-data badges & market-state indicators | 🟡     | market state shown; needs clearer UX                                                                              |

## 3. Charts & technical analysis

| Feature                                   | Status | Notes                                                                                                                                                                                   |
|-------------------------------------------|--------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Historical OHLC candles                   | ✅      | `GET /api/watchlist/{id}/history` → `chart/v3/charts`                                                                                                                                   |
| Selectable ranges (1W / 1M / 3M)          | ✅      | horizon + count presets in the UI                                                                                                                                                       |
| Line + area chart with hover crosshair    | ✅      | custom SVG `PriceChart`                                                                                                                                                                 |
| Candlestick / OHLC bar view               | ✅      | Line / Candles toggle in the chart; green-up / red-down candles with OHLC hover                                                                                                         |
| More ranges & intraday (1D, 5Y, custom)   | ⬜      | more horizon/count presets                                                                                                                                                              |
| Volume sub-panel                          | ⬜      | `Volume` field from chart data (securities)                                                                                                                                             |
| Indicators: SMA/EMA, RSI, MACD, Bollinger | ⬜      | computed client- or server-side over candles                                                                                                                                            |
| Compare / overlay multiple instruments    | ⬜      | indexed-to-100 multi-series                                                                                                                                                             |
| Streaming chart updates                   | ✅      | real OHLC candle subscription (`chart/v3/charts/subscriptions`) → SSE (`GET /api/watchlist/{id}/chart/stream`); the current candle's high/low/close update live and new candles roll in |

## 4. Watchlists

| Feature                              | Status | Notes                            |
|--------------------------------------|--------|----------------------------------|
| Persisted watchlist with live quotes | ✅      | JPA/MySQL, `port`-independent    |
| Add / remove instruments             | ✅      | `POST` / `DELETE /api/watchlist` |
| Multiple named watchlists            | ⬜      | list entity + membership         |
| Reordering & tagging                 | ⬜      |                                  |
| Per-item notes / thesis              | ⬜      |                                  |
| Import/export (CSV)                  | ⬜      |                                  |

## 5. Portfolio & accounts

| Feature                            | Status | Notes                                       |
|------------------------------------|--------|---------------------------------------------|
| Account balance & cash             | ⬜      | `port/v1/balances`                          |
| Open positions & net positions     | ⬜      | `port/v1/positions`, `port/v1/netpositions` |
| Orders (open / filled / cancelled) | ⬜      | `port/v1/orders`, `port/v1/closedpositions` |
| Account & client switching         | ⬜      | `port/v1/accounts`, `port/v1/clients`       |
| Performance & returns over time    | ⬜      | `hist/v3/performance`, `hist/v4/positions`  |
| Live P&L (streaming)               | ⬜      | position subscriptions                      |

## 6. Trading & orders *(live only — requires real auth + confirmations)*

| Feature                                         | Status | Notes                                     |
|-------------------------------------------------|--------|-------------------------------------------|
| Place / modify / cancel orders                  | ⬜      | `trade/v2/orders`                         |
| Order types: market, limit, stop, trailing-stop | ⬜      |                                           |
| Related orders (OCO, take-profit / stop-loss)   | ⬜      |                                           |
| Pre-trade cost & margin impact                  | ⬜      | `trade/v1/pretrade/costs`, `.../margin`   |
| Trade disclaimers & confirmations               | ⬜      | `trade/v1/messages`                       |
| Paper-trading in simulation                     | 🟡     | env switch exists; trading flow not built |

## 7. Alerts & notifications

| Feature                               | Status | Notes                             |
|---------------------------------------|--------|-----------------------------------|
| Price threshold alerts (above/below)  | ⬜      | evaluate against streaming prices |
| % move / volatility alerts            | ⬜      |                                   |
| Delivery: in-app, email, webhook/push | ⬜      |                                   |

## 8. Research & screening

| Feature                                         | Status | Notes                                                                                                                                                                                                                                                                                                                                                                                                   |
|-------------------------------------------------|--------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| News feed per instrument                        | ⬜      | Saxo news service group                                                                                                                                                                                                                                                                                                                                                                                 |
| Corporate actions, dividends, earnings calendar | ⬜      |                                                                                                                                                                                                                                                                                                                                                                                                         |
| Fundamentals: key stats & financials view       | ✅      | Key stats + Financials (per year/quarter) UI (`GET /api/watchlist/{id}/fundamentals`). Saxo has **no** fundamentals API, so served via a `FundamentalsProvider` seam: **live data from Financial Modeling Prep** (`FmpFundamentalsProvider`, requires `FMP_API_KEY`). No mock/sample fallback — **app fails to start** without a key, 404 when the symbol is unmapped, `available=false` for non-equity |
| Screener (filter universe by criteria)          | ⬜      | built on instrument search + metrics                                                                                                                                                                                                                                                                                                                                                                    |

## 9. Risk & analytics

| Feature                                     | Status | Notes                       |
|---------------------------------------------|--------|-----------------------------|
| Exposure by asset class / currency / sector | ⬜      | aggregated from positions   |
| P&L attribution                             | ⬜      |                             |
| Correlation & simple portfolio stats        | ⬜      | computed over price history |

## 10. Platform, auth & UX

| Feature                                       | Status | Notes                             |
|-----------------------------------------------|--------|-----------------------------------|
| Simulation ⇄ live environment switch          | ✅      | config-driven, safe (badge in UI) |
| 24-hour simulation token auth                 | ✅      | `StaticTokenProvider`             |
| OAuth 2.0 Authorization Code + PKCE + refresh | ⬜      | slots into `SaxoTokenProvider`    |
| Multi-user accounts & sessions                | ⬜      |                                   |
| Dark theme (trading-desk aesthetic)           | ✅      |                                   |
| Light theme                                   | ⬜      |                                   |
| Responsive / mobile layout                    | 🟡     | grid collapses; needs polish      |
| Internationalisation & locale formatting      | 🟡     | number/date locale-aware          |
| Data export (CSV/Excel)                       | ⬜      |                                   |

## 11. Engineering & operations

| Feature                                      | Status | Notes                                                                                      |
|----------------------------------------------|--------|--------------------------------------------------------------------------------------------|
| OpenAPI-documented REST API + Swagger UI     | ✅      | springdoc                                                                                  |
| Coroutine-based non-blocking backend         | ✅      | WebFlux + Kotlin coroutines                                                                |
| Unit / controller / integration tests        | ✅      | MockWebServer, MockK, Testcontainers                                                       |
| RFC-7807 error handling (no silent failures) | ✅      | upstream Saxo errors surfaced                                                              |
| Streaming/WebSocket infrastructure           | ✅      | `SaxoPriceStream` (binary frame parser, subscription lifecycle, delta merge) + SSE fan-out |
| Rate-limit awareness & backoff               | ⬜      | Saxo publishes per-endpoint limits                                                         |
| Response caching for reference data          | ⬜      | exchanges/instruments change rarely                                                        |
| Observability (metrics, tracing, health)     | ⬜      | Actuator + Micrometer                                                                      |
| CI/CD pipeline & container image             | ⬜      | `bootBuildImage` available                                                                 |

---

## Suggested phasing

- **Phase 1 — Research (now):** search ✅, watchlist ✅, quotes ✅, charts ✅. Next: instrument
  detail, candlestick view, indicators, multiple watchlists.
- **Phase 2 — Real-time & portfolio:** live price streaming ✅ (WebSocket → SSE). Next: streaming
  charts, read-only portfolio (balances, positions, performance), and price alerts.
- **Phase 3 — Trading (live):** OAuth/PKCE auth, pre-trade cost/margin, order placement with
  confirmations — simulation-first, then gated live.
- **Phase 4 — Retail polish:** screening, news/fundamentals, risk analytics, multi-user, alerts
  delivery, mobile.

## Design principles (keep these as it grows)

1. **Backend owns all Saxo communication** — the frontend never holds tokens or hits Saxo directly.
2. **Environment safety** — simulation vs live is explicit, visible, and config-gated; trading is
   never a silent default.
3. **No silent failures** — upstream errors and missing entitlements are surfaced honestly.
4. **Everything documented via OpenAPI**, everything covered by tests.

> See [`README.md`](README.md) for how to run what exists today.
