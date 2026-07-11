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
| Volume sub-panel                          | ⬜      | `Volume` field from chart data (securities) — carried through the backend, not yet charted                                                                                              |
| Indicators: SMA/EMA, RSI, MACD, Bollinger | ✅      | computed server-side over candles (`jp.saxo_investment_manager.signals.Indicators`); see §8 Trade signals                                                                               |
| Indicator overlays + RSI/MACD sub-panel   | ✅      | SMA/Bollinger overlays on the chart (`PriceChart` `overlays` prop) + RSI/MACD oscillator panels, in the Signals tab                                                                     |
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
| Trade signals (self-computed)                   | ✅      | Signals tab: net bias + bullish/bearish cards (SMA 50/200 cross, price vs SMA 50, RSI 14, MACD, Bollinger) with chart overlays and an RSI/MACD sub-panel. Saxo's "Trade Signals" (Autochartist) is **not** in the API and Autochartist has no self-serve API tier, so signals are computed in-house from `chart/v3` candles (`GET /api/watchlist/{id}/signals`). No third-party dependency              |

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

---

## Appendix: Saxo OpenAPI — full service-group reference

The complete surface the [Saxo OpenAPI](https://www.developer.saxo/openapi/referencedocs) exposes,
independent of what this platform has built. The API is split into ~17 *service groups*; within each,
resources are either request/response or **streaming** subscriptions delivered over the streaming
server (WebSocket). The "Used here" column links back to the sections above.

### Core trading & market data

| Service group                | Key resources / capabilities                                                                                                                                              | Used here            |
|------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------|
| **Root Services** (`root/v1`) | Sessions & session capabilities, trade-session mode (arm for full trading), central subscription management, feature availability, diagnostics                             | ✅ streaming infra    |
| **Reference Data** (`ref/v1`) | Instruments (details/summaries, search), option roots, option/futures spaces, exchanges, countries, currencies, currency pairs, cultures, languages, time zones, standard dates, algo strategies | ✅ §1 search          |
| **Trading** (`trade/v1/v2`)   | InfoPrices/Prices (snapshot + streaming quotes, greeks), Orders (market/limit/stop/trailing/OCO/related), Algo orders, Multi-leg option strategies, Options chain, Block orders, Positions (incl. option exercise), Messages, pre-trade disclaimers & return codes | 🟡 §2 prices, §6 planned |
| **Chart** (`chart/v3`)        | Historical OHLC candles (1m → monthly), snapshot + streaming updates                                                                                                      | ✅ §3 charts          |
| **Portfolio** (`port/v1`)     | Users, Clients, Accounts, Balances, Positions, NetPositions, ClosedPositions, Orders, Exposure, expandable positions tree                                                  | ⬜ §5 planned         |

### History, reporting & client lifecycle

| Service group          | Key resources / capabilities                                                                                                       | Used here     |
|------------------------|-------------------------------------------------------------------------------------------------------------------------------------|---------------|
| **Account History** (`hist`) | Performance, Transactions / audit trail, Unsettled amounts                                                                     | ⬜ §5 planned  |
| **Client Reporting**   | PDF/XLS reports: account statement, portfolio report, trade details, trades executed                                                | ⬜             |
| **Client Services**    | Audit OrderActivities, partner support cases, reports (aggregated amounts, MiFID 2 cost reporting), subscription mgmt, fund transfers | ⬜             |
| **Client Management**  | Sign up and manage leads and clients (onboarding)                                                                                   | ⬜             |
| **Asset Transfers** *(beta)* | Cash transfers, cash withdrawal, prebooked funds, securities transfers, interaccount securities transfer                      | ⬜             |
| **Corporate Actions** *(licensed)* | Voluntary/mandatory events, elections, standing instructions, proxy voting                                             | ⬜             |

### Notifications, regulatory & value-add

| Service group             | Key resources / capabilities                                                                                                                         | Used here    |
|---------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|--------------|
| **Event Notification (ENS)** | Streaming client-activity events: orders, positions, position/account depreciation, account funding, margin calls, corporate actions, advisory rebalance, security transfers | ⬜ §7 planned |
| **Value Add** (`vas/v1`)  | Price alerts, news, forex/economic calendar, and other client-developer extras                                                                        | ⬜ §7/§8      |
| **Market Overview**       | Market movers — winners/losers by exchange                                                                                                            | ⬜ §8         |
| **Regulatory Services**   | Client regulatory information                                                                                                                          | ⬜            |
| **Disclaimer Management** | Manage legal text / notes shown to clients                                                                                                            | ⬜            |
| **Partner Integration**   | Endpoints for partners integrating with Saxo                                                                                                           | ⬜            |

> **Not offered by Saxo:** there is **no fundamentals / financial-statements endpoint** — that's why
> §8 sources fundamentals from Financial Modeling Prep, not Saxo. There is likewise **no trade-signals
> endpoint**: Saxo's in-platform "Trade Signals" are an Autochartist integration (Autochartist has no
> self-serve API tier), so §8 computes its own signals from the `chart/v3` candles instead. Note also
> that in **simulation**, entitlements are limited: FX has live streaming prices, equities return
> `NoAccess`.
>
> Source: [Saxo OpenAPI reference docs](https://www.developer.saxo/openapi/referencedocs) ·
> [service groups](https://www.developer.saxo/openapi/learn/service-groups).
