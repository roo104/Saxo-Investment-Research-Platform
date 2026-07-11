package jp.saxo_investment_manager.api

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "A single key-stat label/value pair, e.g. 'Price / Earnings (LTM)' → '36,08'.")
data class KeyStat(val label: String, val value: String)

@Schema(description = "A financials row: a label and one value per period (null renders as '–').")
data class FinancialRow(val label: String, val values: List<String?>)

@Schema(description = "A group of financials rows under a heading, e.g. 'Income statement'.")
data class FinancialSection(val title: String, val rows: List<FinancialRow>)

@Schema(description = "A financials table: period column headers plus grouped rows.")
data class FinancialStatements(val periods: List<String>, val sections: List<FinancialSection>)

@Schema(description = "Company fundamentals for an instrument (key stats + financials).")
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
