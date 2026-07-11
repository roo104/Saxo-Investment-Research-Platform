import {useEffect, useRef, useState} from 'react'
import {api, ApiError} from '../api'
import type {Instrument} from '../types'

const ASSET_TYPES = ['Stock', 'Etf', 'FxSpot', 'Bond', 'MutualFund', 'CfdOnStock']

interface Props {
    onAdd: (instrument: Instrument) => Promise<void>
    isOnWatchlist: (instrument: Instrument) => boolean
}

export function SearchPanel({onAdd, isOnWatchlist}: Props) {
    const [keywords, setKeywords] = useState('')
    const [assetType, setAssetType] = useState('Stock')
    const [results, setResults] = useState<Instrument[]>([])
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [searched, setSearched] = useState(false)
    const [adding, setAdding] = useState<number | null>(null)
    const [open, setOpen] = useState(false)
    const rootRef = useRef<HTMLDivElement>(null)

    // Close the results dropdown on outside click or Escape.
    useEffect(() => {
        if (!open) return

        function onDown(e: MouseEvent) {
            if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false)
        }

        function onKey(e: KeyboardEvent) {
            if (e.key === 'Escape') setOpen(false)
        }

        document.addEventListener('mousedown', onDown)
        document.addEventListener('keydown', onKey)
        return () => {
            document.removeEventListener('mousedown', onDown)
            document.removeEventListener('keydown', onKey)
        }
    }, [open])

    async function runSearch(e: React.FormEvent) {
        e.preventDefault()
        setLoading(true)
        setError(null)
        setOpen(true)
        try {
            const data = await api.searchInstruments({keywords, assetTypes: assetType})
            setResults(data)
            setSearched(true)
        } catch (err) {
            setError(err instanceof ApiError ? err.message : 'Search failed')
            setResults([])
        } finally {
            setLoading(false)
        }
    }

    async function handleAdd(instrument: Instrument) {
        setAdding(instrument.uic)
        try {
            await onAdd(instrument)
        } finally {
            setAdding(null)
        }
    }

    return (
        <div className="searchbar" ref={rootRef}>
            <form className="search-form" onSubmit={runSearch}>
                <svg className="search-icon" viewBox="0 0 24 24" aria-hidden="true">
                    <circle cx="11" cy="11" r="7" fill="none" stroke="currentColor" strokeWidth="2"/>
                    <line x1="16.5" y1="16.5" x2="21" y2="21" stroke="currentColor" strokeWidth="2"
                          strokeLinecap="round"/>
                </svg>
                <input
                    aria-label="Search keywords"
                    placeholder="Search the Saxo universe — Apple, Novo, Tesla…"
                    value={keywords}
                    onChange={(e) => setKeywords(e.target.value)}
                    onFocus={() => searched && setOpen(true)}
                />
                <select aria-label="Asset type" value={assetType} onChange={(e) => setAssetType(e.target.value)}>
                    {ASSET_TYPES.map((t) => (
                        <option key={t} value={t}>
                            {t}
                        </option>
                    ))}
                </select>
                <button className="btn btn-primary" type="submit" disabled={loading}>
                    {loading ? <span className="spinner"/> : null}
                    Search
                </button>
            </form>

            {open && (
                <div className="search-results">
                    {error && <div className="banner">{error}</div>}

                    <div className="rows">
                        {results.map((r) => {
                            const added = isOnWatchlist(r)
                            return (
                                <div className="row result-row" key={`${r.uic}-${r.assetType}`}>
                                    <div>
                                        <div className="sym">{r.symbol}</div>
                                        <div className="desc" title={r.description}>
                                            {r.description}
                                        </div>
                                    </div>
                                    <span className="tag">{r.exchangeId ?? r.assetType}</span>
                                    <button
                                        className="btn btn-ghost"
                                        disabled={added || adding === r.uic}
                                        onClick={() => handleAdd(r)}
                                    >
                                        {added ? 'On list' : adding === r.uic ? '…' : '+ Add'}
                                    </button>
                                </div>
                            )
                        })}

                        {!loading && searched && results.length === 0 && !error && (
                            <div className="empty">
                                <span className="big">Nothing found</span>
                                Try a different keyword or asset type.
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    )
}
