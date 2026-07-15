import {fmtDate, fmtDecimal} from '../format'
import type {ClosedPosition} from '../types'

/** A money amount in the given currency, e.g. "2,185.00 EUR"; em dash when absent. */
function money(value: number | null | undefined, currency: string | null | undefined): string {
    if (value == null) return '—'
    return currency ? `${fmtDecimal(value)} ${currency}` : fmtDecimal(value)
}

/** Sign class for colouring gains green / losses red; neutral gets no class. */
function sign(value: number | null | undefined): string {
    if (value == null || value === 0) return ''
    return value > 0 ? 'up' : 'down'
}

/** Sums a nullable field across positions; returns null when every value is absent. */
function total(positions: ClosedPosition[], pick: (p: ClosedPosition) => number | null): number | null {
    const present = positions.map(pick).filter((v): v is number => v != null)
    return present.length ? present.reduce((a, b) => a + b, 0) : null
}

/**
 * Realised (closed) positions: what each trade cost to open and close, and the profit/loss it
 * booked — including the slice attributable to currency conversion, which only exists once a
 * position is closed. Costs and P/L are shown in the account base currency so they sum across
 * instruments; the headline KPIs are those sums.
 *
 * Read-only and historical, so (unlike the open-positions panel) there is no live stream — the
 * parent loads it alongside the account snapshot.
 */
export function ClosedPositions({positions, currency}: { positions: ClosedPosition[]; currency: string | null }) {
    if (positions.length === 0) return null

    // Costs are outflows (Saxo reports them negative); present them as positive "spent" amounts.
    const openingSpent = total(positions, (p) => p.openingCost == null ? null : Math.abs(p.openingCost))
    const closingSpent = total(positions, (p) => p.closingCost == null ? null : Math.abs(p.closingCost))
    const fxPl = total(positions, (p) => p.currencyConversionPl)
    const realised = total(positions, (p) => p.profitLossBase)

    return (
        <section className="panel accounts">
            <div className="panel-head">
                <h2>Closed positions</h2>
                <span className="count">{positions.length}</span>
            </div>

            <div className="acct-kpis">
                <div className="acct-kpi">
                    <span className="acct-kpi-label">Opening costs</span>
                    <span className="acct-kpi-value tnum">{money(openingSpent, currency)}</span>
                </div>
                <div className="acct-kpi">
                    <span className="acct-kpi-label">Closing costs</span>
                    <span className="acct-kpi-value tnum">{money(closingSpent, currency)}</span>
                </div>
                <div className="acct-kpi">
                    <span className="acct-kpi-label">Currency conversion P/L</span>
                    <span className={`acct-kpi-value tnum ${sign(fxPl)}`}>{money(fxPl, currency)}</span>
                </div>
                <div className="acct-kpi">
                    <span className="acct-kpi-label">Realised P/L</span>
                    <span className={`acct-kpi-value tnum ${sign(realised)}`}>{money(realised, currency)}</span>
                </div>
            </div>

            <table className="fund-table acct-positions">
                <thead>
                <tr>
                    <th>Instrument</th>
                    <th className="num">Amount</th>
                    <th className="num">Closed</th>
                    <th className="num">Opening cost</th>
                    <th className="num">Closing cost</th>
                    <th className="num">FX conv.</th>
                    <th className="num">Realised P/L</th>
                </tr>
                </thead>
                <tbody>
                {positions.map((p) => (
                    <tr key={p.closedPositionId}>
                        <td>
                            <span className="acct-sym">{p.description}</span>
                            <span className="acct-desc" title={p.symbol}>{p.symbol}</span>
                        </td>
                        <td className="num tnum">{p.amount != null ? fmtDecimal(p.amount, 0) : '—'}</td>
                        <td className="num tnum">{p.closedAt ? fmtDate(p.closedAt) : '—'}</td>
                        <td className="num tnum">
                            {money(p.openingCost == null ? null : Math.abs(p.openingCost), currency)}
                        </td>
                        <td className="num tnum">
                            {money(p.closingCost == null ? null : Math.abs(p.closingCost), currency)}
                        </td>
                        <td className={`num tnum ${sign(p.currencyConversionPl)}`}>
                            {money(p.currencyConversionPl, currency)}
                        </td>
                        <td className={`num tnum ${sign(p.profitLossBase)}`}>{money(p.profitLossBase, currency)}</td>
                    </tr>
                ))}
                </tbody>
            </table>
        </section>
    )
}
