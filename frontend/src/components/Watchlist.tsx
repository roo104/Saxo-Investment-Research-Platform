import {useEffect, useState} from 'react'
import type {WatchlistEntry} from '../types'
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

    // Keep a valid selection: default to the first entry, and re-point if the selected one disappears.
    useEffect(() => {
        if (entries.length === 0) {
            setSelectedId(null)
        } else if (selectedId == null || !entries.some((e) => e.id === selectedId)) {
            setSelectedId(entries[0].id)
        }
    }, [entries, selectedId])

    const selected = entries.find((e) => e.id === selectedId) ?? null

    return (
        <div className="workspace">
            <section className="panel watchlist">
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
                        <WatchRow key={e.id} entry={e} selected={e.id === selectedId} onSelect={setSelectedId}
                                  onRemove={onRemove}/>
                    ))}

                    {!loading && entries.length === 0 && !error && (
                        <div className="empty">
                            <span className="big">Empty</span>
                            Search above and add an instrument to start tracking.
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
