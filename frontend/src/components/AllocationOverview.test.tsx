import {render, screen, within} from '@testing-library/react'
import {describe, expect, it} from 'vitest'
import {AllocationOverview} from './AllocationOverview'
import type {PortfolioEntry} from '../types'

function entry(over: Partial<PortfolioEntry>): PortfolioEntry {
    return {
        id: 1, uic: 1, symbol: 'SYM:xnas', description: 'Something', assetType: 'Stock',
        quantity: 1, openingPrice: 100, sector: 'Technology', bid: null, ask: null, mid: null,
        currency: 'USD', marketState: null, delayedByMinutes: null, priceAvailable: false,
        lastClose: null, exchange: null, country: 'United States', marketOpen: null, ...over,
    }
}

const entries: PortfolioEntry[] = [
    entry({
        id: 1,
        assetType: 'Stock',
        country: 'United States',
        currency: 'USD',
        sector: 'Technology',
        quantity: 2,
        mid: 100
    }),
    entry({
        id: 2,
        assetType: 'Stock',
        country: 'Denmark',
        currency: 'DKK',
        sector: 'Healthcare',
        quantity: 1,
        mid: 100
    }),
    entry({id: 3, assetType: 'FxSpot', country: null, currency: 'USD', sector: null, quantity: 100, mid: 1}),
]

describe('AllocationOverview', () => {
    it('renders the four breakdown cards', () => {
        render(<AllocationOverview entries={entries}/>)
        expect(screen.getByRole('heading', {name: 'Assets'})).toBeInTheDocument()
        expect(screen.getByRole('heading', {name: 'Country'})).toBeInTheDocument()
        expect(screen.getByRole('heading', {name: 'Sector'})).toBeInTheDocument()
        expect(screen.getByRole('heading', {name: 'Currency'})).toBeInTheDocument()
    })

    it('weights the Assets legend by market value', () => {
        render(<AllocationOverview entries={entries}/>)
        // Stock 300 (75%) vs Currency 100 (25%) of a 400 total.
        const card = screen.getByRole('heading', {name: 'Assets'}).closest<HTMLElement>('.alloc-card')!
        expect(within(card).getByText('Stock')).toBeInTheDocument()
        expect(within(card).getByText('75.00%')).toBeInTheDocument()
        expect(within(card).getByText('Currency')).toBeInTheDocument()
        expect(within(card).getByText('25.00%')).toBeInTheDocument()
    })

    it('draws a donut segment per slice with an accessible summary', () => {
        render(<AllocationOverview entries={entries}/>)
        const donut = screen.getByRole('heading', {name: 'Country'}).closest<HTMLElement>('.alloc-card')!
            .querySelector('svg.donut')!
        expect(donut.getAttribute('aria-label')).toContain('United States 50.0%')
        // one track circle + one segment circle per slice (US, Denmark, Unknown)
        expect(donut.querySelectorAll('circle')).toHaveLength(4)
    })
})
