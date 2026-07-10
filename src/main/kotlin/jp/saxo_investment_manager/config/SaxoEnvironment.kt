package jp.saxo_investment_manager.config

/**
 * The Saxo OpenAPI environments the platform can talk to.
 *
 * Simulation is a full sandbox seeded with fake money and market data; it is the only
 * environment a developer-portal 24-hour token is valid for. Live is the real brokerage
 * environment and requires a proper OAuth flow.
 *
 * The active environment is chosen once at startup via the `saxo.environment` property
 * (see [SaxoProperties]); switching is a config change plus a restart, which keeps us from
 * accidentally issuing requests against Live.
 */
enum class SaxoEnvironment(val restBaseUrl: String, val streamingBaseUrl: String) {
    // streamingBaseUrl is the base for the WebSocket; the client appends "/connect" and "/authorize".
    // (Saxo moved streaming off streaming.saxobank.com to *-streaming.saxobank.com in late 2025.)
    SIMULATION("https://gateway.saxobank.com/sim/openapi", "wss://sim-streaming.saxobank.com/sim/oapi/streaming/ws"),
    LIVE("https://gateway.saxobank.com/openapi", "wss://live-streaming.saxobank.com/oapi/streaming/ws"),
}
