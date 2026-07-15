# Saxo Investment Research Platform

An investment **research** platform built on the [Saxo Bank OpenAPI](https://www.developer.saxo/).
A Kotlin backend owns all communication with Saxo — authentication, environment selection, and
request/response mapping — and exposes a small, OpenAPI-documented REST API. A React frontend
visualises it.

The platform can talk to Saxo's **simulation** sandbox or the **live** brokerage environment; you
choose which at startup. It ships configured for simulation with a developer-portal 24-hour token.

The included sample feature is **instrument search + a live-quote portfolio**: search the Saxo
universe, pin instruments to a persisted portfolio, watch their bid/ask/mid update **live** (Saxo
WebSocket streaming, pushed to the browser over Server-Sent Events), and expand any row to see a
**price-history chart** (live-streaming OHLC candles from Saxo, as a line or candlestick view with
1m / 1D / 1W / 1M ranges), plus a **fundamentals** view (key stats + financials — see the note below).

> The longer-term vision — everything this could become as a mini retail investing tool, with a
> status marker for what's built vs. planned — lives in **[`FEATURES.md`](FEATURES.md)**.

---

## Architecture

```
  React + Vite (TypeScript)
        │   fetch /api/*   (Vite dev-server proxy → :8080)
        ▼
  Spring WebFlux REST  ──  suspend controllers, OpenAPI / Swagger UI
        │
     services (Kotlin coroutines)  ──  JPA / MySQL  (portfolio persistence)
        │
     SaxoClient (WebClient + coroutines)
        │   Authorization: Bearer <token>   ← SaxoTokenProvider
        ▼
  Saxo OpenAPI      SIMULATION  |  LIVE      (selected by configuration)
```

**Environment switching is config-driven and deliberately safe.** The `saxo.environment` property
picks the base URL and the matching token; a simulation token is never sent to Live. The active
environment is exposed at `GET /api/environment` and shown as a badge in the UI. Switching means
changing configuration and restarting — there is no runtime toggle that could accidentally hit Live.

### Tech stack

| Layer     | Choice                                                                  |
|-----------|-------------------------------------------------------------------------|
| Language  | Kotlin 2.3 (coroutines), Java 25 toolchain                              |
| Framework | Spring Boot 4.1, Spring WebFlux (reactive), Spring Data JPA             |
| HTTP      | `WebClient` with coroutine (`awaitBody`) extensions                     |
| Database  | MySQL 8 (Testcontainers for tests)                                      |
| Docs      | springdoc-openapi → Swagger UI + OpenAPI 3 spec                         |
| Frontend  | React 19 + TypeScript + Vite                                            |
| Testing   | JUnit 5, MockK, MockWebServer, Testcontainers, Vitest + Testing Library |

---

## Prerequisites

- JDK 25 (the Gradle build declares a toolchain 25; if it is not installed, Gradle will attempt to
  provision it via the foojay resolver). JDK 21+ is fine for *running* Gradle itself.
- Docker (for MySQL and for the Testcontainers-based tests).
- Node.js 20+ and npm (for the frontend).

## Get a simulation token

1. Sign in at the [Saxo Developer Portal](https://www.developer.saxo/).
2. Open your application → **24-Hour Token** and copy it. It is valid for simulation only and
   expires after 24 hours (refresh it the same way when it lapses).

## Configuration

All configuration is read from environment variables (see `src/main/resources/application.yml`):

| Variable           | Default                                                          | Purpose                                                                       |
|--------------------|------------------------------------------------------------------|-------------------------------------------------------------------------------|
| `SAXO_ENV`         | `simulation`                                                     | Active environment: `simulation`/`live`                                       |
| `SAXO_SIM_TOKEN`   | *(empty)*                                                        | Bearer token for simulation                                                   |
| `SAXO_LIVE_TOKEN`  | *(empty)*                                                        | Bearer token for live                                                         |
| `SAXO_DB_URL`      | `jdbc:mysql://localhost:3306/saxo?createDatabaseIfNotExist=true` | JDBC URL                                                                      |
| `SAXO_DB_USER`     | `saxo`                                                           | DB user                                                                       |
| `SAXO_DB_PASSWORD` | `saxo`                                                           | DB password                                                                   |
| `FMP_API_KEY`      | *(empty)*                                                        | Financial Modeling Prep key — **required**; the app fails to start without it |

Tokens are never committed and never written to disk by the app.

---

## Run it

### 1. Start MySQL

```bash
docker compose up -d
```

### 2. Start the backend

```bash
export SAXO_ENV=simulation
export SAXO_SIM_TOKEN=<your 24-hour token>
./gradlew bootRun
```

- API base: `http://localhost:8080/api`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI spec: `http://localhost:8080/v3/api-docs`

### 3. Start the frontend

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173  (proxies /api to :8080)
```

Search for an instrument, click **Add**, and watch the quote refresh in the portfolio.

> **Tip — market-data entitlements.** A fresh simulation account is typically entitled to FX prices
> but *not* to live equity/exchange data. Saxo then returns a quote marked `NoAccess`, which the app
> shows honestly as *"no quote"*. For a live-updating demo, add an **FxSpot** pair such as *EURUSD*
> (`uic 21`); equities will still search and persist, they just won't show a price until the account
> is entitled.

### Switching to live

Set `SAXO_ENV=live` and provide `SAXO_LIVE_TOKEN` (obtained via a proper OAuth flow — see below),
then restart. The environment badge turns red to make the active environment unmistakable.

---

## REST API

| Method & path                          | Description                                                  |
|----------------------------------------|--------------------------------------------------------------|
| `GET /api/instruments`                 | Search instruments (`keywords`, `assetTypes`, `exchangeId`)  |
| `GET /api/portfolio`                   | List portfolio entries with their latest quotes              |
| `POST /api/portfolio`                  | Add an instrument `{ "uic": 211, "assetType": "Stock" }`     |
| `DELETE /api/portfolio/{id}`           | Remove a portfolio entry                                     |
| `GET /api/portfolio/{id}/history`      | OHLC price candles (`horizon` minutes, `count`) for charting |
| `GET /api/portfolio/stream`            | **SSE** stream of live prices for portfolio instruments      |
| `GET /api/portfolio/{id}/chart/stream` | **SSE** stream of live OHLC candles (`horizon`, `count`)     |
| `GET /api/portfolio/{id}/fundamentals` | Company key stats & financials (live from FMP — see note)    |
| `GET /api/account`                     | Read-only account overview: accounts + aggregate balance     |
| `GET /api/account/positions`           | Open net positions valued with current prices (P/L per line) |
| `GET /api/environment`                 | The active Saxo environment                                  |

Errors are returned as RFC-7807 `application/problem+json`. An upstream Saxo error (e.g. an expired
token) is surfaced with its status and body rather than being swallowed into an empty result.

---

## Testing

```bash
./gradlew test          # backend: unit + controller + Testcontainers integration (needs Docker)
cd frontend && npm test # frontend: Vitest component tests
```

Backend test layers:

- **Client/service unit tests** — `MockWebServer` stubs Saxo; `MockK` fakes collaborators. No DB, no Docker.
- **Controller tests** — `@WebFluxTest` + `WebTestClient`, including validation and the Saxo-error → problem+json path.
- **Repository + context tests** — `@DataJpaTest` / `@SpringBootTest` against a real MySQL via Testcontainers.

---

## Project layout

```
src/main/kotlin/jp/saxo_investment_manager/
  config/     SaxoEnvironment, SaxoProperties, SaxoTokenProvider, WebClientConfig, OpenApiConfig
  saxo/       Saxo DTOs + ReferenceDataClient, PricingClient, ChartClient (coroutine WebClient)
  streaming/  StreamingMessageParser (binary frames) + SaxoPriceStream, SaxoChartStream (WS → Flows)
  fundamentals/ FundamentalsProvider seam + FmpFundamentalsProvider (live data from FMP; no mock fallback)
  portfolio/  PortfolioItem entity + PortfolioRepository (JPA)
  service/    InstrumentService, PortfolioService
  api/        Controllers (incl. SSE StreamController, FundamentalsController), API DTOs, RFC-7807 exception handler
frontend/src/
  api.ts, types.ts            typed API client
  components/                 SearchPanel, Portfolio, PortfolioRow, PriceChart, Fundamentals, EnvironmentBadge
  App.tsx                     orchestration + polling
```

---

## Authentication: current state and next step

Today the backend authenticates with a **static bearer token** (`StaticTokenProvider`) — ideal for
the simulation sandbox. Production/live use should implement OAuth 2.0 Authorization Code with PKCE
and token refresh. The seam already exists: implement `SaxoTokenProvider.accessToken()` (it is a
`suspend` function, so it can refresh over the network) and register it in place of
`StaticTokenProvider`. No calling code changes.

## Fundamentals data source

Saxo's OpenAPI does **not** expose company fundamentals (EPS, revenue, financial statements — see
[Saxo support](https://openapi.help.saxo/hc/en-us/articles/4418418812689-How-do-I-find-fiscal-numbers-like-EPS-EBITDA-etc-on-companies)).
The **Fundamentals** view (key stats + financials) is served through a `FundamentalsProvider` seam,
implemented by **`FmpFundamentalsProvider`**. It always serves **live** data or an error — there is
**no mock/sample fallback**:

- `FMP_API_KEY` is **required**. Without it the **application fails to start** (fail-fast).
- With a key set, it maps the Saxo symbol to an FMP ticker (US listings map cleanly; some venues get
  a suffix) and fetches quote/ratios/statements in parallel from
  [Financial Modeling Prep](https://financialmodelingprep.com) (v3 API).
- If FMP doesn't recognise the ticker, the endpoint returns **404 Not Found** (no fabricated data).

Non-equity instruments (FX, bonds) report `available = false`. To use a different feed
(Morningstar/FactSet), implement `FundamentalsProvider` and mark it `@Primary` — no calling-code
changes. Note FMP's free tier is rate-limited (~250 calls/day) and non-US symbol mapping is
best-effort (unmapped symbols return 404).
