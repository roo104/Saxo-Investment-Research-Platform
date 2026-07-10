import type {WatchlistEntry} from '../types'
import {WatchRow} from './WatchRow'

interface Props {
    entries: WatchlistEntry[]
    loading: boolean
    error: string | null
    streaming?: boolean
    onRemove: (id: number) => void
}

export function Watchlist({entries, loading, error, streaming, onRemove}: Props) {
    return (
        <section className="panel">
            <div className="panel-head">
                <h2>Watchlist</h2>
                <span className="count">
          {streaming && entries.length > 0 ? <span className="live-dot" title="Live streaming"/> : null}
                    {loading && entries.length === 0 ? <span className="spinner"/> : null}
                    {entries.length} tracked
        </span>
            </div>

            {error && <div className="banner">{error}</div>}

            <div className="rows">
                {entries.map((e) => (
                    <WatchRow key={e.id} entry={e} onRemove={onRemove}/>
                ))}

                {!loading && entries.length === 0 && !error && (
                    <div className="empty">
                        <span className="big">Your watchlist is empty</span>
                        Search for an instrument and add it to start tracking quotes.
                    </div>
                )}
            </div>
        </section>
    )
}
