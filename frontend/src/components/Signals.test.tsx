import {render, screen, waitFor} from '@testing-library/react'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {Signals} from './Signals'
import {api} from '../api'
import type {Signals as SignalsData} from '../types'

vi.mock('../api', () => ({
    api: {getSignals: vi.fn()},
    ApiError: class extends Error {
    },
}))

const data: SignalsData = {
    symbol: 'AAPL:xnas',
    horizonMinutes: 1440,
    available: true,
    asOf: '2026-07-10T00:00:00Z',
    netBias: 'BULLISH',
    signals: [
        {indicator: 'RSI (14)', label: 'Overbought', direction: 'BEARISH', value: [74], detail: 'RSI above 70.'},
        {
            indicator: 'SMA 50/200',
            label: 'Golden cross',
            direction: 'BULLISH',
            value: [1, 2],
            detail: 'Crossed above.'
        },
    ],
    points: [
        {time: '2026-07-08T00:00:00Z', open: 1, high: 1, low: 1, close: 1},
        {time: '2026-07-09T00:00:00Z', open: 1.1, high: 1.1, low: 1.1, close: 1.1},
        {time: '2026-07-10T00:00:00Z', open: 1.2, high: 1.2, low: 1.2, close: 1.2},
    ],
    overlays: [{name: 'SMA 50', points: [null, 1.05, 1.15]}],
    oscillators: [{name: 'RSI', points: [null, 60, 74]}],
}

describe('Signals', () => {
    beforeEach(() => vi.clearAllMocks())

    it('renders the net bias and one card per signal', async () => {
        vi.mocked(api.getSignals).mockResolvedValue(data)

        render(<Signals id={1} currency="USD"/>)

        await waitFor(() => expect(screen.getByText('Golden cross')).toBeInTheDocument())
        expect(screen.getByText('Bullish')).toBeInTheDocument()
        expect(screen.getByText('Overbought')).toBeInTheDocument()
    })

    it('shows the unavailable state when the backend has no data', async () => {
        vi.mocked(api.getSignals).mockResolvedValue({...data, available: false, signals: []})

        render(<Signals id={1} currency={null}/>)

        await waitFor(() => expect(screen.getByText('No signals')).toBeInTheDocument())
    })
})
