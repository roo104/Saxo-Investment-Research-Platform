import { useLayoutEffect, useMemo, useRef, useState } from 'react'
import type { PricePoint } from '../types'

const H = 220
const PAD = { top: 14, right: 16, bottom: 22, left: 56 }

function fmtPrice(v: number): string {
  return v.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 5 })
}

function fmtDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

function fmtTime(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
}

interface Props {
  points: PricePoint[]
  currency: string | null
  /** When true, the last point is the live (streaming) candle: pulsing tip, guide line, LIVE tag. */
  live?: boolean
}

/**
 * A single-series price line + area chart. Per the dataviz method: one hue (the brand gold),
 * no legend (the panel title names the series), recessive axes, and a crosshair-and-tooltip hover.
 *
 * The [points] are OHLC candle closes. When [live], the final candle is streaming — Saxo re-sends
 * it as its close moves and appends new candles as the horizon rolls over — so the right edge tracks
 * the market in real time. Width is measured from the container so the SVG maps 1:1 to pixels.
 */
export function PriceChart({ points, currency, live }: Props) {
  const wrapRef = useRef<HTMLDivElement>(null)
  const svgRef = useRef<SVGSVGElement>(null)
  const [width, setWidth] = useState(560)
  const [hover, setHover] = useState<number | null>(null)

  useLayoutEffect(() => {
    const el = wrapRef.current
    if (!el) return
    const update = () => setWidth(el.clientWidth || 560)
    update()
    if (typeof ResizeObserver === 'undefined') return
    const ro = new ResizeObserver(update)
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  const { series, labels } = useMemo(() => {
    const series: number[] = []
    const times: string[] = []
    for (const p of points) {
      if (p.close != null) {
        series.push(p.close)
        times.push(p.time)
      }
    }
    // Label with times for intraday ranges (span under ~36h), dates otherwise.
    const spanMs = times.length > 1 ? Date.parse(times[times.length - 1]) - Date.parse(times[0]) : 0
    const format = spanMs > 0 && spanMs < 36 * 3600 * 1000 ? fmtTime : fmtDate
    return { series, labels: times.map(format) }
  }, [points])

  const geometry = useMemo(() => {
    if (series.length < 2) return null
    const W = width
    const min = Math.min(...series)
    const max = Math.max(...series)
    const span = max - min || max || 1
    const innerW = W - PAD.left - PAD.right
    const innerH = H - PAD.top - PAD.bottom
    const x = (i: number) => PAD.left + (i / (series.length - 1)) * innerW
    const y = (v: number) => PAD.top + innerH - ((v - min) / span) * innerH
    const coords = series.map((v, i) => [x(i), y(v)] as const)
    const line = coords.map(([cx, cy], i) => `${i === 0 ? 'M' : 'L'}${cx.toFixed(1)},${cy.toFixed(1)}`).join(' ')
    const base = H - PAD.bottom
    const area = `${line} L${coords[coords.length - 1][0].toFixed(1)},${base} L${coords[0][0].toFixed(1)},${base} Z`
    return { W, min, max, x, y, coords, line, area }
  }, [series, width])

  if (!geometry) {
    return (
      <div className="chart" ref={wrapRef}>
        <div className="empty">Not enough price history to chart.</div>
      </div>
    )
  }

  const first = series[0]
  const last = series[series.length - 1]
  const change = last - first
  const changePct = (change / first) * 100
  const up = change >= 0
  const isLiveTip = !!live
  const lastCoord = geometry.coords[geometry.coords.length - 1]
  const hoverCoord = hover != null ? geometry.coords[hover] : null

  function onMove(e: React.MouseEvent<SVGSVGElement>) {
    const rect = svgRef.current!.getBoundingClientRect()
    const ratio = (e.clientX - rect.left) / rect.width
    setHover(Math.max(0, Math.min(series.length - 1, Math.round(ratio * (series.length - 1)))))
  }

  return (
    <div className="chart" ref={wrapRef}>
      <div className="chart-head">
        <div className="chart-price tnum">
          {fmtPrice(last)}
          {currency ? <span className="chart-ccy"> {currency}</span> : null}
          {isLiveTip ? <span className="chart-live-tag">LIVE</span> : null}
        </div>
        <div className={`chart-change tnum ${up ? 'up' : 'down'}`}>
          {up ? '▲' : '▼'} {fmtPrice(Math.abs(change))} ({changePct.toFixed(2)}%)
        </div>
      </div>

      <svg
        ref={svgRef}
        className="chart-svg"
        width={geometry.W}
        height={H}
        viewBox={`0 0 ${geometry.W} ${H}`}
        role="img"
        aria-label={`Price chart, latest ${fmtPrice(last)} ${currency ?? ''}`}
        onMouseMove={onMove}
        onMouseLeave={() => setHover(null)}
      >
        <defs>
          <linearGradient id="priceFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--gold)" stopOpacity="0.28" />
            <stop offset="100%" stopColor="var(--gold)" stopOpacity="0" />
          </linearGradient>
        </defs>

        <line className="chart-grid" x1={PAD.left} y1={geometry.y(geometry.max)} x2={geometry.W - PAD.right} y2={geometry.y(geometry.max)} />
        <line className="chart-grid" x1={PAD.left} y1={geometry.y(geometry.min)} x2={geometry.W - PAD.right} y2={geometry.y(geometry.min)} />

        {isLiveTip && (
          <line className="chart-live-line" x1={PAD.left} y1={lastCoord[1]} x2={geometry.W - PAD.right} y2={lastCoord[1]} />
        )}

        <path d={geometry.area} fill="url(#priceFill)" />
        <path d={geometry.line} className="chart-line" />

        <circle cx={lastCoord[0]} cy={lastCoord[1]} r={isLiveTip ? 4.5 : 4} className={isLiveTip ? 'chart-dot-live' : 'chart-dot'} />

        {hoverCoord && (
          <>
            <line className="chart-crosshair" x1={hoverCoord[0]} y1={PAD.top} x2={hoverCoord[0]} y2={H - PAD.bottom} />
            <circle cx={hoverCoord[0]} cy={hoverCoord[1]} r="4" className="chart-dot-hover" />
          </>
        )}

        <text className="chart-axis" x={PAD.left - 8} y={geometry.y(geometry.max) + 4} textAnchor="end">{fmtPrice(geometry.max)}</text>
        <text className="chart-axis" x={PAD.left - 8} y={geometry.y(geometry.min) + 4} textAnchor="end">{fmtPrice(geometry.min)}</text>
        <text className="chart-axis" x={PAD.left} y={H - 6} textAnchor="start">{labels[0]}</text>
        <text className="chart-axis" x={geometry.W - PAD.right} y={H - 6} textAnchor="end">{labels[labels.length - 1]}</text>
      </svg>

      <div className="chart-tip tnum">
        {hover != null ? (
          <>
            <span>{labels[hover]}</span>
            <strong>{fmtPrice(series[hover])}</strong>
          </>
        ) : (
          <span className="chart-tip-hint">{isLiveTip ? 'streaming live candles · hover for values' : 'hover the chart for values'}</span>
        )}
      </div>
    </div>
  )
}
