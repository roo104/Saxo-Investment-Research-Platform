import {useEffect, useRef, useState} from 'react'
import type {WatchlistEntry} from '../types'

function fmt(value: number | null): string {
  if (value == null) return '—'
  return value.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 5})
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
                    <span className="sym">{entry.symbol}</span>
                    <span className="desc" title={entry.description}>{entry.description}</span>
                </span>
        </button>
        <div className="quote">
          {entry.priceAvailable ? (
              <div className="mid tnum">{fmt(entry.mid)}</div>
          ) : (
              <span className="na">no quote</span>
          )}
        </div>
        <button className="icon-btn" title="Remove" aria-label={`Remove ${entry.symbol}`}
                onClick={() => onRemove(entry.id)}>×
        </button>
      </div>
  )
}
