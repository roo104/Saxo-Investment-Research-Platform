// Small sub-charts for indicators that live on their own scale (not the price axis):
// RSI on a 0..100 band, and MACD with its signal line and histogram. Rendered below the price chart.
// Width is measured from the container so the SVG maps 1:1 to pixels (no aspect-ratio distortion).
// PAD.left/right match the price chart's, so a given point lines up vertically down the whole stack;
// the bottom-most panel carries the shared time axis (see [showTime]).

import {useLayoutEffect, useRef, useState} from 'react'

const OH = 96
// left/right padding mirrors PriceChart's so the plot areas align across the stacked timeline
const PAD = {top: 10, right: 16, bottom: 14, left: 56}
const INNER_H = OH - PAD.top - PAD.bottom

/** Track the pixel width of a container element, following live resizes. */
function useMeasuredWidth(): [React.RefObject<HTMLDivElement | null>, number] {
    const ref = useRef<HTMLDivElement>(null)
    const [width, setWidth] = useState(560)
    useLayoutEffect(() => {
        const el = ref.current
        if (!el) return
        const update = () => setWidth(el.clientWidth || 560)
        update()
        if (typeof ResizeObserver === 'undefined') return
        const ro = new ResizeObserver(update)
        ro.observe(el)
        return () => ro.disconnect()
    }, [])
    return [ref, width]
}

const xAt = (n: number, innerW: number) => (i: number) => PAD.left + (n <= 1 ? 0 : (i / (n - 1)) * innerW)

/** Build an SVG path over a nullable series, breaking the line across warm-up gaps. */
function linePath(values: (number | null)[], x: (i: number) => number, y: (v: number) => number): string {
    let d = ''
    let pen = false
    values.forEach((v, i) => {
        if (v == null || !Number.isFinite(v)) {
            pen = false
            return
        }
        d += `${pen ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)} `
        pen = true
    })
    return d.trim()
}

// ~6 evenly spaced tick indices across a series of length n (same cadence as the price chart's x-axis).
function timeTicks(n: number): number[] {
    const count = Math.min(6, n)
    if (count < 2) return n > 0 ? [0] : []
    return Array.from({length: count}, (_, i) => Math.round((i * (n - 1)) / (count - 1)))
}

// Shared time axis along the bottom gutter of a panel; only the bottom-most panel renders it.
function TimeAxis({n, labels, x}: { n: number; labels: string[]; x: (i: number) => number }) {
    const ticks = timeTicks(n)
    return (
        <>
            {ticks.map((idx, i) => {
                const label = labels[idx]
                if (!label) return null
                const anchor = i === 0 ? 'start' : i === ticks.length - 1 ? 'end' : 'middle'
                return (
                    <text key={idx} className="osc-axis" x={x(idx)} y={OH - 3} textAnchor={anchor}>{label}</text>
                )
            })}
        </>
    )
}

// Plain-language explanation of each RSI element, keyed by its swatch colour.
const RSI_LEGEND = [
    {
        swatch: 'rsi',
        label: 'RSI',
        tip: 'Relative Strength Index on a 0–100 scale. It rises when recent gains outpace losses and falls when losses dominate.',
    },
    {
        swatch: 'guide',
        label: 'Overbought / oversold',
        tip: 'The dashed lines at 70 and 30. Above 70 is overbought and may pull back; below 30 is oversold and may bounce.',
    },
    {
        swatch: 'zone',
        label: 'Neutral 30–70',
        tip: 'The shaded band between 30 and 70 — normal territory, where RSI gives no strong overbought or oversold signal.',
    },
] as const

