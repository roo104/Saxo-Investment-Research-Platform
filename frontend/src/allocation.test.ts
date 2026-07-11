import {describe, expect, it} from 'vitest'
import {buildAllocation, positionValue} from './allocation'
import type {PortfolioEntry} from './types'

function entry(over: Partial<PortfolioEntry>): PortfolioEntry {
    return {
        id: 1,
        uic: 1,
        symbol: 'SYM:xnas',
        description: 'Something',
        assetType: 'Stock',
        quantity: 1,
        openingPrice: 100,
        bid: null,
        ask: null,
        mid: null,
        currency: 'USD',
        marketState: null,
        delayedByMinutes: null,
        priceAvailable: false,
        lastClose: null,
        exchange: null,
        country: 'United States',
        marketOpen: null,
        ...over,
    }
}

describe('positionValue', () => {
    it('prefers live mid over close and opening price', () => {
        expect(positionValue(entry({quantity: 2, mid: 10, lastClose: 5, openingPrice: 1}))).toBe(20)
    })

    it('falls back to last close, then opening price', () => {
        expect(positionValue(entry({quantity: 2, mid: null, lastClose: 5, openingPrice: 1}))).toBe(10)
        expect(positionValue(entry({quantity: 2, mid: null, lastClose: null, openingPrice: 3}))).toBe(6)
    })

    it('is zero when quantity or price is missing', () => {
        expect(positionValue(entry({quantity: null, mid: 10}))).toBe(0)
        expect(positionValue(entry({quantity: 2, mid: null, lastClose: null, openingPrice: null}))).toBe(0)
    })
})

describe('buildAllocation', () => {
    it('weights slices by market value and percentages sum to 1', () => {
        const {assets} = buildAllocation([
            entry({assetType: 'Stock', quantity: 1, mid: 75}),
            entry({assetType: 'FxSpot', quantity: 1, mid: 25}),
        ])
        expect(assets.slices.map((s) => s.label)).toEqual(['Stock', 'Currency'])
        expect(assets.slices[0].pct).toBeCloseTo(0.75)
        expect(assets.slices.reduce((sum, s) => sum + s.pct, 0)).toBeCloseTo(1)
    })

    it('groups country by listing exchange and orders slices largest-first', () => {
        const {country} = buildAllocation([
            entry({country: 'Denmark', quantity: 1, mid: 10}),
            entry({country: 'United States', quantity: 1, mid: 90}),
            entry({country: null, quantity: 1, mid: 5}),
        ])
        expect(country.slices.map((s) => s.label)).toEqual(['United States', 'Denmark', 'Unknown'])
    })

    it('rolls everything past the top five into a single Others slice', () => {
        const entries = ['A', 'B', 'C', 'D', 'E', 'F', 'G'].map((c, i) =>
            entry({currency: c, quantity: 1, mid: 100 - i}),
        )
        const {currency} = buildAllocation(entries)
        expect(currency.slices).toHaveLength(6)
        expect(currency.slices.at(-1)!.label).toBe('Others')
        // Others = F(95) + G(94) = 189 of a 679 total.
        expect(currency.slices.at(-1)!.value).toBe(189)
        expect(currency.slices.reduce((sum, s) => sum + s.pct, 0)).toBeCloseTo(1)
    })

    it('ignores positions with no value', () => {
        const {assets} = buildAllocation([
            entry({assetType: 'Stock', quantity: 1, mid: 50}),
            entry({assetType: 'Etf', quantity: null, mid: null, lastClose: null, openingPrice: null}),
        ])
        expect(assets.slices.map((s) => s.label)).toEqual(['Stock'])
    })
})
