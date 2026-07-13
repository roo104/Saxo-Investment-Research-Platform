import {useLayoutEffect, useMemo, useRef, useState} from 'react'
import type {IndexSeries, MarketRegion} from '../types'
import {fmtDecimal, fmtTime} from '../format'

const H = 260
const PAD = {top: 16, right: 16, bottom: 24, left: 52}

// Region hue families — grouped so the legend reads by region, distinct enough to tell apart on the
// near-black canvas. Applied per series via inline stroke (like the price-chart overlays).
export const REGION_ORDER: MarketRegion[] = ['Americas', 'Europe', 'Asia']
const REGION_COLORS: Record<MarketRegion, string[]> = {
    Americas: ['#33aae0', '#0091d5', '#3f6bd6'],
    Europe: ['#8a6fe0', '#6f4fd0', '#c08fe8', '#d98fd0'],
    Asia: ['#58d18b', '#2bb6a6', '#a3d977'],
}

// A point within one candle of the cursor counts as "present" for the hover tooltip.
const HOVER_TOLERANCE_MS = 16 * 60 * 1000

interface RebasedPoint {
    tMs: number
    pct: number
}

export interface PreparedSeries {
    key: string
    name: string
    region: MarketRegion
    color: string
    marketOpen: boolean
    /** Rebased to % change from the first visible point; empty if it has no data in the window. */
    points: RebasedPoint[]
    /** Latest rebased % (the series' 24h change), or null when it has no data. */
    last: number | null
}

/**
 * Rebase each index to % change from its first visible point and colour it by region, so indices at
 * very different levels share one scale. Series are ordered Americas → Europe → Asia and coloured
 * within their region; the panel reuses this for the legend so swatches and % match the chart.
 */
export function prepareSeries(series: IndexSeries[], startMs: number, endMs: number): PreparedSeries[] {
    const ordered = [...series].sort((a, b) => REGION_ORDER.indexOf(a.region) - REGION_ORDER.indexOf(b.region))
    const counters: Record<MarketRegion, number> = {Americas: 0, Europe: 0, Asia: 0}
    return ordered.map((s) => {
        const palette = REGION_COLORS[s.region]
        const color = palette[counters[s.region]++ % palette.length]
        const visible = s.points
            .map((p) => ({tMs: Date.parse(p.time), close: p.close}))
            .filter((p) => p.close != null && p.tMs >= startMs && p.tMs <= endMs)
            .sort((a, b) => a.tMs - b.tMs)
        const base = visible[0]?.close ?? null
        const points: RebasedPoint[] = base != null && base !== 0
            ? visible.map((p) => ({tMs: p.tMs, pct: (p.close! / base - 1) * 100}))
            : []
        return {
            key: s.key,
            name: s.name,
            region: s.region,
            color,
            marketOpen: s.marketOpen,
            points,
            last: points.length ? points[points.length - 1].pct : null,
        }
    })
}

interface Props {
    series: PreparedSeries[]
    /** Window bounds in epoch ms; x spans exactly [startMs, endMs] (the last 24h). */
    startMs: number
    endMs: number
    /** Keys toggled off in the legend; those lines are hidden and excluded from the scale. */
    hiddenKeys?: Set<string>
    /** Key hovered in the legend: that line is emphasised and the rest dimmed. */
    highlightKey?: string | null
}

/** Signed one-decimal percentage, e.g. 1.23 -> "+1.2%". Shared by the chart axis and legend. */
export function fmtPct(v: number): string {
    return `${v >= 0 ? '+' : ''}${fmtDecimal(v, 1)}%`
}

/**
 * The combined 24h markets chart: every index a coloured % line on one shared wall-clock axis.
 * Because regions trade at different hours, their lines occupy different parts of the axis (the
 * Asia → Europe → US cascade). Open markets get a pulsing live tip; hovering shows each index's
 * value at that time. Width is measured from the container so the SVG maps 1:1 to pixels.
 */
