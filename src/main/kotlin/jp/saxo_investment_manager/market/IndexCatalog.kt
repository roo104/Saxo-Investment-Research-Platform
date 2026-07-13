package jp.saxo_investment_manager.market

/** The region a market index belongs to; used to group and colour the 24h overview chart. */
enum class IndexRegion(val label: String) {
    AMERICAS("Americas"),
    EUROPE("Europe"),
    ASIA("Asia"),
}

/**
 * A headline market index to plot on the 24h overview chart, plus how to resolve it against Saxo.
 *
 * Saxo exposes no catalogue of indices; the ones it offers are index CFDs (symbols ending `.I`).
 * Each is resolved by its stable Saxo [symbol] against `GET /ref/v1/instruments` — matching the
 * symbol keeps the mapping portable across the simulation and live environments, where the numeric
 * UIC can differ. [sessionMic] is the underlying cash exchange's MIC, whose regular hours decide the
 * open/closed state (see [MarketCalendar]); the CFD itself trades nearly around the clock, but we
 * want "is the market this tracks open right now".
 */
data class MarketIndex(
    val key: String,
    val name: String,
    val region: IndexRegion,
    val symbol: String,
    val sessionMic: String,
)

/**
 * The indices shown on the overview chart, grouped Americas → Europe → Asia. Chosen from what Saxo
 * simulation actually offers: there is no Nasdaq Composite (US Tech 100 stands in), no broad STOXX
 * 600 (Germany 40 stands in) and no Taiwan index (Australia 200 stands in). The resolver skips any
 * symbol Saxo can't match in the active environment, so the response degrades gracefully.
 */
object IndexCatalog {
    val indices: List<MarketIndex> = listOf(
        MarketIndex("spx", "S&P 500", IndexRegion.AMERICAS, "US500.I", "xnys"),
        MarketIndex("nas100", "Nasdaq 100", IndexRegion.AMERICAS, "USNAS100.I", "xnas"),
        MarketIndex("dji", "Dow Jones", IndexRegion.AMERICAS, "US30.I", "xnys"),
        MarketIndex("ger40", "Germany 40", IndexRegion.EUROPE, "GER40.I", "xetr"),
        MarketIndex("c25", "OMX C25", IndexRegion.EUROPE, "DEN25.I", "xcse"),
        MarketIndex("n225", "Nikkei 225", IndexRegion.ASIA, "JP225.I", "xtks"),
        MarketIndex("hsi", "Hang Seng", IndexRegion.ASIA, "HK50.I", "xhkg"),
        MarketIndex("aus200", "Australia 200", IndexRegion.ASIA, "AUS200.I", "xasx"),
    )
}
