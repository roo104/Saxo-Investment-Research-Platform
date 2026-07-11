import {useEffect, useRef, useState} from 'react'
import type {PricePoint, WatchlistEntry} from '../types'
import {PriceChart, type ChartMode} from './PriceChart'
import {Fundamentals} from './Fundamentals'
import {Signals} from './Signals'
import {MarketBadge} from './MarketBadge'

const RANGES = [
    {key: '1m', horizon: 1, count: 120},
    {key: '1D', horizon: 5, count: 288},
    {key: '1W', horizon: 60, count: 168},
    {key: '1M', horizon: 1440, count: 30},
] as const

function fmt(value: number | null): string {
    if (value == null) return '—'
    return value.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 5})
}

/** The detail frame — shows a single selected instrument with its chart, fundamentals and signals. */
export function WatchDetail({entry, onRemove}: { entry: WatchlistEntry; onRemove: (id: number) => void }) {
    const [view, setView] = useState<'chart' | 'fundamentals' | 'signals'>('chart')
    const [range, setRange] = useState<(typeof RANGES)[number]['key']>('1D')
    const [mode, setMode] = useState<ChartMode>('candles')
    const [candles, setCandles] = useState<PricePoint[]>([])
    const [loading, setLoading] = useState(false)
    const [streaming, setStreaming] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const byTime = useRef<Map<string, PricePoint>>(new Map())

    // Live candle stream while on the chart view; reconnect when the range or instrument changes.
    useEffect(() => {
        if (view !== 'chart') return
        const r = RANGES.find((x) => x.key === range)!
        setLoading(true)
        setError(null)
        byTime.current = new Map()
        setCandles([])

        const commit = () => {
            const arr = [...byTime.current.values()].sort((a, b) => (a.time < b.time ? -1 : a.time > b.time ? 1 : 0))
            if (arr.length > 600) arr.splice(0, arr.length - 600)
            setCandles(arr)
        }

        const es = new EventSource(`/api/watchlist/${entry.id}/chart/stream?horizon=${r.horizon}&count=${r.count}`)
        es.addEventListener('snapshot', (e) => {
            const u = JSON.parse((e as MessageEvent).data) as { points: PricePoint[] }
            byTime.current = new Map(u.points.map((p) => [p.time, p]))
            setLoading(false)
            setStreaming(true)
            commit()
        })
        es.addEventListener('update', (e) => {
            const u = JSON.parse((e as MessageEvent).data) as { points: PricePoint[] }
            for (const p of u.points) byTime.current.set(p.time, p)
            commit()
        })
        es.onerror = () => setStreaming(false)

        return () => {
            es.close()
            setStreaming(false)
        }
    }, [view, range, entry.id])

    return (
        <section className="panel detail">
            <div className="panel-head">
                <div className="detail-title">
                    <span className="sym">{entry.symbol}</span>
                    <span className="desc" title={entry.description}>{entry.description}</span>
                </div>
                <div className="detail-head-right">
                    <MarketBadge entry={entry}/>
                    {entry.priceAvailable ? (
                        <div className="quote">
                            <div className="mid tnum">
                                {fmt(entry.mid)}
                                {entry.currency ?
                                    <span style={{
                                        color: 'var(--text-faint)',
                                        fontSize: 11
                                    }}> {entry.currency}</span> : null}
                            </div>
                            <div className="spread">{fmt(entry.bid)} / {fmt(entry.ask)}</div>
                        </div>
                    ) : (
                        <span className="na"
                              title="Saxo isn’t returning a live price — usually a market-data entitlement your account doesn’t have (common for equities on simulation).">
                            Live price unavailable
                        </span>
                    )}
                    <button className="icon-btn" title="Remove" aria-label={`Remove ${entry.symbol}`}
                            onClick={() => onRemove(entry.id)}>×
                    </button>
                </div>
            </div>

            <div className="chart-region">
                <div className="view-tabs">
                    <button className={`view-tab${view === 'chart' ? ' active' : ''}`}
                            onClick={() => setView('chart')}>Chart
                    </button>
                    <button className={`view-tab${view === 'fundamentals' ? ' active' : ''}`}
                            onClick={() => setView('fundamentals')}>Fundamentals
                    </button>
                    <button className={`view-tab${view === 'signals' ? ' active' : ''}`}
                            onClick={() => setView('signals')}>Signals
                    </button>
                </div>

                {view === 'chart' ? (
                    <>
                        <div className="chart-controls">
                            <div className="range-tabs">
                                {RANGES.map((r) => (
                                    <button key={r.key} className={`range-tab${range === r.key ? ' active' : ''}`}
                                            onClick={() => setRange(r.key)}>
                                        {r.key}
                                    </button>
                                ))}
                            </div>
                            <div className="mode-toggle">
                                <button className={`range-tab${mode === 'line' ? ' active' : ''}`}
                                        onClick={() => setMode('line')}>Line
                                </button>
                                <button className={`range-tab${mode === 'candles' ? ' active' : ''}`}
                                        onClick={() => setMode('candles')}>Candles
                                </button>
                            </div>
                        </div>
                        {loading && candles.length === 0 &&
                            <div className="empty"><span className="spinner"/>streaming candles…</div>}
                        {error && <div className="banner">{error}</div>}
                        {candles.length > 0 &&
                            <PriceChart points={candles} currency={entry.currency} mode={mode} live={streaming}/>}
                    </>
                ) : view === 'fundamentals' ? (
                    <Fundamentals id={entry.id}/>
                ) : (
                    <Signals id={entry.id} currency={entry.currency}/>
                )}
            </div>
        </section>
    )
}
