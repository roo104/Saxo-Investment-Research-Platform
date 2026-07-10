import {useState} from 'react'
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

    async function runSearch(e: React.FormEvent) {
        e.preventDefault()
        setLoading(true)
        setError(null)
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
        <section className="panel">
            <div className="panel-head">
                <h2>Instrument search</h2>
                <span className="count">{searched ? `${results.length} results` : 'reference data'}</span>
            </div>

            <form className="search-form" onSubmit={runSearch}>
                <input
                    aria-label="Search keywords"
                    placeholder="Search Apple, Novo, Tesla…"
                    value={keywords}
                    onChange={(e) => setKeywords(e.target.value)}
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
                {!searched && !error && (
                    <div className="empty">
                        <span className="big">Search the Saxo universe</span>
                        Look up an instrument, then pin it to your watchlist.
                    </div>
                )}
            </div>
        </section>
    )
}
