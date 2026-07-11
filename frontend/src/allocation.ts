// Aggregates the portfolio into the three allocation breakdowns rendered by the overview donuts:
// Assets (asset class), Country (listing exchange) and Currency. Each position is weighted by its
// market value — quantity times the best price we have (live mid, then last daily close, then the
// opening price), so equities that can't be quoted in Saxo sim still contribute a sensible value.
//
// Values are summed in each position's *native* currency without FX conversion; the Currency donut
// makes that spread explicit. Base-currency normalisation is a deliberate follow-up.

import type {PortfolioEntry} from './types'
import {assetLabel} from './assetTypes'

export interface AllocationSlice {
    label: string
    value: number
    /** Fraction of the breakdown total, 0..1. */
    pct: number
    /** A CSS colour (custom-property reference) for the donut segment and legend swatch. */
    color: string
}

export interface Breakdown {
    title: string
    slices: AllocationSlice[]
}

export interface Allocation {
    assets: Breakdown
    country: Breakdown
    currency: Breakdown
    total: number
}

// The largest N groups are shown by name; the remainder collapse into a single "Others" slice.
const TOP_N = 5
const OTHERS_LABEL = 'Others'

// Categorical palette, applied by descending rank so the same colour means "biggest slice" across
// every donut. "Others" always gets its own muted grey. Defined in styles.css.
const PALETTE = ['var(--cat-1)', 'var(--cat-2)', 'var(--cat-3)', 'var(--cat-4)', 'var(--cat-5)']
const OTHERS_COLOR = 'var(--cat-others)'

/** Market value of a position: quantity × the best price available, or 0 when nothing is known. */
export function positionValue(entry: PortfolioEntry): number {
    const price = entry.mid ?? entry.lastClose ?? entry.openingPrice
    if (price == null || entry.quantity == null) return 0
    return entry.quantity * price
}

/** Sums position values by key, then ranks them into named slices plus an "Others" rollup. */
function breakdown(title: string, entries: PortfolioEntry[], keyOf: (e: PortfolioEntry) => string): Breakdown {
    const totals = new Map<string, number>()
    for (const entry of entries) {
        const value = positionValue(entry)
        if (value <= 0) continue
        totals.set(keyOf(entry), (totals.get(keyOf(entry)) ?? 0) + value)
    }

    const ranked = [...totals.entries()]
        .map(([label, value]) => ({label, value}))
        .sort((a, b) => b.value - a.value)

    const total = ranked.reduce((sum, s) => sum + s.value, 0)

    const named = ranked.slice(0, TOP_N)
    const rest = ranked.slice(TOP_N)
    const restValue = rest.reduce((sum, s) => sum + s.value, 0)

    const slices: AllocationSlice[] = named.map((s, i) => ({
        label: s.label,
        value: s.value,
        pct: total > 0 ? s.value / total : 0,
        color: PALETTE[i % PALETTE.length],
    }))
    if (restValue > 0) {
        slices.push({
            label: OTHERS_LABEL,
            value: restValue,
            pct: total > 0 ? restValue / total : 0,
            color: OTHERS_COLOR
        })
    }

    return {title, slices}
}

/** Builds all three breakdowns from the current portfolio entries. */
export function buildAllocation(entries: PortfolioEntry[]): Allocation {
    return {
        assets: breakdown('Assets', entries, (e) => assetLabel(e.assetType)),
        country: breakdown('Country', entries, (e) => e.country ?? 'Unknown'),
        currency: breakdown('Currency', entries, (e) => e.currency ?? 'Unknown'),
        total: entries.reduce((sum, e) => sum + positionValue(e), 0),
    }
}
