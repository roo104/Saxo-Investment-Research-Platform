import {useEffect, useMemo, useState} from 'react'
import type {WatchlistEntry} from '../types'
import {ASSET_FILTERS, type AssetFilter, inCategory} from '../assetTypes'
import {WatchRow} from './WatchRow'
import {WatchDetail} from './WatchDetail'

interface Props {
    entries: WatchlistEntry[]
    loading: boolean
    error: string | null
    streaming?: boolean
    onRemove: (id: number) => void
}

export function Watchlist({entries, loading, error, streaming, onRemove}: Props) {
    const [selectedId, setSelectedId] = useState<number | null>(null)
    const [filter, setFilter] = useState<AssetFilter>('All')

    // Filter by asset type, then show alphabetically by company name (the prominent label in each row).
    const visible = useMemo(
        () => entries
            .filter((e) => inCategory(e.assetType, filter))
            .sort((a, b) => a.description.localeCompare(b.description, undefined, {sensitivity: 'base'})),
        [entries, filter])

    // Keep a valid selection: default to the first visible row, and re-point if the selected one disappears.
    useEffect(() => {
        if (visible.length === 0) {
            setSelectedId(null)
        } else if (selectedId == null || !visible.some((e) => e.id === selectedId)) {
            setSelectedId(visible[0].id)
        }
    }, [visible, selectedId])

    const selected = entries.find((e) => e.id === selectedId) ?? null

    return (
        <div className="workspace">
            <section className="panel watchlist">
                <div className="panel-head">
                    <h2>Watchlist</h2>
                    <span className="count">
                        {streaming && entries.length > 0 ? <span className="live-dot" title="Live streaming"/> : null}
                        {loading && entries.length === 0 ? <span className="spinner"/> : null}
                        {filter === 'All' ? entries.length : `${visible.length} / ${entries.length}`} tracked
                    </span>
                </div>

                {entries.length > 0 && (
                    <div className="watch-filter" role="tablist" aria-label="Filter by asset type">
                        {ASSET_FILTERS.map((f) => (
                            <button key={f} role="tab" aria-selected={filter === f}
                                    className={`range-tab${filter === f ? ' active' : ''}`}
                                    onClick={() => setFilter(f)}>
                                {f}
                            </button>
                        ))}
                    </div>
                )}

                {error && <div className="banner">{error}</div>}

                <div className="rows">
                    {visible.map((e) => (
                        <WatchRow key={e.id} entry={e} selected={e.id === selectedId} onSelect={setSelectedId}
                                  onRemove={onRemove}/>
                    ))}

                    {!loading && entries.length === 0 && !error && (
                        <div className="empty">
                            <span className="big">Empty</span>
                            Search above and add an instrument to start tracking.
                        </div>
                    )}

                    {!loading && entries.length > 0 && visible.length === 0 && (
                        <div className="empty">
                            <span className="big">No matches</span>
                            No {filter} instruments on your watchlist.
                        </div>
                    )}
                </div>
            </section>

            {selected ? (
                <WatchDetail key={selected.id} entry={selected} onRemove={onRemove}/>
            ) : (
                <section className="panel detail detail-empty">
                    <div className="empty">
                        <span className="big">No instrument selected</span>
                        Pick something from your watchlist to see its chart, fundamentals and signals.
                    </div>
                </section>
            )}
        </div>
    )
}
