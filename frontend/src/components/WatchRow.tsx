import { useEffect, useRef, useState } from 'react'
import type { PricePoint, WatchlistEntry } from '../types'
import { PriceChart } from './PriceChart'

const RANGES = [
  { key: '1m', horizon: 1, count: 120 },
  { key: '1D', horizon: 5, count: 288 },
  { key: '1W', horizon: 60, count: 168 },
  { key: '1M', horizon: 1440, count: 30 },
] as const

function fmt(value: number | null): string {
  if (value == null) return '—'
  return value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 5 })
}

export function WatchRow({ entry, onRemove }: { entry: WatchlistEntry; onRemove: (id: number) => void }) {
  const [expanded, setExpanded] = useState(false)
  const [range, setRange] = useState<(typeof RANGES)[number]['key']>('1m')
  const [candles, setCandles] = useState<PricePoint[]>([])
  const [loading, setLoading] = useState(false)
  const [streaming, setStreaming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const byTime = useRef<Map<string, PricePoint>>(new Map())

  // Flash the row price green/red when the mid changes (driven by the price SSE stream).
  const prevMid = useRef<number | null>(entry.mid)
  const [flash, setFlash] = useState<'up' | 'down' | null>(null)
  useEffect(() => {
    const prev = prevMid.current
    if (entry.mid != null && prev != null && prev !== entry.mid) {
      setFlash(entry.mid > prev ? 'up' : 'down')
      const t = setTimeout(() => setFlash(null), 800)
      prevMid.current = entry.mid
      return () => clearTimeout(t)
    }
    prevMid.current = entry.mid
  }, [entry.mid])

  // Open a live candle stream while expanded; reconnect when the range changes.
  useEffect(() => {
    if (!expanded) return
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
  }, [expanded, range, entry.id])

  return (
    <div className={`watch-item${expanded ? ' is-open' : ''}`}>
      <div className={`row watch-row${flash ? ` flash-${flash}` : ''}`}>
        <button className="watch-toggle" onClick={() => setExpanded((v) => !v)} aria-expanded={expanded} aria-label={`Toggle chart for ${entry.symbol}`}>
          <span className={`chevron${expanded ? ' open' : ''}`}>›</span>
          <span>
            <span className="sym">{entry.symbol}</span>
            <span className="desc" title={entry.description}>
              {entry.description}
            </span>
          </span>
        </button>
        <div className="quote">
          {entry.priceAvailable ? (
            <>
              <div className="mid tnum">
                {fmt(entry.mid)}
                {entry.currency ? <span style={{ color: 'var(--text-faint)', fontSize: 11 }}> {entry.currency}</span> : null}
              </div>
              <div className="spread">
                {fmt(entry.bid)} / {fmt(entry.ask)}
              </div>
              {entry.marketState && <span className="state">{entry.marketState}</span>}
            </>
          ) : (
            <span className="na">no quote</span>
          )}
        </div>
        <button className="icon-btn" title="Remove" aria-label={`Remove ${entry.symbol}`} onClick={() => onRemove(entry.id)}>
          ×
        </button>
      </div>

      {expanded && (
        <div className="chart-region">
          <div className="range-tabs">
            {RANGES.map((r) => (
              <button key={r.key} className={`range-tab${range === r.key ? ' active' : ''}`} onClick={() => setRange(r.key)}>
                {r.key}
              </button>
            ))}
          </div>
          {loading && candles.length === 0 && <div className="empty"><span className="spinner" />streaming candles…</div>}
          {error && <div className="banner">{error}</div>}
          {candles.length > 0 && <PriceChart points={candles} currency={entry.currency} live={streaming} />}
        </div>
      )}
    </div>
  )
}
