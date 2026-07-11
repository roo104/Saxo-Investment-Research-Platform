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
    return new Date(iso).toLocaleTimeString(undefined, {hour: '2-digit', minute: '2-digit', hour12: false})
}

export type ChartMode = 'line' | 'candles'

export interface ChartOverlay {
    name: string
    color: string
    /** One value per price point (index-aligned); null marks an indicator warm-up gap. */
    values: (number | null)[]
}

interface Props {
  points: PricePoint[]
  currency: string | null
    mode?: ChartMode
    /** When true, the last candle is the live (streaming) one: pulsing tip, guide line, LIVE tag. */
  live?: boolean
    /** Indicator lines drawn over the price series (e.g. moving averages, Bollinger bands). */
    overlays?: ChartOverlay[]
}

interface Candle {
    o: number
    h: number
    l: number
    c: number
    label: string
}

/**
 * A single-series price chart with two views: a line + area of closes, or OHLC candlesticks
 * (green up / red down). One hue for the line (Saxo blue), semantic up/down for candles; recessive
 * axes; crosshair-and-tooltip hover. When [live], the final candle streams and the right edge tracks
 * the market. Width is measured from the container so the SVG maps 1:1 to pixels.
 */
export function PriceChart({points, currency, mode = 'line', live, overlays}: Props) {
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

    const candlesData = useMemo<Candle[]>(() => {
    const times: string[] = []
        const rows: Omit<Candle, 'label'>[] = []
    for (const p of points) {
        if (p.close == null) continue
        const c = p.close
        rows.push({o: p.open ?? c, h: p.high ?? c, l: p.low ?? c, c})
        times.push(p.time)
    }
    const spanMs = times.length > 1 ? Date.parse(times[times.length - 1]) - Date.parse(times[0]) : 0
        const fmt = spanMs > 0 && spanMs < 36 * 3600 * 1000 ? fmtTime : fmtDate
        return rows.map((r, i) => ({...r, label: fmt(times[i])}))
  }, [points])

  const geometry = useMemo(() => {
      if (candlesData.length < 2) return null
    const W = width
      const lows = mode === 'candles' ? candlesData.map((d) => d.l) : candlesData.map((d) => d.c)
      const highs = mode === 'candles' ? candlesData.map((d) => d.h) : candlesData.map((d) => d.c)
      // Fold overlay values (which can exceed the close range, e.g. Bollinger bands) into the scale.
      const extra = (overlays ?? [])
          .flatMap((o) => o.values.slice(0, candlesData.length))
          .filter((v): v is number => v != null && Number.isFinite(v))
      const min = Math.min(...lows, ...extra)
      const max = Math.max(...highs, ...extra)
    const span = max - min || max || 1
    const innerW = W - PAD.left - PAD.right
    const innerH = H - PAD.top - PAD.bottom
      const x = (i: number) => PAD.left + (i / (candlesData.length - 1)) * innerW
    const y = (v: number) => PAD.top + innerH - ((v - min) / span) * innerH
      const bodyW = Math.max(1, Math.min(14, (innerW / candlesData.length) * 0.62))
      return {W, min, max, x, y, innerW, bodyW}
  }, [candlesData, width, mode, overlays])

  if (!geometry) {
    return (
      <div className="chart" ref={wrapRef}>
        <div className="empty">Not enough price history to chart.</div>
      </div>
    )
  }

    const closes = candlesData.map((d) => d.c)
    const first = closes[0]
    const last = closes[closes.length - 1]
  const change = last - first
  const changePct = (change / first) * 100
  const up = change >= 0
  const isLiveTip = !!live
    const lastCoord = [geometry.x(candlesData.length - 1), geometry.y(last)] as const
    const hoverIdx = hover
    const hoverCoord = hoverIdx != null ? ([geometry.x(hoverIdx), geometry.y(closes[hoverIdx])] as const) : null

    const linePath = closes
        .map((v, i) => `${i === 0 ? 'M' : 'L'}${geometry.x(i).toFixed(1)},${geometry.y(v).toFixed(1)}`)
        .join(' ')
    const base = H - PAD.bottom
    const areaPath = `${linePath} L${geometry.x(closes.length - 1).toFixed(1)},${base} L${geometry.x(0).toFixed(1)},${base} Z`

  function onMove(e: React.MouseEvent<SVGSVGElement>) {
    const rect = svgRef.current!.getBoundingClientRect()
    const ratio = (e.clientX - rect.left) / rect.width
      setHover(Math.max(0, Math.min(candlesData.length - 1, Math.round(ratio * (candlesData.length - 1)))))
  }

    // Evenly spaced axis ticks: ~4 price levels on Y, ~6 time points on X.
    const yTickN = 4
    const yTicks = Array.from({length: yTickN + 1}, (_, i) => geometry.min + (i / yTickN) * (geometry.max - geometry.min))
    const xTickN = Math.min(6, candlesData.length)
    const xTicks = Array.from({length: xTickN}, (_, i) => Math.round((i * (candlesData.length - 1)) / (xTickN - 1)))

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
        aria-label={`Price chart (${mode}), latest ${fmtPrice(last)} ${currency ?? ''}`}
        onMouseMove={onMove}
        onMouseLeave={() => setHover(null)}
      >
        <defs>
          <linearGradient id="priceFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--blue)" stopOpacity="0.28"/>
              <stop offset="100%" stopColor="var(--blue)" stopOpacity="0"/>
          </linearGradient>
        </defs>

          {yTicks.map((v, i) => (
              <line key={`yg${i}`} className="chart-grid" x1={PAD.left} y1={geometry.y(v)} x2={geometry.W - PAD.right}
                    y2={geometry.y(v)}/>
          ))}
          {xTicks.map((idx, i) => (
              <line key={`xg${i}`} className="chart-grid chart-grid-v" x1={geometry.x(idx)} y1={PAD.top}
                    x2={geometry.x(idx)} y2={H - PAD.bottom}/>
          ))}

        {isLiveTip && (
          <line className="chart-live-line" x1={PAD.left} y1={lastCoord[1]} x2={geometry.W - PAD.right} y2={lastCoord[1]} />
        )}

          {mode === 'line' ? (
              <>
                  <path d={areaPath} fill="url(#priceFill)"/>
                  <path d={linePath} className="chart-line"/>
              </>
          ) : (
              candlesData.map((d, i) => {
                  const cx = geometry.x(i)
                  const cls = d.c >= d.o ? 'candle-up' : 'candle-down'
                  const bodyTop = geometry.y(Math.max(d.o, d.c))
                  const bodyH = Math.max(1, Math.abs(geometry.y(d.c) - geometry.y(d.o)))
                  const liveCls = isLiveTip && i === candlesData.length - 1 ? ' candle-live' : ''
                  return (
                      <g key={i} className={`${cls}${liveCls}`}>
                          <line className="candle-wick" x1={cx} y1={geometry.y(d.h)} x2={cx} y2={geometry.y(d.l)}/>
                          <rect className="candle-body" x={cx - geometry.bodyW / 2} y={bodyTop} width={geometry.bodyW}
                                height={bodyH}/>
                      </g>
                  )
              })
          )}

          {(overlays ?? []).map((o) => {
              let d = ''
              let pen = false
              for (let i = 0; i < candlesData.length; i++) {
                  const v = o.values[i]
                  if (v == null || !Number.isFinite(v)) {
                      pen = false
                      continue
                  }
                  d += `${pen ? 'L' : 'M'}${geometry.x(i).toFixed(1)},${geometry.y(v).toFixed(1)} `
                  pen = true
              }
              return d ? <path key={o.name} className="chart-overlay" d={d.trim()} style={{stroke: o.color}}/> : null
          })}

          {(mode === 'line' || isLiveTip) && (
              <circle cx={lastCoord[0]} cy={lastCoord[1]} r={isLiveTip ? 4.5 : 4}
                      className={isLiveTip ? 'chart-dot-live' : 'chart-dot'}/>
          )}

        {hoverCoord && (
          <>
            <line className="chart-crosshair" x1={hoverCoord[0]} y1={PAD.top} x2={hoverCoord[0]} y2={H - PAD.bottom} />
            <circle cx={hoverCoord[0]} cy={hoverCoord[1]} r="4" className="chart-dot-hover" />
          </>
        )}

          {yTicks.map((v, i) => (
              <text key={`yl${i}`} className="chart-axis" x={PAD.left - 8} y={geometry.y(v) + 4}
                    textAnchor="end">{fmtPrice(v)}</text>
          ))}
          {xTicks.map((idx, i) => {
              const anchor = i === 0 ? 'start' : i === xTicks.length - 1 ? 'end' : 'middle'
              return (
                  <text key={`xl${i}`} className="chart-axis" x={geometry.x(idx)} y={H - 6} textAnchor={anchor}>
                      {candlesData[idx].label}
                  </text>
              )
          })}
      </svg>

      <div className="chart-tip tnum">
          {hoverIdx != null ? (
              mode === 'candles' ? (
                  <span className="ohlc">
              <span>{candlesData[hoverIdx].label}</span>
              <span>O {fmtPrice(candlesData[hoverIdx].o)}</span>
              <span>H {fmtPrice(candlesData[hoverIdx].h)}</span>
              <span>L {fmtPrice(candlesData[hoverIdx].l)}</span>
              <strong>C {fmtPrice(candlesData[hoverIdx].c)}</strong>
            </span>
              ) : (
                  <>
                      <span>{candlesData[hoverIdx].label}</span>
                      <strong>{fmtPrice(closes[hoverIdx])}</strong>
                  </>
              )
        ) : (
              <span
                  className="chart-tip-hint">{isLiveTip ? 'streaming live · hover for values' : 'hover the chart for values'}</span>
        )}
      </div>
    </div>
  )
}
