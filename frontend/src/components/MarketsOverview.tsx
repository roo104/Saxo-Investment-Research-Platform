import {useCallback, useEffect, useMemo, useState} from 'react'
import {api} from '../api'
import type {IndexSeries} from '../types'
import {fmtPct, MarketsChart, prepareSeries, REGION_ORDER} from './MarketsChart'

const WINDOW_MS = 24 * 3600 * 1000
// Poll while a market is open so open indices track live; chart history, not streaming, so ~60s.
const REFRESH_MS = 60_000

/**
 * The "World markets" panel: the last 24h of the headline indices as rebased % lines on one shared
 * clock, grouped by region, with a live-refreshing legend. Refreshes every ~60s while any market is
 * open (indices have no live quote stream in simulation, so we re-poll chart history instead).
 */
export function MarketsOverview() {
    const [series, setSeries] = useState<IndexSeries[]>([])
    const [now, setNow] = useState(() => Date.now())
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    // Markets toggled off in the legend, and the one currently hovered (emphasised in the chart).
    const [hidden, setHidden] = useState<Set<string>>(new Set())
    const [hoverKey, setHoverKey] = useState<string | null>(null)

    const toggle = useCallback((key: string) => {
        setHidden((prev) => {
            const next = new Set(prev)
            next.has(key) ? next.delete(key) : next.add(key)
            return next
        })
    }, [])

    const load = useCallback(async () => {
        try {
            setSeries(await api.getIndices())
            setNow(Date.now())
            setError(null)
        } catch {
            setError('Could not load market indices')
        } finally {
            setLoading(false)
        }
    }, [])

    useEffect(() => {
        load()
    }, [load])

    const anyOpen = series.some((s) => s.marketOpen)
    useEffect(() => {
        if (!anyOpen) return
        const id = setInterval(load, REFRESH_MS)
        return () => clearInterval(id)
    }, [anyOpen, load])

    const startMs = now - WINDOW_MS
    const prepared = useMemo(() => prepareSeries(series, startMs, now), [series, startMs, now])

    const groups = REGION_ORDER.map((region) => ({
        region,
        items: prepared.filter((p) => p.region === region),
    })).filter((g) => g.items.length > 0)

    return (
        <section className="panel markets">
            <div className="panel-head">
                <h2>World markets</h2>
                <span className="count">
                    last 24h · % change
                    {anyOpen && <span className="live-dot"/>}
                </span>
            </div>

            <div className="markets-body">
                {prepared.length === 0 ? (
                    <div className="empty">
                        {loading ? <><span className="spinner"/>Loading market
                            indices…</> : error ?? 'No index data available right now.'}
                    </div>
                ) : (
                    <>
                        <MarketsChart series={prepared} startMs={startMs} endMs={now} hiddenKeys={hidden}
                                      highlightKey={hoverKey}/>
                        <div className="markets-legend">
                            {groups.map((g) => (
                                <div className="markets-legend-group" key={g.region}>
                                    <h3>{g.region}</h3>
                                    <ul>
                                        {g.items.map((s) => {
                                            const off = hidden.has(s.key)
                                            return (
                                                <li
                                                    key={s.key}
                                                    className={`markets-legend-item ${off ? 'is-off' : ''}`}
                                                    role="button"
                                                    tabIndex={0}
                                                    aria-pressed={!off}
                                                    title={`${s.name} — click to ${off ? 'show' : 'hide'}`}
                                                    onClick={() => toggle(s.key)}
                                                    onKeyDown={(e) => {
                                                        if (e.key === 'Enter' || e.key === ' ') {
                                                            e.preventDefault()
                                                            toggle(s.key)
                                                        }
                                                    }}
                                                    onMouseEnter={() => setHoverKey(s.key)}
                                                    onMouseLeave={() => setHoverKey((k) => (k === s.key ? null : k))}
                                                    onFocus={() => setHoverKey(s.key)}
                                                    onBlur={() => setHoverKey((k) => (k === s.key ? null : k))}
                                                >
                                                    <span className="markets-swatch" style={{background: s.color}}/>
                                                    <span className="markets-name">{s.name}</span>
                                                    <span
                                                        className={`markets-pct tnum ${(s.last ?? 0) >= 0 ? 'up' : 'down'}`}>
                                                        {s.last != null ? fmtPct(s.last) : '—'}
                                                    </span>
                                                    <span className={`mkt-badge ${s.marketOpen ? 'is-open' : ''}`}>
                                                        <span className="mkt-dot"/>
                                                        {s.marketOpen ? 'Open' : 'Closed'}
                                                    </span>
                                                </li>
                                            )
                                        })}
                                    </ul>
                                </div>
                            ))}
                        </div>
                    </>
                )}
            </div>
        </section>
    )
}
