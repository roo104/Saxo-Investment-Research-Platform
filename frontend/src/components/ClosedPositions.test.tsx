import {render, screen, within} from '@testing-library/react'
import {describe, expect, it} from 'vitest'
import {ClosedPositions} from './ClosedPositions'
import type {ClosedPosition} from '../types'

const base: ClosedPosition = {
    closedPositionId: '1-2', uic: 21, symbol: 'EURUSD', description: 'Euro/US Dollar',
    assetType: 'FxSpot', currency: 'USD', amount: 100000, buyOrSell: 'Buy',
    openPrice: 1.1, closingPrice: 1.12, openedAt: '2026-07-01T10:00:00Z', closedAt: '2026-07-03T14:30:00Z',
    openingCost: -5, closingCost: -4, profitLoss: 200, profitLossBase: 180, currencyConversionPl: -8,
}

describe('ClosedPositions', () => {
    it('renders nothing when there are no closed positions', () => {
        const {container} = render(<ClosedPositions positions={[]} currency="EUR"/>)
        expect(container).toBeEmptyDOMElement()
    })

    it('sums costs as positive spend and P/L keeping sign, in the base currency', () => {
        const second: ClosedPosition = {
            ...base, closedPositionId: '3-4', symbol: 'GBPUSD',
            openingCost: -3, closingCost: -6, profitLossBase: -30, currencyConversionPl: 2,
        }
        const {container} = render(<ClosedPositions positions={[base, second]} currency="EUR"/>)

        const kpi = (label: string) =>
            [...container.querySelectorAll('.acct-kpi')]
                .find((k) => k.querySelector('.acct-kpi-label')?.textContent === label)
                ?.querySelector('.acct-kpi-value')

        // Opening costs 5 + 3 = 8; closing costs 4 + 6 = 10 — shown as positive spend.
        expect(kpi('Opening costs')?.textContent).toMatch(/8\.00 EUR/)
        expect(kpi('Closing costs')?.textContent).toMatch(/10\.00 EUR/)
        // Currency conversion P/L: -8 + 2 = -6, coloured as a loss.
        expect(kpi('Currency conversion P/L')?.textContent).toMatch(/-6\.00 EUR/)
        expect(kpi('Currency conversion P/L')).toHaveClass('down')
        // Realised P/L: 180 + (-30) = 150, coloured as a gain.
        expect(kpi('Realised P/L')?.textContent).toMatch(/150\.00 EUR/)
        expect(kpi('Realised P/L')).toHaveClass('up')

        expect(screen.getByText('EURUSD')).toBeInTheDocument()
        expect(screen.getByText('GBPUSD')).toBeInTheDocument()
    })

    it('shows an em dash for the FX column when Saxo does not report conversion P/L', () => {
        const noFx: ClosedPosition = {...base, currencyConversionPl: null}
        const {container} = render(<ClosedPositions positions={[noFx]} currency="EUR"/>)

        const fxCell = within(container.querySelector('tbody tr')!).getAllByRole('cell')[5]
        expect(fxCell.textContent).toBe('—')
    })
})
