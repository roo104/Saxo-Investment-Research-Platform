import {useEffect, useRef, useState} from 'react'
import type {WatchlistEntry} from '../types'
import {fmtDecimal} from '../format'
import {MarketBadge} from './MarketBadge'

function fmt(value: number | null): string {
  if (value == null) return '—'
  return fmtDecimal(value)
}

interface Props {
  entry: WatchlistEntry
  selected: boolean
  onSelect: (id: number) => void
  onRemove: (id: number) => void
}

/** A compact row in the master list — selecting it shows the instrument in the detail frame. */
export function WatchRow({entry, selected, onSelect, onRemove}: Props) {
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

  return (
      <div className={`row watch-row${selected ? ' is-selected' : ''}${flash ? ` flash-${flash}` : ''}`}>
        <button className="watch-toggle" onClick={() => onSelect(entry.id)} aria-pressed={selected}
                aria-label={`Show ${entry.symbol}`}>
                <span>
                    <span className="sym">{entry.description}</span>
                    <span className="desc" title={entry.symbol}>{entry.symbol}</span>
                </span>
        </button>
        <div className="quote">
          {entry.priceAvailable ? (
              <div className="mid tnum">{fmt(entry.mid)}</div>
          ) : entry.lastClose != null ? (
              <div className="mid tnum" title="Last close — no live price on your account">
                {fmt(entry.lastClose)}
                <span className="close-tag">close</span>
              </div>
          ) : (
              <span className="na" title="No price available for this instrument on your account">
                No price
              </span>
          )}
          <MarketBadge entry={entry}/>
        </div>
        <button className="icon-btn" title="Remove" aria-label={`Remove ${entry.symbol}`}
                onClick={() => onRemove(entry.id)}>×
        </button>
      </div>
  )
}
