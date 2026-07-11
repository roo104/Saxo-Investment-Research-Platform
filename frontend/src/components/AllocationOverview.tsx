import {useMemo} from 'react'
import type {PortfolioEntry} from '../types'
import {type Breakdown, buildAllocation} from '../allocation'
import {fmtPercent} from '../format'
import {DonutChart} from './DonutChart'

interface Props {
    entries: PortfolioEntry[]
}

// Plain-language explanation shown behind each card's "?" affordance.
const HELP: Record<string, string> = {
    Assets: 'How your holdings split across asset classes (stocks, ETFs, currencies), weighted by market value.',
    Country: 'Share of holdings by the country of the exchange each instrument lists on — not the issuer’s home country.',
    Sector: 'Share of equity holdings by sector. Sourced from fundamentals data; FX, ETFs and unclassified names show as “Unknown”.',
    Currency: 'Share of holdings by the currency each instrument trades in. Values are not converted to a single currency.',
}

/**
 * The three allocation donuts (Assets · Country · Currency) with percentage legends, weighted by
 * each position's market value. Recomputes as live prices stream into [entries].
 */
export function AllocationOverview({entries}: Props) {
    const allocation = useMemo(() => buildAllocation(entries), [entries])

    return (
        <section className="panel allocation">
            <div className="panel-head">
                <h2>Allocation</h2>
            </div>
            <div className="alloc-cards">
                <AllocationCard breakdown={allocation.assets}/>
                <AllocationCard breakdown={allocation.country}/>
                <AllocationCard breakdown={allocation.sector}/>
                <AllocationCard breakdown={allocation.currency}/>
            </div>
        </section>
    )
}

function AllocationCard({breakdown}: { breakdown: Breakdown }) {
    return (
        <div className="alloc-card">
            <div className="alloc-card-head">
                <h3>{breakdown.title}</h3>
                <span className="help" tabIndex={0} role="img" aria-label={HELP[breakdown.title]}
                      title={HELP[breakdown.title]}>?</span>
            </div>

            <DonutChart slices={breakdown.slices}/>

            {breakdown.slices.length > 0 ? (
                <ul className="alloc-legend">
                    {breakdown.slices.map((s) => (
                        <li className="alloc-legend-item" key={s.label}>
                            <span className="alloc-swatch" style={{background: s.color}}/>
                            <span className="alloc-label" title={s.label}>{s.label}</span>
                            <span className="alloc-pct tnum">{fmtPercent(s.pct)}</span>
                        </li>
                    ))}
                </ul>
            ) : (
                <p className="alloc-empty">No priced positions yet.</p>
            )}
        </div>
    )
}
