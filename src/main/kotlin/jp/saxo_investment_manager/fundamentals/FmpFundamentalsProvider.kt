package jp.saxo_investment_manager.fundamentals

import jp.saxo_investment_manager.api.FinancialRow
import jp.saxo_investment_manager.api.FinancialSection
import jp.saxo_investment_manager.api.FinancialStatements
import jp.saxo_investment_manager.api.Fundamentals
import jp.saxo_investment_manager.api.KeyStat
import jp.saxo_investment_manager.api.StatUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.JsonNode

/**
 * Real fundamentals from [Financial Modeling Prep](https://financialmodelingprep.com) (stable API).
 *
 * This is the only [FundamentalsProvider]: fundamentals are always live data or an error — the app
 * never serves mock/sample figures. `FMP_API_KEY` is mandatory: if it is missing the application
 * **fails to start** (see the [init] check). If FMP doesn't recognise the instrument's ticker, the
 * request fails with a clear HTTP error rather than falling back.
 */
@Component
class FmpFundamentalsProvider(
    private val properties: FmpProperties,
) : FundamentalsProvider {
    private val log = LoggerFactory.getLogger(javaClass)
    private val equityTypes = setOf("Stock", "CfdOnStock")
    private val client: WebClient by lazy { WebClient.builder().baseUrl(properties.baseUrl).build() }

    init {
        require(properties.apiKey.isNotBlank()) {
            "FMP_API_KEY is not configured. Fundamentals serve live data only (no mock fallback), so a key " +
                    "is mandatory — get a free one at https://financialmodelingprep.com and set FMP_API_KEY."
        }
    }

    // Best-effort Saxo-symbol → FMP-ticker mapping. US listings map cleanly; some venues get a
    // suffix; anything FMP doesn't recognise fails as "no fundamentals for this instrument".
    private val exchangeSuffix = mapOf(
        "xcse" to ".CO", "xetr" to ".DE", "xlon" to ".L", "xpar" to ".PA", "xmil" to ".MI",
        "xams" to ".AS", "xhel" to ".HE", "xsto" to ".ST", "xosl" to ".OL", "xswx" to ".SW",
        "xtse" to ".TO", "xhkg" to ".HK", "xtks" to ".T",
    )

    override suspend fun fundamentals(uic: Long, assetType: String, symbol: String, name: String): Fundamentals {
        if (assetType !in equityTypes) {
            val empty = FinancialStatements(emptyList(), emptyList())
            return Fundamentals(symbol, name, "USD", available = false, emptyList(), empty, empty)
        }
        val ticker = toTicker(symbol)
        return fetch(ticker, symbol, name)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No fundamentals for $symbol (FMP ticker $ticker).")
    }

    private fun toTicker(symbol: String): String {
        val base = normalizeShareClass(symbol.substringBefore(':'))
        val venue = symbol.substringAfter(':', "").lowercase()
        return base + (exchangeSuffix[venue] ?: "")
    }

    // Saxo appends the share class as a trailing lowercase letter (NOVOb, MAERSKb, CARLb); FMP
    // expects it hyphenated and uppercased (NOVO-B, MAERSK-B, CARL-B). Only a lone lowercase letter
    // following an uppercase one is treated as a share class, so plain US tickers (AAPL) are left
    // untouched.
    private fun normalizeShareClass(base: String): String {
        if (base.length < 2) return base
        val cls = base.last()
        val prev = base[base.length - 2]
        return if (cls in 'a'..'z' && prev in 'A'..'Z') "${base.dropLast(1)}-${cls.uppercaseChar()}" else base
    }

    private suspend fun fetch(ticker: String, symbol: String, name: String): Fundamentals? = coroutineScope {
        val quoteD = async { get("/stable/quote", "symbol" to ticker) }
        val ratiosTtmD = async { get("/stable/ratios-ttm", "symbol" to ticker) }
        val keyTtmD = async { get("/stable/key-metrics-ttm", "symbol" to ticker) }
        val incomeYD =
            async { get("/stable/income-statement", "symbol" to ticker, "period" to "annual", "limit" to "3") }
        val balanceYD =
            async { get("/stable/balance-sheet-statement", "symbol" to ticker, "period" to "annual", "limit" to "3") }
        val ratiosYD = async { get("/stable/ratios", "symbol" to ticker, "period" to "annual", "limit" to "3") }
        val incomeQD =
            async { get("/stable/income-statement", "symbol" to ticker, "period" to "quarter", "limit" to "4") }
        val balanceQD =
            async { get("/stable/balance-sheet-statement", "symbol" to ticker, "period" to "quarter", "limit" to "4") }
        val ratiosQD = async { get("/stable/ratios", "symbol" to ticker, "period" to "quarter", "limit" to "4") }

        val income = incomeYD.await()
        if (income == null || !income.isArray || income.isEmpty) return@coroutineScope null // unknown ticker → 404

        val quote = firstObj(quoteD.await())
        val ttm = firstObj(ratiosTtmD.await())
        val key = firstObj(keyTtmD.await())
        val ccy = income[0].str("reportedCurrency") ?: "USD"

        // The stable /quote no longer carries eps/pe — those come from the TTM ratios endpoint now.
        // Values are raw numbers tagged with a unit; the client localises and formats them.
        val keyStats = buildList {
            add(ttm?.dbl("netIncomePerShareTTM"), "Earnings Per Share (LTM)", StatUnit.RATIO)
            add(ttm?.dbl("priceToEarningsRatioTTM"), "Price / Earnings (LTM)", StatUnit.RATIO)
            add(ttm?.dbl("priceToBookRatioTTM"), "Price / Book Value (MRQ)", StatUnit.RATIO)
            add(ttm?.dbl("dividendYieldTTM"), "Dividend Yield (LTM)", StatUnit.PERCENT)
            add(ttm?.dbl("dividendPayoutRatioTTM"), "Dividend Payout Ratio (LTM)", StatUnit.PERCENT)
            add(key?.dbl("evToEBITDATTM"), "Enterprise Value / EBITDA (MRQ / LTM)", StatUnit.RATIO)
        }

        Fundamentals(
            symbol = symbol,
            name = quote?.str("name") ?: name,
            currency = ccy,
            available = true,
            keyStats = keyStats,
            perYear = statements(income, balanceYD.await(), ratiosYD.await()) { it.str("fiscalYear") ?: "" },
            perQuarter = statements(incomeQD.await(), balanceQD.await(), ratiosQD.await()) {
                "${it.str("period") ?: ""} ${it.str("fiscalYear") ?: ""}".trim()
            },
        )
    }

    private fun statements(
        income: JsonNode?,
        balance: JsonNode?,
        ratios: JsonNode?,
        label: (JsonNode) -> String,
    ): FinancialStatements {
        val incomeRows = income?.toList() ?: emptyList()
        val balanceByYear = (balance?.toList() ?: emptyList()).associateBy { it.str("date") }
        val ratiosByYear = (ratios?.toList() ?: emptyList()).associateBy { it.str("date") }
        // Align balance/ratios to the income periods by statement date.
        val aligned = incomeRows.map { inc ->
            Triple(inc, balanceByYear[inc.str("date")], ratiosByYear[inc.str("date")])
        }
        return FinancialStatements(
            periods = aligned.map { (i, _, _) -> label(i) },
            sections = listOf(
                FinancialSection(
                    "Income statement",
                    listOf(
                        FinancialRow("Revenue", StatUnit.MONEY_BILLIONS, aligned.map { (i, _, _) -> i.dbl("revenue") }),
                        FinancialRow("EBITDA", StatUnit.MONEY_BILLIONS, aligned.map { (i, _, _) -> i.dbl("ebitda") }),
                        FinancialRow(
                            "Net Income",
                            StatUnit.MONEY_BILLIONS,
                            aligned.map { (i, _, _) -> i.dbl("netIncome") }),
                    ),
                ),
                FinancialSection(
                    "Balance sheet",
                    listOf(
                        FinancialRow(
                            "Total Assets",
                            StatUnit.MONEY_BILLIONS,
                            aligned.map { (_, b, _) -> b?.dbl("totalAssets") }),
                        FinancialRow(
                            "Total Debt",
                            StatUnit.MONEY_BILLIONS,
                            aligned.map { (_, b, _) -> b?.dbl("totalDebt") }),
                    ),
                ),
                FinancialSection(
                    "Ratios",
                    listOf(
                        FinancialRow(
                            "Price / Sales",
                            StatUnit.RATIO,
                            aligned.map { (_, _, r) -> r?.dbl("priceToSalesRatio") }),
                        FinancialRow("Earnings Per Share", StatUnit.MONEY, aligned.map { (i, _, _) -> i.dbl("eps") }),
                        FinancialRow("Return on equity", StatUnit.PERCENT, aligned.map { (i, b, _) -> roe(i, b) }),
                    ),
                ),
            ),
        )
    }

    // Each endpoint is fetched independently: a data-level HTTP error on one (unknown ticker → 404,
    // free-tier throttling → 429, symbols gated to a paid plan such as non-US listings → 402) returns
    // null instead of throwing — the caller then surfaces a clean 404. Throwing here would propagate
    // out of its `async` and cancel the sibling requests mid-flight, which Reactor Netty logs as a
    // noisy "Rejecting additional inbound receiver" / onErrorDropped. A null income array is treated
    // as "unknown ticker" by the caller and surfaced as a clean 404.
    //
    // Auth failures (401/403 — a wrong or revoked FMP_API_KEY) are the exception: they are a
    // misconfiguration, not missing data, so they surface loudly rather than masquerading as "no
    // fundamentals for this instrument".
    private suspend fun get(path: String, vararg query: Pair<String, String>): JsonNode? =
        try {
            client.get()
                .uri { b ->
                    b.path(path)
                    query.forEach { b.queryParam(it.first, it.second) }
                    b.queryParam("apikey", properties.apiKey).build()
                }
                .retrieve()
                .awaitBody<JsonNode>()
        } catch (e: WebClientResponseException.Unauthorized) {
            throw authFailure(path, e)
        } catch (e: WebClientResponseException.Forbidden) {
            throw authFailure(path, e)
        } catch (e: WebClientResponseException) {
            log.debug("FMP {} failed: {} {}", path, e.statusCode, e.statusText)
            null
        }

    // A wrong/revoked FMP_API_KEY is a misconfiguration, not missing data, so log it loudly (WARN)
    // in addition to surfacing it as a 502 — the HTTP access log alone doesn't say *why* it failed.
    private fun authFailure(path: String, e: WebClientResponseException): ResponseStatusException {
        log.warn("FMP rejected the API key on {} ({} {}). Check FMP_API_KEY.", path, e.statusCode, e.statusText)
        return ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "FMP rejected the API key (${e.statusCode}). Check FMP_API_KEY.",
            e,
        )
    }

    private fun firstObj(node: JsonNode?): JsonNode? = node?.takeIf { it.isArray && !it.isEmpty }?.get(0)

    // The stable /ratios endpoint no longer returns returnOnEquity, so derive it from the statements
    // we already fetch: net income ÷ shareholders' equity for the same period.
    private fun roe(income: JsonNode, balance: JsonNode?): Double? {
        val netIncome = income.dbl("netIncome") ?: return null
        val equity = balance?.dbl("totalStockholdersEquity")?.takeIf { it != 0.0 } ?: return null
        return netIncome / equity
    }

    private fun JsonNode.dbl(vararg fields: String): Double? =
        fields.firstNotNullOfOrNull { f -> get(f)?.takeIf { !it.isNull && it.isNumber }?.asDouble() }

    private fun JsonNode.str(field: String): String? = get(field)?.takeIf { !it.isNull }?.asString()

    /** Adds a [KeyStat] only when the value is present. */
    private fun MutableList<KeyStat>.add(value: Double?, label: String, unit: StatUnit) {
        if (value != null) add(KeyStat(label, value, unit))
    }
}
