package jp.saxo_investment_manager.api

import io.swagger.v3.oas.annotations.media.Schema

/**
 * How a raw numeric fundamentals value should be rendered. The server sends numbers, not
 * formatted strings, so the client can localise the decimal/grouping separators to the viewer's
 * locale (keeping fundamentals consistent with live prices, which are localised the same way).
 */
@Schema(description = "How a numeric fundamentals value should be rendered by the client (locale-aware).")
enum class StatUnit {
    @Schema(description = "Plain ratio, 2 decimals (e.g. P/E 36.08).")
    RATIO,

    @Schema(description = "A fraction to render as a percentage, 2 decimals (0.3608 → 36.08%).")
    PERCENT,

    @Schema(description = "An absolute money amount, 2 decimals, suffixed with the currency.")
    MONEY,

    @Schema(description = "An absolute money amount rendered in billions (e.g. 1.5bn USD).")
    MONEY_BILLIONS,
}

@Schema(description = "A single key stat: a label, a raw numeric value, and how to render it, e.g. 'Price / Earnings (LTM)' → 36.08 RATIO.")
data class KeyStat(val label: String, val value: Double, val unit: StatUnit)

@Schema(description = "A financials row: a label, a rendering unit, and one raw value per period (null renders as '–').")
data class FinancialRow(val label: String, val unit: StatUnit, val values: List<Double?>)

@Schema(description = "A group of financials rows under a heading, e.g. 'Income statement'.")
data class FinancialSection(val title: String, val rows: List<FinancialRow>)

@Schema(description = "A financials table: period column headers plus grouped rows.")
data class FinancialStatements(val periods: List<String>, val sections: List<FinancialSection>)

@Schema(description = "Company fundamentals for an instrument (key stats + financials). Money values are all in [currency].")
data class Fundamentals(
    val symbol: String,
    val name: String,
    val currency: String,
    @get:Schema(description = "False when fundamentals don't apply to this instrument type (e.g. FX).")
    val available: Boolean,
    val keyStats: List<KeyStat>,
    val perYear: FinancialStatements,
    val perQuarter: FinancialStatements,
)
