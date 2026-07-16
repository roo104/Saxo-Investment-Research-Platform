import type {
    AccountOverview,
    ClosedPosition,
    EnvironmentInfo,
    Fundamentals,
    IndexSeries,
    Instrument,
    Performance,
    PerformancePeriod,
    Position,
    PriceHistory,
    Signals,
    PortfolioEntry
} from './types'

const BASE = '/api'

/** Thrown for any non-2xx response, carrying the RFC-7807 detail the backend returned. */
export class ApiError extends Error {
    constructor(
        public status: number,
        message: string,
    ) {
        super(message)
        this.name = 'ApiError'
    }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const res = await fetch(`${BASE}${path}`, {
        headers: {'Content-Type': 'application/json', ...(init?.headers ?? {})},
        ...init,
    })
    if (!res.ok) {
        // The backend speaks application/problem+json; surface its detail/title rather than a generic error.
        let message = res.statusText
        try {
            const problem = await res.json()
            message = problem.detail ?? problem.title ?? message
        } catch {
            /* body was not JSON */
        }
        throw new ApiError(res.status, message)
    }
    if (res.status === 204) return undefined as T
    return (await res.json()) as T
}

export const api = {
    getEnvironment: () => request<EnvironmentInfo>('/environment'),

    searchInstruments: (params: { keywords?: string; assetTypes?: string; exchangeId?: string }) => {
        const query = new URLSearchParams()
        if (params.keywords) query.set('keywords', params.keywords)
        if (params.assetTypes) query.set('assetTypes', params.assetTypes)
        if (params.exchangeId) query.set('exchangeId', params.exchangeId)
        return request<Instrument[]>(`/instruments?${query.toString()}`)
    },

    getPortfolio: () => request<PortfolioEntry[]>('/portfolio'),

    getIndices: () => request<IndexSeries[]>('/indices'),

    getAccount: () => request<AccountOverview>('/account'),

    getPositions: () => request<Position[]>('/account/positions'),

    getClosedPositions: () => request<ClosedPosition[]>('/account/closed-positions'),

    getPerformance: (period: PerformancePeriod) =>
        request<Performance>(`/account/performance?period=${period}`),

    addToPortfolio: (uic: number, assetType: string, quantity: number, openingPrice: number) =>
        request<PortfolioEntry>('/portfolio', {
            method: 'POST',
            body: JSON.stringify({uic, assetType, quantity, openingPrice}),
        }),

    removeFromPortfolio: (id: number) =>
        request<void>(`/portfolio/${id}`, {method: 'DELETE'}),

    getHistory: (id: number, params: { horizon: number; count: number }) => {
        const query = new URLSearchParams({horizon: String(params.horizon), count: String(params.count)})
        return request<PriceHistory>(`/portfolio/${id}/history?${query.toString()}`)
    },

    getFundamentals: (id: number) => request<Fundamentals>(`/portfolio/${id}/fundamentals`),

    getSignals: (id: number, params: { horizon: number; count: number }) => {
        const query = new URLSearchParams({horizon: String(params.horizon), count: String(params.count)})
        return request<Signals>(`/portfolio/${id}/signals?${query.toString()}`)
    },
}
