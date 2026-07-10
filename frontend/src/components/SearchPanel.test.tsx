import {render, screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {SearchPanel} from './SearchPanel'
import {api} from '../api'
import type {Instrument} from '../types'

vi.mock('../api', () => ({
    api: {searchInstruments: vi.fn()},
    ApiError: class extends Error {
    },
}))

const apple: Instrument = {
    uic: 211,
    symbol: 'AAPL:xnas',
    description: 'Apple Inc.',
    assetType: 'Stock',
    exchangeId: 'NASDAQ',
    currencyCode: 'USD',
}

describe('SearchPanel', () => {
    beforeEach(() => vi.clearAllMocks())

    it('runs a search and renders results, then adds one to the watchlist', async () => {
        vi.mocked(api.searchInstruments).mockResolvedValue([apple])
        const onAdd = vi.fn().mockResolvedValue(undefined)
        const user = userEvent.setup()

        render(<SearchPanel onAdd={onAdd} isOnWatchlist={() => false}/>)

        await user.type(screen.getByLabelText('Search keywords'), 'Apple')
        await user.click(screen.getByRole('button', {name: /search/i}))

        await waitFor(() => expect(screen.getByText('AAPL:xnas')).toBeInTheDocument())
        expect(api.searchInstruments).toHaveBeenCalledWith({keywords: 'Apple', assetTypes: 'Stock'})

        await user.click(screen.getByRole('button', {name: /add/i}))
        await waitFor(() => expect(onAdd).toHaveBeenCalledWith(apple))
    })

    it('marks instruments already on the watchlist as non-addable', async () => {
        vi.mocked(api.searchInstruments).mockResolvedValue([apple])
        const user = userEvent.setup()

        render(<SearchPanel onAdd={vi.fn()} isOnWatchlist={() => true}/>)

        await user.click(screen.getByRole('button', {name: /search/i}))
        await waitFor(() => expect(screen.getByText('On list')).toBeInTheDocument())
        expect(screen.getByRole('button', {name: 'On list'})).toBeDisabled()
    })
})