export function MarketsChart({series, startMs, endMs, hiddenKeys, highlightKey}: Props) {
    const wrapRef = useRef<HTMLDivElement>(null)
    const svgRef = useRef<SVGSVGElement>(null)
    const [width, setWidth] = useState(720)
    const [hoverX, setHoverX] = useState<number | null>(null)

    useLayoutEffect(() => {
        const el = wrapRef.current
        if (!el) return
        const update = () => setWidth(el.clientWidth || 720)
        update()
        if (typeof ResizeObserver === 'undefined') return
        const ro = new ResizeObserver(update)
        ro.observe(el)
        return () => ro.disconnect()
    }, [])

    // Only toggled-on series are drawn and folded into the scale, so hiding a market lets the rest
    // use the full vertical space.
    const visible = useMemo(() => series.filter((s) => !hiddenKeys?.has(s.key)), [series, hiddenKeys])

    const geometry = useMemo(() => {
        const span = endMs - startMs || 1
        const pcts = visible.flatMap((s) => s.points.map((p) => p.pct))
        // Always fold 0 into the range so the zero baseline is on-screen; pad so lines don't touch edges.
        const rawMin = Math.min(0, ...pcts)
        const rawMax = Math.max(0, ...pcts)
        const pad = (rawMax - rawMin) * 0.12 || 1
        const min = rawMin - pad
        const max = rawMax + pad
        const innerW = width - PAD.left - PAD.right
        const innerH = H - PAD.top - PAD.bottom
        const x = (tMs: number) => PAD.left + ((tMs - startMs) / span) * innerW
        const y = (v: number) => PAD.top + innerH - ((v - min) / (max - min)) * innerH
        return {W: width, min, max, x, y, innerW}
    }, [visible, width, startMs, endMs])

    const hasData = visible.some((s) => s.points.length >= 2)
    if (!hasData) {
        return (
            <div className="markets-chart" ref={wrapRef}>
                <div
                    className="empty">{series.length ? 'All markets hidden — re-enable one in the legend.' : 'No index data available right now.'}</div>
            </div>
        )
    }

    const {x, y, min, max, W} = geometry

    // ~5 clock ticks across the window, ~4 % levels on Y.
    const xTickN = 5
    const xTicks = Array.from({length: xTickN}, (_, i) => startMs + (i / (xTickN - 1)) * (endMs - startMs))
    const yTickN = 4
    const yTicks = Array.from({length: yTickN + 1}, (_, i) => min + (i / yTickN) * (max - min))

    const hoverTMs = hoverX != null ? startMs + ((hoverX - PAD.left) / geometry.innerW) * (endMs - startMs) : null
    const hoverRows =
        hoverTMs != null
            ? visible
                .map((s) => {
                    let nearest: RebasedPoint | null = null
                    for (const p of s.points) {
                        if (nearest == null || Math.abs(p.tMs - hoverTMs) < Math.abs(nearest.tMs - hoverTMs)) nearest = p
                    }
                    return nearest && Math.abs(nearest.tMs - hoverTMs) <= HOVER_TOLERANCE_MS
                        ? {key: s.key, name: s.name, color: s.color, pct: nearest.pct}
                        : null
                })
                .filter((r): r is NonNullable<typeof r> => r != null)
            : []

    function onMove(e: React.MouseEvent<SVGSVGElement>) {
        const rect = svgRef.current!.getBoundingClientRect()
        const px = ((e.clientX - rect.left) / rect.width) * W
        setHoverX(Math.max(PAD.left, Math.min(W - PAD.right, px)))
    }

    return (
        <div className="markets-chart" ref={wrapRef}>
            <svg
                ref={svgRef}
                className="markets-svg"
                width={W}
                height={H}
                viewBox={`0 0 ${W} ${H}`}
                role="img"
                aria-label="24-hour percentage-change chart of major market indices by region"
                onMouseMove={onMove}
                onMouseLeave={() => setHoverX(null)}
            >
                {yTicks.map((v, i) => (
                    <line key={`yg${i}`} className="chart-grid" x1={PAD.left} y1={y(v)} x2={W - PAD.right} y2={y(v)}/>
                ))}
                {xTicks.map((t, i) => (
                    <line key={`xg${i}`} className="chart-grid chart-grid-v" x1={x(t)} y1={PAD.top} x2={x(t)}
                          y2={H - PAD.bottom}/>
                ))}

                {/* Zero baseline — the rebasing reference every line starts from. */}
                <line className="markets-zero" x1={PAD.left} y1={y(0)} x2={W - PAD.right} y2={y(0)}/>

                {visible.map((s) => {
                    if (s.points.length < 2) return null
                    const d = s.points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(p.tMs).toFixed(1)},${y(p.pct).toFixed(1)}`).join(' ')
                    const tip = s.points[s.points.length - 1]
                    const dimmed = highlightKey != null && highlightKey !== s.key
                    const emphasised = highlightKey === s.key
                    return (
                        <g key={s.key} className="markets-series" style={{opacity: dimmed ? 0.16 : 1}}>
                            <path className="markets-line" d={d}
                                  style={{stroke: s.color, strokeWidth: emphasised ? 2.8 : undefined}}/>
                            <circle
                                cx={x(tip.tMs)}
                                cy={y(tip.pct)}
                                r={s.marketOpen ? 4 : 3}
                                className={s.marketOpen ? 'markets-dot-live' : 'markets-dot'}
                                style={{fill: s.color}}
                            />
                        </g>
                    )
                })}

                {hoverX != null && (
                    <line className="chart-crosshair" x1={hoverX} y1={PAD.top} x2={hoverX} y2={H - PAD.bottom}/>
                )}

                {yTicks.map((v, i) => (
                    <text key={`yl${i}`} className="chart-axis" x={PAD.left - 8} y={y(v) + 4} textAnchor="end">
                        {fmtPct(v)}
                    </text>
                ))}
                {xTicks.map((t, i) => {
                    const anchor = i === 0 ? 'start' : i === xTicks.length - 1 ? 'end' : 'middle'
                    return (
                        <text key={`xl${i}`} className="chart-axis" x={x(t)} y={H - 6} textAnchor={anchor}>
                            {fmtTime(new Date(t).toISOString())}
                        </text>
                    )
                })}
            </svg>

            <div className="markets-tip tnum">
                {hoverRows.length > 0 ? (
                    <>
                        <span className="markets-tip-time">{fmtTime(new Date(hoverTMs!).toISOString())}</span>
                        {hoverRows.map((r) => (
                            <span className="markets-tip-item" key={r.key}>
                                <span className="markets-tip-swatch" style={{background: r.color}}/>
                                {r.name} <strong className={r.pct >= 0 ? 'up' : 'down'}>{fmtPct(r.pct)}</strong>
                            </span>
                        ))}
                    </>
                ) : (
                    <span className="chart-tip-hint">hover the chart for values at a point in time</span>
                )}
            </div>
        </div>
    )
}
