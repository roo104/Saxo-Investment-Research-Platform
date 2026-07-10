import {render} from '@testing-library/react'
import {describe, expect, it} from 'vitest'
import {PriceChart} from './PriceChart'
import type {PricePoint} from '../types'

const pts = (closes: number[]): PricePoint[] =>
    closes.map((c, i) => ({time: `2026-07-0${i + 1}T00:00:00Z`, open: c, high: c, low: c, close: c}))

describe('PriceChart', () => {
    it('draws a line path and shows the latest price and period change', () => {
        const {container} = render(<PriceChart points={pts([1.0, 1.1, 1.2])} currency="USD"/>)

        const line = container.querySelector('.chart-line')
        expect(line).not.toBeNull()
        expect(line!.getAttribute('d')).toMatch(/^M/)

        expect(container.querySelector('.chart-price')!.textContent).toContain('1.20')
        // +0.20 on a 1.00 base = +20%
        expect(container.querySelector('.chart-change')!.textContent).toContain('20.00%')
        expect(container.querySelector('.chart-change')!.className).toContain('up')
    })

    it('renders a fallback when there is too little data', () => {
        const {getByText} = render(<PriceChart points={pts([1.0])} currency={null}/>)
        expect(getByText(/not enough price history/i)).toBeInTheDocument()
    })

    it('renders candlesticks (no line) in candles mode', () => {
        const {container} = render(<PriceChart points={pts([1.0, 1.1, 1.2])} currency="USD" mode="candles"/>)
        expect(container.querySelector('.chart-line')).toBeNull()
        expect(container.querySelectorAll('.candle-body').length).toBe(3)
    })

    it('marks the last candle as the live tip when streaming', () => {
        const {container, getByText} = render(<PriceChart points={pts([1.0, 1.1, 1.25])} currency="USD" live/>)

        expect(container.querySelector('.chart-price')!.textContent).toContain('1.25')
        expect(getByText('LIVE')).toBeInTheDocument()
        expect(container.querySelector('.chart-dot-live')).not.toBeNull()
        // +0.25 on a 1.00 base = +25%
        expect(container.querySelector('.chart-change')!.textContent).toContain('25.00%')
    })
})
