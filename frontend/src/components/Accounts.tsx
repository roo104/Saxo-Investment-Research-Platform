import {useCallback, useEffect, useState} from 'react'
import {api} from '../api'
import {fmtDecimal} from '../format'
import type {AccountBalance, AccountOverview, Position} from '../types'

// Balance and positions arrive live over SSE; a slow reconcile poll corrects any drift and keeps
// the accounts list (which the stream doesn't carry) fresh, and covers gaps where simulation
// entitlements make the stream deltas sparse.
const REFRESH_MS = 60_000

/** A money amount in its account/instrument currency, e.g. "117,500.00 USD". */
function money(value: number | null | undefined, currency: string | null | undefined): string {
    if (value == null) return '—'
    return currency ? `${fmtDecimal(value)} ${currency}` : fmtDecimal(value)
}

/** A raw ratio rendered as a signed percentage, e.g. 0.1667 -> "+16.67%". */
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

/**
 * The "Accounts" panel: the authenticated Saxo account's balance headline (total value, cash,
 * unrealised P/L, open positions) plus a table of open net positions with per-position P/L.
 *
 * Everything is read-only and served live from Saxo's Portfolio service group. In simulation the
 * balance is always available; positions are only present once the sim account holds any.
 */
export function Accounts() {
    const [overview, setOverview] = useState<AccountOverview | null>(null)
    const [positions, setPositions] = useState<Position[]>([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    const [streaming, setStreaming] = useState(false)

    const load = useCallback(async () => {
        try {
            const [o, p] = await Promise.all([api.getAccount(), api.getPositions()])
            setOverview(o)
            setPositions(p)
            setError(null)
        } catch {
            setError('Could not load account data')
        } finally {
            setLoading(false)
        }
    }, [])

    // Initial render plus a slow reconcile poll as a fallback for the live stream.
    useEffect(() => {
        load()
        const id = setInterval(load, REFRESH_MS)
        return () => clearInterval(id)
    }, [load])

    // Live balance + positions over SSE, merged into the polled state.
    useEffect(() => {
        const es = new EventSource('/api/account/stream')
        es.onopen = () => setStreaming(true)
        es.onerror = () => setStreaming(false)
        es.addEventListener('balance', (ev) => {
            const balance: AccountBalance = JSON.parse((ev as MessageEvent).data)
            setOverview((prev) => ({accounts: prev?.accounts ?? [], balance}))
        })
        es.addEventListener('positions', (ev) => {
            const next: Position[] = JSON.parse((ev as MessageEvent).data)
            setPositions(next)
        })
        return () => es.close()
    }, [])

    const balance = overview?.balance
    const account = overview?.accounts.find((a) => a.active) ?? overview?.accounts[0] ?? null
    const ccy = balance?.currency ?? account?.currency ?? null
    // Account-level unrealised P/L is the sum of each position's P/L in the account base currency.
    // Saxo's balance has no honest field for this — its UnrealizedPositionsValue is a position value,
    // not a gain/loss — so we aggregate the positions we already hold.
    const unrealized = positions.length
        ? positions.reduce((sum, p) => sum + (p.profitLossBase ?? p.profitLoss ?? 0), 0)
        : null

    return (
        <section className="panel accounts">
            <div className="panel-head">
                <h2>Accounts</h2>
                <span className="count">
                    {loading && !overview ? <span className="spinner"/> : null}
                    {streaming ? <span className="live-dot" title="Live streaming"/> : null}
                    {account ? `#${account.accountId}${account.accountType ? ` · ${account.accountType}` : ''}` : ''}
                </span>
            </div>

            {error && <div className="banner">{error}</div>}

            {balance && (
                <div className="acct-kpis">
                    <div className="acct-kpi">
                        <span className="acct-kpi-label">Total value</span>
                        <span className="acct-kpi-value tnum">{money(balance.totalValue, ccy)}</span>
                    </div>
                    <div className="acct-kpi">
                        <span className="acct-kpi-label">Cash</span>
                        <span className="acct-kpi-value tnum">{money(balance.cashBalance, ccy)}</span>
                    </div>
                    <div className="acct-kpi">
                        <span className="acct-kpi-label">Unrealised P/L</span>
                        <span className={`acct-kpi-value tnum ${sign(unrealized)}`}>{money(unrealized, ccy)}</span>
                    </div>
                    <div className="acct-kpi">
                        <span className="acct-kpi-label">Open positions</span>
                        <span className="acct-kpi-value tnum">{balance.openPositionsCount ?? positions.length}</span>
                    </div>
                </div>
            )}

            {positions.length > 0 ? (
                <table className="fund-table acct-positions">
                    <thead>
                    <tr>
                        <th>Instrument</th>
                        <th className="num">Amount</th>
                        <th className="num">Avg open</th>
                        <th className="num">Last</th>
                        <th className="num">Market value</th>
                        <th className="num">P/L</th>
                        <th className="num">Day</th>
                    </tr>
                    </thead>
                    <tbody>
                    {positions.map((p) => (
                        <tr key={p.netPositionId}>
                            <td>
                                <span className="acct-sym">{p.symbol}</span>
                                <span className="acct-desc">{p.description}</span>
                            </td>
                            <td className="num tnum">{p.amount != null ? fmtDecimal(p.amount, 0) : '—'}</td>
                            <td className="num tnum">{p.averageOpenPrice != null ? fmtDecimal(p.averageOpenPrice) : '—'}</td>
                            <td className="num tnum">{p.currentPrice != null ? fmtDecimal(p.currentPrice) : '—'}</td>
                            <td className="num tnum">{money(p.marketValue, p.currency)}</td>
                            <td className={`num tnum ${sign(p.profitLoss)}`}>
                                {money(p.profitLoss, p.currency)}
                                {p.profitLossPct != null &&
                                    <span className="acct-pl-pct"> ({pct(p.profitLossPct)})</span>}
                            </td>
                            <td className={`num tnum ${sign(p.dayChangePct)}`}>{pct(p.dayChangePct)}</td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            ) : (
                !loading && !error && (
                    <div className="empty">
                        <span className="big">No open positions</span>
                        This account isn't holding anything right now.
                    </div>
                )
            )}
        </section>
    )
}
