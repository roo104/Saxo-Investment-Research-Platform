import {useCallback, useEffect, useRef, useState} from 'react'
import {api, ApiError} from './api'
import {EnvironmentBadge} from './components/EnvironmentBadge'
import {SearchPanel} from './components/SearchPanel'
import {AllocationOverview} from './components/AllocationOverview'
import {Portfolio} from './components/Portfolio'
import type {EnvironmentInfo, Instrument, PriceTick, PortfolioEntry} from './types'

export default function App() {
    const [env, setEnv] = useState<EnvironmentInfo | null>(null)
    const [entries, setEntries] = useState<PortfolioEntry[]>([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    const [streaming, setStreaming] = useState(false)
    const loaded = useRef(false)

    const refresh = useCallback(async () => {
        try {
            setEntries(await api.getPortfolio())
            setError(null)
        } catch (err) {
            setError(err instanceof ApiError ? err.message : 'Could not load the portfolio')
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
        const es = new EventSource('/api/portfolio/stream')
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
                            marketOpen: t.marketOpen,
                            exchange: e.exchange ?? t.exchange,
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
        async (instrument: Instrument, quantity: number, openingPrice: number) => {
            await api.addToPortfolio(instrument.uic, instrument.assetType, quantity, openingPrice)
            await refresh()
        },
        [refresh],
    )

    const handleRemove = useCallback(
        async (id: number) => {
            setEntries((prev) => prev.filter((e) => e.id !== id)) // optimistic
            try {
                await api.removeFromPortfolio(id)
            } finally {
                refresh()
            }
        },
        [refresh],
    )

    const isOnPortfolio = useCallback(
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

            <main className="stack">
                <SearchPanel onAdd={handleAdd} isOnPortfolio={isOnPortfolio}/>
                {entries.length > 0 && <AllocationOverview entries={entries}/>}
                <Portfolio entries={entries} loading={loading} error={error} streaming={streaming}
                           onRemove={handleRemove}/>
            </main>

            <p className="footer-note">
                {streaming ? 'Live prices streaming' : 'Reconnecting stream…'} · API docs at{' '}
                <a href="/swagger-ui.html">/swagger-ui.html</a>
            </p>
        </div>
    )
}
