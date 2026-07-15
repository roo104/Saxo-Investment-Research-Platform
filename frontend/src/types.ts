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

export interface PortfolioEntry {
    id: number
    uic: number
    symbol: string
    description: string
    assetType: string
    quantity: number | null
    openingPrice: number | null
    sector: string | null
    bid: number | null
    ask: number | null
    mid: number | null
    currency: string | null
    marketState: string | null
    delayedByMinutes: number | null
    priceAvailable: boolean
    lastClose: number | null
    exchange: string | null
    country: string | null
    marketOpen: boolean | null
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

export type MarketRegion = 'Americas' | 'Europe' | 'Asia'

export interface IndexSeries {
    key: string
    name: string
    region: MarketRegion
    currency: string | null
    marketOpen: boolean
    points: PricePoint[]
}

// How a raw numeric fundamentals value should be rendered (mirrors the backend StatUnit enum).
export type StatUnit = 'RATIO' | 'PERCENT' | 'MONEY' | 'MONEY_BILLIONS'

export interface KeyStat {
    label: string
    value: number
    unit: StatUnit
}

export interface FinancialRow {
    label: string
    unit: StatUnit
    values: (number | null)[]
}

export interface FinancialSection {
    title: string
    rows: FinancialRow[]
}

export interface FinancialStatements {
    periods: string[]
    sections: FinancialSection[]
}

export interface Fundamentals {
    symbol: string
    name: string
    currency: string
    available: boolean
    keyStats: KeyStat[]
    perYear: FinancialStatements
    perQuarter: FinancialStatements
}

export interface Account {
    accountId: string
    currency: string | null
    accountType: string | null
    active: boolean
}

export interface AccountBalance {
    currency: string | null
    cashBalance: number | null
    totalValue: number | null
    nonMarginPositionsValue: number | null
    marginAvailable: number | null
    marginUsed: number | null
    openPositionsCount: number | null
}

export interface AccountOverview {
    accounts: Account[]
    balance: AccountBalance
}

export interface Position {
    netPositionId: string
    uic: number
    symbol: string
    description: string
    assetType: string
    currency: string | null
    amount: number | null
    openingDirection: string | null
    averageOpenPrice: number | null
    currentPrice: number | null
    marketValue: number | null
    profitLoss: number | null
    // P/L converted to the account base currency; summed for the account-level unrealised P/L.
    profitLossBase: number | null
    // Raw ratios (e.g. 0.1667 = +16.67%); the client localises them.
    profitLossPct: number | null
    dayChangePct: number | null
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
    exchange: string | null
    marketOpen: boolean | null
}

export type SignalDirection = 'BULLISH' | 'BEARISH' | 'NEUTRAL'

export interface Signal {
    indicator: string
    label: string
    direction: SignalDirection
    // Raw metric value(s); the client localises and formats them. One number for most indicators,
    // two for the SMA cross (fast / slow), empty when the indicator has no reading.
    value: number[]
    detail: string
}

export interface IndicatorSeries {
    name: string
    points: (number | null)[]
}

export interface Signals {
    symbol: string
    horizonMinutes: number
    available: boolean
    asOf: string | null
    netBias: SignalDirection
    signals: Signal[]
    points: PricePoint[]
    overlays: IndicatorSeries[]
    oscillators: IndicatorSeries[]
}
