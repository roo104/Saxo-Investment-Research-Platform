import {useCallback, useEffect, useRef, useState} from 'react'
import {api, ApiError} from './api'
import {EnvironmentBadge} from './components/EnvironmentBadge'
import {SearchPanel} from './components/SearchPanel'
import {Watchlist} from './components/Watchlist'
import type {EnvironmentInfo, Instrument, PriceTick, WatchlistEntry} from './types'

export default function App() {
    const [env, setEnv] = useState<EnvironmentInfo | null>(null)
    const [entries, setEntries] = useState<WatchlistEntry[]>([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    const [streaming, setStreaming] = useState(false)
    const loaded = useRef(false)

    const refresh = useCallback(async () => {
        try {
            setEntries(await api.getWatchlist())
            setError(null)
        } catch (err) {
            setError(err instanceof ApiError ? err.message : 'Could not load the watchlist')
        } finally {
            if (!loaded.current) {
                loaded.current = true
                setLoading(false)
            }
        }
    }, [])

    // Load once on mount; live prices arrive over SSE and list membership is refreshed
    // explicitly after adds/removes, so no periodic poll is needed.
    useEffect(() => {
        api.getEnvironment().then(setEnv).catch(() => setEnv(null))
        refresh()
    }, [refresh])

    // Open an SSE stream of live prices; reconnect whenever the set of instruments changes so newly
    // added items get subscribed. Price ticks are merged into the matching entry by uic + asset type.
    const uicKey = entries.map((e) => `${e.uic}:${e.assetType}`).sort().join(',')
    useEffect(() => {
        const es = new EventSource('/api/watchlist/stream')
        es.onopen = () => setStreaming(true)
        es.onerror = () => setStreaming(false)
        es.addEventListener('price', (ev) => {
            const t: PriceTick = JSON.parse((ev as MessageEvent).data)
            setEntries((prev) =>
                prev.map((e) =>
                    e.uic === t.uic && e.assetType === t.assetType
                        ? {
                            ...e,
                            bid: t.bid,
                            ask: t.ask,
                            mid: t.mid,
                            marketState: t.marketState,
                            priceAvailable: t.priceAvailable,
                            currency: e.currency ?? t.currency
                        }
                        : e,
                ),
            )
        })
        return () => {
            es.close()
            setStreaming(false)
        }
    }, [uicKey])

    const handleAdd = useCallback(
        async (instrument: Instrument) => {
            await api.addToWatchlist(instrument.uic, instrument.assetType)
            await refresh()
        },
        [refresh],
    )

    const handleRemove = useCallback(
        async (id: number) => {
            setEntries((prev) => prev.filter((e) => e.id !== id)) // optimistic
            try {
                await api.removeFromWatchlist(id)
            } finally {
                refresh()
            }
        },
        [refresh],
    )

    const isOnWatchlist = useCallback(
        (i: Instrument) => entries.some((e) => e.uic === i.uic && e.assetType === i.assetType),
        [entries],
    )

    return (
        <div className="app">
            <header className="topbar">
                <div className="brand">
                    <h1>
                        Saxo <em>Research</em>
                    </h1>
                    <span className="tagline">instrument intelligence desk</span>
                </div>
                <EnvironmentBadge env={env}/>
            </header>

            <main className="grid">
                <SearchPanel onAdd={handleAdd} isOnWatchlist={isOnWatchlist}/>
                <Watchlist entries={entries} loading={loading} error={error} streaming={streaming}
                           onRemove={handleRemove}/>
            </main>

            <p className="footer-note">
                {streaming ? 'Live prices streaming' : 'Reconnecting stream…'} · API docs at{' '}
                <a href="/swagger-ui.html">/swagger-ui.html</a>
            </p>
        </div>
    )
}
