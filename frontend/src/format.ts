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
