import {render, screen, waitFor} from '@testing-library/react'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {Accounts} from './Accounts'
import {api} from '../api'
import type {AccountOverview, Position} from '../types'

vi.mock('../api', () => ({
    api: {getAccount: vi.fn(), getPositions: vi.fn()},
}))

const overview: AccountOverview = {
    accounts: [{accountId: '9226248', currency: 'USD', accountType: 'Normal', active: true}],
    balance: {
        currency: 'USD', cashBalance: 100000, totalValue: 117500,
        nonMarginPositionsValue: 17500, unrealizedPositionsValue: 2500,
        marginAvailable: 95000, marginUsed: 5000, openPositionsCount: 1,
    },
}

const position: Position = {
    netPositionId: '211__Stock', uic: 211, symbol: 'AAPL:xnas', description: 'Apple Inc.',
    assetType: 'Stock', currency: 'USD', amount: 100, openingDirection: 'Buy',
    averageOpenPrice: 150, currentPrice: 175, marketValue: 17500,
    profitLoss: 2500, profitLossPct: 0.1667, dayChangePct: 0.012,
}

describe('Accounts', () => {
    beforeEach(() => vi.clearAllMocks())

    it('renders the balance headline and a positions row with signed P/L', async () => {
        vi.mocked(api.getAccount).mockResolvedValue(overview)
        vi.mocked(api.getPositions).mockResolvedValue([position])

        const {container} = render(<Accounts/>)

        await waitFor(() => expect(screen.getByText('AAPL:xnas')).toBeInTheDocument())
        expect(screen.getByText(/117,?500\.00 USD/)).toBeInTheDocument()
        // P/L cell shows the currency amount and its ratio as a signed percent, coloured up.
        const plCell = container.querySelector('.acct-positions td.up')
        expect(plCell).not.toBeNull()
        expect(plCell!.textContent).toContain('+16.67%')
    })

    it('shows an empty state when there are no open positions', async () => {
        vi.mocked(api.getAccount).mockResolvedValue(overview)
        vi.mocked(api.getPositions).mockResolvedValue([])

        render(<Accounts/>)

        await waitFor(() => expect(screen.getByText(/No open positions/i)).toBeInTheDocument())
    })

    it('surfaces an error banner when the account fails to load', async () => {
        vi.mocked(api.getAccount).mockRejectedValue(new Error('boom'))
        vi.mocked(api.getPositions).mockRejectedValue(new Error('boom'))

        render(<Accounts/>)

        await waitFor(() => expect(screen.getByText(/Could not load account data/i)).toBeInTheDocument())
    })
})
