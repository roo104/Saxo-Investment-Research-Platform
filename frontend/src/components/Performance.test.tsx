import {render, screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {Performance} from './Performance'
import {api} from '../api'
import type {Performance as PerformanceData} from '../types'

vi.mock('../api', () => ({api: {getPerformance: vi.fn()}}))

const gaining: PerformanceData = {
    period: 'Year', available: true, startValue: 100000, endValue: 117500,
    absoluteReturn: 17500, returnPct: 0.175,
    points: [
        {date: '2026-01-01', value: 100000},
        {date: '2026-04-01', value: 108000},
        {date: '2026-07-15', value: 117500},
    ],
}

describe('Performance', () => {
    beforeEach(() => vi.clearAllMocks())

    it('renders the return headline and a gaining value curve', async () => {
        vi.mocked(api.getPerformance).mockResolvedValue(gaining)

        const {container} = render(<Performance currency="USD"/>)

        const kpi = (label: string) =>
            [...container.querySelectorAll('.acct-kpi')]
                .find((k) => k.querySelector('.acct-kpi-label')?.textContent === label)
                ?.querySelector('.acct-kpi-value')

        await waitFor(() => expect(kpi('Return')?.textContent).toBe('+17.50%'))
        expect(kpi('Return')).toHaveClass('up')
        expect(kpi('Change')?.textContent).toMatch(/17,?500\.00 USD/)
        expect(kpi('Current value')?.textContent).toMatch(/117,?500\.00 USD/)

        // A rising period draws the curve in the gain colour.
        expect(container.querySelector('.perf-chart.up')).not.toBeNull()
        // Defaults to the 1-year period.
        expect(api.getPerformance).toHaveBeenCalledWith('Year')
    })

    it('refetches with the selected StandardPeriod when the period changes', async () => {
        vi.mocked(api.getPerformance).mockResolvedValue(gaining)

        render(<Performance currency="USD"/>)
        await waitFor(() => expect(api.getPerformance).toHaveBeenCalledWith('Year'))

        await userEvent.click(screen.getByRole('button', {name: '3M'}))

        await waitFor(() => expect(api.getPerformance).toHaveBeenCalledWith('Quarter'))
    })

    it('shows an honest empty state, not a flat zero line, when the curve has no value', async () => {
        // A dead/unfunded account: Saxo returns points, but all at value 0 (available=false).
        vi.mocked(api.getPerformance).mockResolvedValue({
            period: 'AllTime', available: false, startValue: 0, endValue: 0,
            absoluteReturn: 0, returnPct: 0,
            points: [{date: '2016-03-28', value: 0}, {date: '2016-03-29', value: 0}],
        })

        const {container} = render(<Performance currency="USD"/>)

        await waitFor(() => expect(screen.getByText(/No performance history/i)).toBeInTheDocument())
        // Neither the flat curve nor the misleading "+0.00%" KPIs are shown.
        expect(container.querySelector('.perf-chart')).toBeNull()
        expect(container.querySelector('.acct-kpi')).toBeNull()
    })

    it('surfaces an error banner when the request fails', async () => {
        vi.mocked(api.getPerformance).mockRejectedValue(new Error('boom'))

        render(<Performance currency="USD"/>)

        await waitFor(() => expect(screen.getByText(/Could not load performance data/i)).toBeInTheDocument())
    })
})
