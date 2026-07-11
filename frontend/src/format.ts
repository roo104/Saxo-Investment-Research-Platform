// Locale-aware number formatting. Every number rendered in the UI — live prices, chart axes and
// fundamentals alike — goes through here, so the decimal and grouping separators consistently
// follow the viewer's browser locale (`undefined` = the runtime default locale).

/** A decimal number with a fixed number of fraction digits (default 2). */
export function fmtDecimal(value: number, digits = 2): string {
    return value.toLocaleString(undefined, {minimumFractionDigits: digits, maximumFractionDigits: digits})
}
