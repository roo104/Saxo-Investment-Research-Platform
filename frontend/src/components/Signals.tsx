import {useEffect, useMemo, useState} from 'react'
import {api, ApiError} from '../api'
import type {IndicatorSeries, Signals as SignalsData, SignalDirection} from '../types'
import {PriceChart, type ChartOverlay} from './PriceChart'
import {MacdPanel, RsiPanel} from './Oscillator'

// Signals need enough candles to warm the indicators up (SMA 200 in particular), so the presets
// request longer series than the live chart does.
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

const BIAS_LABEL: Record<SignalDirection, string> = {BULLISH: 'Bullish', BEARISH: 'Bearish', NEUTRAL: 'Neutral'}
const dirClass = (d: SignalDirection) => (d === 'BULLISH' ? 'sig-bull' : d === 'BEARISH' ? 'sig-bear' : 'sig-neutral')
const seriesOf = (list: IndicatorSeries[], name: string): (number | null)[] =>
    list.find((s) => s.name === name)?.points ?? []

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
                {data.signals.map((s) => (
                    <div key={s.indicator} className={`sig-card ${dirClass(s.direction)}`}>
                        <div className="sig-card-head">
                            <span className="sig-dot"/>
                            <span className="sig-label">{s.label}</span>
                            {s.value ? <span className="sig-value tnum">{s.value}</span> : null}
                        </div>
                        <div className="sig-indicator">{s.indicator}</div>
                        <div className="sig-detail">{s.detail}</div>
                    </div>
                ))}
            </div>

            <PriceChart points={data.points} currency={currency} mode="line" overlays={overlays}/>
            <div className="sig-legend">
                {overlays.map((o) => (
                    <span key={o.name} className="sig-legend-item">
                        <span className="sig-swatch" style={{background: o.color}}/>{o.name}
                    </span>
                ))}
            </div>

            <RsiPanel values={seriesOf(data.oscillators, 'RSI')}/>
            <MacdPanel
                macd={seriesOf(data.oscillators, 'MACD')}
                signal={seriesOf(data.oscillators, 'Signal')}
                histogram={seriesOf(data.oscillators, 'Histogram')}
            />
        </div>
    )
}
