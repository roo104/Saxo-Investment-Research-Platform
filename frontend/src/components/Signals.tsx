import {useEffect, useMemo, useState} from 'react'
import {api, ApiError} from '../api'
import type {IndicatorSeries, Signals as SignalsData, SignalDirection} from '../types'
import {PriceChart, type ChartOverlay} from './PriceChart'
import {MacdPanel, RsiPanel} from './Oscillator'
import {fmtDecimal} from '../format'

// `count` is the number of candles to display; the backend fetches extra warm-up candles on top
// so long indicators (SMA 200) are fully formed across the whole window, not just its tail.
const RANGES = [
    {key: 'Hourly', horizon: 60, count: 400},
    {key: 'Daily', horizon: 1440, count: 250},
    {key: 'Weekly', horizon: 10080, count: 250},
] as const

const OVERLAY_COLORS: Record<string, string> = {
    'SMA 50': '#f5a623',
    'SMA 200': '#a78bfa',
    'Bollinger upper': '#64748b',
    'Bollinger lower': '#64748b',
}

// Plain-language explanation of what each indicator measures — shown on hover.
const EXPLANATIONS: Record<string, string> = {
    'SMA 50/200':
        'Compares the 50-period and 200-period simple moving averages. When the faster 50 sits above the slower 200 the trend is up; a fresh cross above is the "golden cross" (bullish), a cross below the "death cross" (bearish).',
    'Price vs SMA 50':
        'Where the latest price sits relative to its 50-period average. Above the average points to short-term strength, below it to weakness.',
    'RSI (14)':
        'Relative Strength Index over 14 periods, on a 0–100 scale. Above 70 is overbought and may pull back; below 30 is oversold and may bounce; 30–70 is neutral.',
    'MACD (12,26,9)':
        'Momentum from the gap between the 12- and 26-period averages, versus a 9-period signal line. MACD above its signal line is bullish momentum, below is bearish; a cross flags a shift.',
    'Bollinger (20,2)':
        'A 20-period average with bands two standard deviations above and below. Closing above the upper band is a breakout, below the lower band a breakdown; inside the bands is normal volatility.',
}

// Plain-language explanation for the chart-overlay lines, shown on hover in the legend.
const OVERLAY_EXPLANATIONS: Record<string, string> = {
    'SMA 50':
        'The 50-period simple moving average — the average closing price over the last 50 periods, redrawn each period. It smooths out short-term noise to show the medium-term trend; price above it is generally a sign of strength, below it of weakness.',
    'SMA 200':
        'The 200-period simple moving average — the average closing price over the last 200 periods. It tracks the long-term trend and moves slowly; it often acts as a support or resistance level, and where the 50 sits relative to it defines the golden/death cross.',
    'Bollinger upper':
        'The upper Bollinger band — two standard deviations above the 20-period average. It rises and falls with volatility; price closing above it signals an unusually strong move (a breakout) rather than normal fluctuation.',
    'Bollinger lower':
        'The lower Bollinger band — two standard deviations below the 20-period average. It widens when volatility grows; price closing below it signals an unusually weak move (a breakdown) rather than normal fluctuation.',
}

const BIAS_LABEL: Record<SignalDirection, string> = {BULLISH: 'Bullish', BEARISH: 'Bearish', NEUTRAL: 'Neutral'}
const dirClass = (d: SignalDirection) => (d === 'BULLISH' ? 'sig-bull' : d === 'BEARISH' ? 'sig-bear' : 'sig-neutral')
const seriesOf = (list: IndicatorSeries[], name: string): (number | null)[] =>
    list.find((s) => s.name === name)?.points ?? []

// The raw indicator reading(s), localised: one number for most, "fast / slow" for the SMA cross.
const fmtSignalValue = (value: number[]): string => value.map((v) => fmtDecimal(v, 4)).join(' / ')

