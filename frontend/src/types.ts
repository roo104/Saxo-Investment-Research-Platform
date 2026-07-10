// Mirrors the backend's camelCase API contract (see jp.saxo_investment_manager.api.ApiModels).
// Run `npm run gen:api` against a running backend to regenerate a full spec into api-schema.d.ts.

export interface Instrument {
    uic: number
    symbol: string
    description: string
    assetType: string
    exchangeId: string | null
    currencyCode: string | null
}

export interface WatchlistEntry {
    id: number
    uic: number
    symbol: string
    description: string
    assetType: string
    bid: number | null
    ask: number | null
    mid: number | null
    currency: string | null
    marketState: string | null
    delayedByMinutes: number | null
    priceAvailable: boolean
}

export interface EnvironmentInfo {
    environment: string
    restBaseUrl: string
}

export interface PricePoint {
    time: string
    open: number | null
    high: number | null
    low: number | null
    close: number | null
}

export interface PriceHistory {
    uic: number
    symbol: string
    assetType: string
    horizonMinutes: number
    currency: string | null
    points: PricePoint[]
}

export interface PriceTick {
    uic: number
    assetType: string
    bid: number | null
    ask: number | null
    mid: number | null
    currency: string | null
    marketState: string | null
    lastUpdated: string | null
    priceAvailable: boolean
}
