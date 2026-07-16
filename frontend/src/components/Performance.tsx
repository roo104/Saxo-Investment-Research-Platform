import {useCallback, useEffect, useMemo, useState} from 'react'
import {api} from '../api'
import {fmtDateYear, fmtDecimal} from '../format'
import type {Performance as PerformanceData, PerformancePeriod} from '../types'

// Period presets: a short label for the tab, the Saxo StandardPeriod the backend expects.
const PERIODS: { label: string; period: PerformancePeriod }[] = [
    {label: '1M', period: 'Month'},
    {label: '3M', period: 'Quarter'},
    {label: '1Y', period: 'Year'},
    {label: 'All', period: 'AllTime'},
]

/** A money amount in the account base currency, e.g. "117,500.00 USD"; em dash when absent. */
function money(value: number | null | undefined, currency: string | null): string {
    if (value == null) return '—'
    return currency ? `${fmtDecimal(value)} ${currency}` : fmtDecimal(value)
}

/** A raw ratio rendered as a signed percentage, e.g. 0.175 -> "+17.50%". */
function pct(value: number | null | undefined): string {
    if (value == null) return '—'
    const s = fmtDecimal(value * 100)
    return value > 0 ? `+${s}%` : `${s}%`
}

/** Sign class for colouring gains green / losses red; neutral gets no class. */
function sign(value: number | null | undefined): string {
    if (value == null || value === 0) return ''
    return value > 0 ? 'up' : 'down'
}

const W = 600
const H = 160
const PAD = 4

/**
 * The account-value curve as a line + area chart, coloured by whether the period gained or lost.
 * Returns null (no chart) when there are fewer than two points to connect.
 */
function Curve({points, gaining}: { points: { date: string; value: number }[]; gaining: boolean }) {
    const {line, area} = useMemo(() => {
        const values = points.map((p) => p.value)
        const min = Math.min(...values)
        const max = Math.max(...values)
        const span = max - min || 1
        const x = (i: number) => PAD + (i / (points.length - 1)) * (W - 2 * PAD)
        const y = (v: number) => PAD + (1 - (v - min) / span) * (H - 2 * PAD)
        const line = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(2)},${y(p.value).toFixed(2)}`).join(' ')
        const area = `${line} L${x(points.length - 1).toFixed(2)},${H - PAD} L${x(0).toFixed(2)},${H - PAD} Z`
        return {line, area}
    }, [points])

    return (
        <svg className={`perf-chart ${gaining ? 'up' : 'down'}`} viewBox={`0 0 ${W} ${H}`}
             preserveAspectRatio="none" role="img" aria-label="Account value over time">
            <path className="perf-area" d={area}/>
            <path className="perf-line" d={line} fill="none"/>
        </svg>
    )
}

/**
 * The "Performance" panel: historic account value and return over a selectable period, served from
 * Saxo's History service group. The headline return is time-weighted when Saxo reports it, and the
 * chart is the account-value curve in base currency.
 *
 * Read-only and historical, so (unlike the open-positions panel) there is no live stream; it fetches
 * on mount and whenever the period changes. In simulation the series is often empty — the panel says
 * so honestly rather than drawing a flat line. Currency comes from the parent's account overview.
 */
export function Performance({currency}: { currency: string | null }) {
    const [period, setPeriod] = useState<PerformancePeriod>('Year')
    const [data, setData] = useState<PerformanceData | null>(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)

    const load = useCallback(async (p: PerformancePeriod) => {
        setLoading(true)
        try {
            setData(await api.getPerformance(p))
            setError(null)
        } catch {
            setError('Could not load performance data')
        } finally {
            setLoading(false)
        }
    }, [])

    useEffect(() => {
        load(period)
    }, [load, period])

    const points = data?.points ?? []
    const hasData = data?.available ?? false
    const gaining = (data?.returnPct ?? data?.absoluteReturn ?? 0) >= 0

    return (
        <section className="panel accounts">
            <div className="panel-head">
                <h2>Performance</h2>
                <div className="perf-controls">
                    {loading && <span className="spinner"/>}
                    <div className="range-tabs">
                        {PERIODS.map((p) => (
                            <button key={p.period}
                                    className={`range-tab${period === p.period ? ' active' : ''}`}
                                    aria-pressed={period === p.period}
                                    onClick={() => setPeriod(p.period)}>
                                {p.label}
                            </button>
                        ))}
                    </div>
                </div>
            </div>

            {error && <div className="banner">{error}</div>}

            {hasData ? (
                <>
                    <div className="acct-kpis">
                        <div className="acct-kpi">
                            <span className="acct-kpi-label">Return</span>
                            <span
                                className={`acct-kpi-value tnum ${sign(data?.returnPct)}`}>{pct(data?.returnPct)}</span>
                        </div>
                        <div className="acct-kpi">
                            <span className="acct-kpi-label">Change</span>
                            <span className={`acct-kpi-value tnum ${sign(data?.absoluteReturn)}`}>
                                {money(data?.absoluteReturn, currency)}
                            </span>
                        </div>
                        <div className="acct-kpi">
                            <span className="acct-kpi-label">Start value</span>
                            <span className="acct-kpi-value tnum">{money(data?.startValue, currency)}</span>
                        </div>
                        <div className="acct-kpi">
                            <span className="acct-kpi-label">Current value</span>
                            <span className="acct-kpi-value tnum">{money(data?.endValue, currency)}</span>
                        </div>
                    </div>

                    {points.length >= 2 && (
                        <figure className="perf-figure">
                            <Curve points={points} gaining={gaining}/>
                            <figcaption className="perf-axis">
                                <span>{fmtDateYear(points[0].date)}</span>
                                <span>{fmtDateYear(points[points.length - 1].date)}</span>
                            </figcaption>
                        </figure>
                    )}
                </>
            ) : (
                !loading && !error && (
                    <div className="empty">
                        <span className="big">No performance history</span>
                        Saxo has no account-value history for this period. In simulation the
                        performance service is often empty even for a funded account.
                    </div>
                )
            )}
        </section>
    )
}
