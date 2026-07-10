import type {EnvironmentInfo, Instrument, PriceHistory, WatchlistEntry} from './types'

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

    getWatchlist: () => request<WatchlistEntry[]>('/watchlist'),

    addToWatchlist: (uic: number, assetType: string) =>
        request<WatchlistEntry>('/watchlist', {
            method: 'POST',
            body: JSON.stringify({uic, assetType}),
        }),

    removeFromWatchlist: (id: number) =>
        request<void>(`/watchlist/${id}`, {method: 'DELETE'}),

    getHistory: (id: number, params: { horizon: number; count: number }) => {
        const query = new URLSearchParams({horizon: String(params.horizon), count: String(params.count)})
        return request<PriceHistory>(`/watchlist/${id}/history?${query.toString()}`)
    },
}