export function RsiPanel(
    {values, tip, labels, showTime}:
    { values: (number | null)[]; tip?: string; labels?: string[]; showTime?: boolean },
) {
    const [ref, width] = useMeasuredWidth()
    if (values.length < 2) return <div className="osc" ref={ref}/>
    const innerW = width - PAD.left - PAD.right
    const x = xAt(values.length, innerW)
    const y = (v: number) => PAD.top + INNER_H * (1 - v / 100)
    return (
        <div className="osc" ref={ref}>
            <div className={`osc-title${tip ? ' has-tip' : ''}`} tabIndex={tip ? 0 : undefined}>
                RSI (14)
                {tip ? <span className="sig-info" aria-hidden="true">i</span> : null}
                {tip ? <span className="sig-tip" role="tooltip">{tip}</span> : null}
            </div>
            <svg className="osc-svg" viewBox={`0 0 ${width} ${OH}`} role="img" aria-label="RSI">
                <rect className="osc-zone" x={PAD.left} width={innerW} y={y(70)} height={y(30) - y(70)}/>
                {[30, 70].map((g) => (
                    <line key={g} className="osc-guide" x1={PAD.left} x2={width - PAD.right} y1={y(g)} y2={y(g)}/>
                ))}
                <path className="osc-line osc-rsi" d={linePath(values, x, y)} vectorEffect="non-scaling-stroke"/>
                <text className="osc-axis" x={PAD.left - 5} y={y(70) + 3} textAnchor="end">70</text>
                <text className="osc-axis" x={PAD.left - 5} y={y(30) + 3} textAnchor="end">30</text>
                {showTime && labels ? <TimeAxis n={values.length} labels={labels} x={x}/> : null}
            </svg>
            <div className="sig-legend osc-legend">
                {RSI_LEGEND.map((item) => (
                    <span key={item.label} className="sig-legend-item has-tip" tabIndex={0}>
                        <span className={`sig-swatch osc-swatch-${item.swatch}`}/>{item.label}
                        <span className="sig-tip" role="tooltip">{item.tip}</span>
                    </span>
                ))}
            </div>
        </div>
    )
}

// Plain-language explanation of each MACD series, keyed by its swatch colour.
const MACD_LEGEND = [
    {
        swatch: 'macd',
        label: 'MACD',
        tip: 'The MACD line — the gap between the 12- and 26-period moving averages. Above zero the faster average leads (upward momentum); below zero it lags (downward).',
    },
    {
        swatch: 'signal',
        label: 'Signal',
        tip: 'The signal line — a 9-period average of the MACD line that smooths it. When MACD crosses above the signal, momentum is turning up; a cross below turns it down.',
    },
    {
        swatch: 'hist',
        label: 'Histogram',
        tip: 'The gap between the MACD and signal lines. Green bars grow as bullish momentum builds, red as bearish; bars near zero mean the two lines are about to cross.',
    },
] as const

export function MacdPanel(
    {macd, signal, histogram, tip, labels, showTime}:
    {
        macd: (number | null)[]; signal: (number | null)[]; histogram: (number | null)[];
        tip?: string; labels?: string[]; showTime?: boolean
    },
) {
    const [ref, width] = useMeasuredWidth()
    const finite = [...macd, ...signal, ...histogram].filter((v): v is number => v != null && Number.isFinite(v))
    if (macd.length < 2 || finite.length === 0) return <div className="osc" ref={ref}/>
    const innerW = width - PAD.left - PAD.right
    const bound = Math.max(...finite.map(Math.abs)) || 1
    const x = xAt(macd.length, innerW)
    const y = (v: number) => PAD.top + INNER_H * (1 - (v + bound) / (2 * bound))
    const barW = Math.max(1, (innerW / macd.length) * 0.6)
    const zero = y(0)
    return (
        <div className="osc" ref={ref}>
            <div className={`osc-title${tip ? ' has-tip' : ''}`} tabIndex={tip ? 0 : undefined}>
                MACD (12,26,9)
                {tip ? <span className="sig-info" aria-hidden="true">i</span> : null}
                {tip ? <span className="sig-tip up" role="tooltip">{tip}</span> : null}
            </div>
            <svg className="osc-svg" viewBox={`0 0 ${width} ${OH}`} role="img" aria-label="MACD">
                <line className="osc-guide" x1={PAD.left} x2={width - PAD.right} y1={zero} y2={zero}/>
                {histogram.map((v, i) => {
                    if (v == null || !Number.isFinite(v)) return null
                    const yv = y(v)
                    return (
                        <rect key={i} className={`osc-hist ${v >= 0 ? 'up' : 'down'}`} x={x(i) - barW / 2}
                              width={barW} y={Math.min(yv, zero)} height={Math.max(0.5, Math.abs(yv - zero))}/>
                    )
                })}
                <path className="osc-line osc-macd" d={linePath(macd, x, y)} vectorEffect="non-scaling-stroke"/>
                <path className="osc-line osc-signal" d={linePath(signal, x, y)} vectorEffect="non-scaling-stroke"/>
                {showTime && labels ? <TimeAxis n={macd.length} labels={labels} x={x}/> : null}
            </svg>
            <div className="sig-legend osc-legend">
                {MACD_LEGEND.map((item) => (
                    <span key={item.label} className="sig-legend-item has-tip" tabIndex={0}>
                        <span className={`sig-swatch osc-swatch-${item.swatch}`}/>{item.label}
                        <span className="sig-tip" role="tooltip">{item.tip}</span>
                    </span>
                ))}
            </div>
        </div>
    )
}
