import type {PortfolioEntry} from '../types'

/**
 * A small pill showing whether the instrument's exchange is currently open. Nothing renders when
 * the state is unknown (no live market state and an unrecognised exchange).
 */
export function MarketBadge({entry}: { entry: PortfolioEntry }) {
    if (entry.marketOpen == null) return null
    const open = entry.marketOpen
    const where = entry.exchange ?? 'Market'
    return (
        <span className={`mkt-badge${open ? ' is-open' : ''}`} title={`${where} — ${open ? 'open' : 'closed'}`}>
            <span className="mkt-dot"/>
            {open ? 'Open' : 'Closed'}
        </span>
    )
}
