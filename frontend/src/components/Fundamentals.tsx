import {Fragment, useEffect, useState} from 'react'
import {api, ApiError} from '../api'
import type {Fundamentals as FundamentalsData} from '../types'

export function Fundamentals({id}: { id: number }) {
    const [data, setData] = useState<FundamentalsData | null>(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    const [period, setPeriod] = useState<'year' | 'quarter'>('year')
    const [attempt, setAttempt] = useState(0)

    useEffect(() => {
        let alive = true
        setLoading(true)
        api
            .getFundamentals(id)
            .then((d) => alive && (setData(d), setError(null)))
            .catch((e) => alive && setError(e instanceof ApiError ? e.message : 'Could not load fundamentals'))
            .finally(() => alive && setLoading(false))
        return () => {
            alive = false
        }
    }, [id, attempt])

    if (loading) return <div className="empty"><span className="spinner"/>loading fundamentals…</div>
    if (error)
        return (
            <div className="banner">
                <span>{error}</span>
                <button className="banner-retry" onClick={() => setAttempt((n) => n + 1)}>Retry</button>
            </div>
        )
    if (!data) return null
    if (!data.available) {
        return (
            <div className="empty">
                <span className="big">No fundamentals</span>
                Company fundamentals aren't available for this instrument type.
            </div>
        )
    }

    const stmts = period === 'year' ? data.perYear : data.perQuarter

    return (
        <div className="fund">
            <h3 className="fund-title">Key stats</h3>
            <table className="fund-table kv">
                <thead>
                <tr>
                    <th/>
                    <th className="num">{data.symbol}</th>
                </tr>
                </thead>
                <tbody>
                {data.keyStats.map((k) => (
                    <tr key={k.label}>
                        <td>{k.label}</td>
                        <td className="num tnum">{k.value}</td>
                    </tr>
                ))}
                </tbody>
            </table>

            <div className="fund-fin-head">
                <h3 className="fund-title">Financials</h3>
                <div className="mode-toggle">
                    <button className={`range-tab${period === 'year' ? ' active' : ''}`}
                            onClick={() => setPeriod('year')}>Per year
                    </button>
                    <button className={`range-tab${period === 'quarter' ? ' active' : ''}`}
                            onClick={() => setPeriod('quarter')}>Per quarter
                    </button>
                </div>
            </div>
            <table className="fund-table fin">
                <thead>
                <tr>
                    <th/>
                    {stmts.periods.map((p) => (
                        <th key={p} className="num">{p}</th>
                    ))}
                </tr>
                </thead>
                <tbody>
                {stmts.sections.map((sec) => (
                    <Fragment key={sec.title}>
                        <tr className="fin-section">
                            <td colSpan={stmts.periods.length + 1}>— {sec.title}</td>
                        </tr>
                        {sec.rows.map((r) => (
                            <tr key={r.label}>
                                <td>{r.label}</td>
                                {r.values.map((v, i) => (
                                    <td key={i} className="num tnum">{v ?? '–'}</td>
                                ))}
                            </tr>
                        ))}
                    </Fragment>
                ))}
                </tbody>
            </table>
        </div>
    )
}