export function Signals({id, currency}: { id: number; currency: string | null }) {
    const [data, setData] = useState<SignalsData | null>(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    const [range, setRange] = useState<(typeof RANGES)[number]['key']>('Daily')
    const [attempt, setAttempt] = useState(0)

    useEffect(() => {
        let alive = true
        setLoading(true)
        const r = RANGES.find((x) => x.key === range)!
        api
            .getSignals(id, {horizon: r.horizon, count: r.count})
            .then((d) => alive && (setData(d), setError(null)))
            .catch((e) => alive && setError(e instanceof ApiError ? e.message : 'Could not load signals'))
            .finally(() => alive && setLoading(false))
        return () => {
            alive = false
        }
    }, [id, range, attempt])

    const overlays = useMemo<ChartOverlay[]>(() => {
        if (!data) return []
        return Object.keys(OVERLAY_COLORS)
            .map((name) => ({name, color: OVERLAY_COLORS[name], values: seriesOf(data.overlays, name)}))
            .filter((o) => o.values.some((v) => v != null))
    }, [data])

    if (loading) return <div className="empty"><span className="spinner"/>computing signals…</div>
    if (error)
        return (
            <div className="banner">
                <span>{error}</span>
                <button className="banner-retry" onClick={() => setAttempt((n) => n + 1)}>Retry</button>
            </div>
        )
    if (!data) return null

    const rangeTabs = (
        <div className="chart-controls">
            <div className="range-tabs">
                {RANGES.map((r) => (
                    <button key={r.key} className={`range-tab${range === r.key ? ' active' : ''}`}
                            onClick={() => setRange(r.key)}>{r.key}</button>
                ))}
            </div>
        </div>
    )

    if (!data.available) {
        return (
            <div className="signals">
                {rangeTabs}
                <div className="empty">
                    <span className="big">No signals</span>
                    Not enough price history to compute indicators for this instrument.
                </div>
            </div>
        )
    }

    const bull = data.signals.filter((s) => s.direction === 'BULLISH').length
    const bear = data.signals.filter((s) => s.direction === 'BEARISH').length

    return (
        <div className="signals">
            {rangeTabs}

            <div className="sig-summary">
                <span className={`sig-bias ${dirClass(data.netBias)}`}>{BIAS_LABEL[data.netBias]}</span>
                <span
                    className="sig-summary-note">{bull} bullish · {bear} bearish · {data.signals.length} indicators</span>
            </div>

            <div className="sig-cards">
                {data.signals.map((s) => {
                    const explanation = EXPLANATIONS[s.indicator]
                    return (
                        <div key={s.indicator}
                             className={`sig-card ${dirClass(s.direction)}${explanation ? ' has-tip' : ''}`}>
                            <div className="sig-card-head">
                                <span className="sig-dot"/>
                                <span className="sig-label">{s.label}</span>
                                {explanation ? <span className="sig-info" aria-hidden="true">i</span> : null}
                                {s.value.length ?
                                    <span className="sig-value tnum">{fmtSignalValue(s.value)}</span> : null}
                            </div>
                            <div className="sig-indicator">{s.indicator}</div>
                            <div className="sig-detail">{s.detail}</div>
                            {explanation ? <div className="sig-tip" role="tooltip">{explanation}</div> : null}
                        </div>
                    )
                })}
            </div>

            <PriceChart points={data.points} currency={currency} mode="line" overlays={overlays}/>
            <div className="sig-legend">
                {overlays.map((o) => {
                    const tip = OVERLAY_EXPLANATIONS[o.name]
                    return (
                        <span key={o.name} className={`sig-legend-item${tip ? ' has-tip' : ''}`}
                              tabIndex={tip ? 0 : undefined}>
                            <span className="sig-swatch" style={{background: o.color}}/>{o.name}
                            {tip ? <span className="sig-tip" role="tooltip">{tip}</span> : null}
                        </span>
                    )
                })}
            </div>

            <RsiPanel values={seriesOf(data.oscillators, 'RSI')} tip={EXPLANATIONS['RSI (14)']}/>
            <MacdPanel
                macd={seriesOf(data.oscillators, 'MACD')}
                signal={seriesOf(data.oscillators, 'Signal')}
                histogram={seriesOf(data.oscillators, 'Histogram')}
                tip={EXPLANATIONS['MACD (12,26,9)']}
            />
        </div>
    )
}
