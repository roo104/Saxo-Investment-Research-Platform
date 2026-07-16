// Locale-aware number formatting. Every number rendered in the UI — live prices, chart axes and
// fundamentals alike — goes through here, so the decimal and grouping separators consistently
// follow the viewer's browser locale (`undefined` = the runtime default locale).

/** A decimal number with a fixed number of fraction digits (default 2). */
export function fmtDecimal(value: number, digits = 2): string {
    return value.toLocaleString(undefined, {minimumFractionDigits: digits, maximumFractionDigits: digits})
}

/** A ratio (0..1) rendered as a locale-aware percentage, e.g. 0.4855 -> "48.55%". */
export function fmtPercent(value: number, digits = 2): string {
    return value.toLocaleString(undefined, {
        style: 'percent',
        minimumFractionDigits: digits,
        maximumFractionDigits: digits,
    })
}

/** A short calendar date like "Jul 4" in the viewer's locale. */
export function fmtDate(iso: string): string {
    return new Date(iso).toLocaleDateString(undefined, {month: 'short', day: 'numeric'})
}

/** A calendar date including the year like "28 Mar 2016" — for spans measured in months/years. */
export function fmtDateYear(iso: string): string {
    return new Date(iso).toLocaleDateString(undefined, {year: 'numeric', month: 'short', day: 'numeric'})
}

/** A 24h clock time like "14:30" in the viewer's locale (never am/pm). */
export function fmtTime(iso: string): string {
    return new Date(iso).toLocaleTimeString(undefined, {hour: '2-digit', minute: '2-digit', hour12: false})
}

/**
 * Axis labels for a series of ISO timestamps, index-aligned with the input: intraday spans
 * (under 36h) get clock times, longer spans get calendar dates. Chart and oscillator panels share
 * this so a given point reads the same across the stacked timeline.
 */
export function axisTimeLabels(times: string[]): string[] {
    const spanMs = times.length > 1 ? Date.parse(times[times.length - 1]) - Date.parse(times[0]) : 0
    const fmt = spanMs > 0 && spanMs < 36 * 3600 * 1000 ? fmtTime : fmtDate
    return times.map(fmt)
}
