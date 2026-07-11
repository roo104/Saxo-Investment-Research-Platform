package jp.saxo_investment_manager.market

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId

/**
 * Determines whether the exchange an instrument trades on is currently open.
 *
 * Saxo only returns a live MarketState on instruments the account is entitled to stream (FX in
 * simulation); equities come back with no quote at all, so their open/closed status must be derived
 * from the exchange's regular trading hours instead. This calendar does both: it prefers Saxo's
 * MarketState when present and otherwise falls back to a static table of regular sessions keyed by
 * the exchange (MIC) code carried in the Saxo symbol suffix, e.g. "AAPL:xnas" -> xnas.
 *
 * Only regular Monday-Friday sessions are modelled; public holidays and half-days are NOT accounted
 * for, so a market holiday reads as "open" until a holiday calendar is added. Intraday breaks (e.g.
 * the Tokyo/Hong Kong lunch) are treated as open — the window is first-open to last-close.
 */
@Component
class MarketCalendar(private val clock: Clock) {

    /** A regular daily trading session for an exchange, in the exchange's local time. */
    private data class Session(
        val name: String,
        val country: String,
        val zone: ZoneId,
        val open: LocalTime,
        val close: LocalTime,
    )

    private fun session(name: String, country: String, zone: String, open: String, close: String) =
        Session(name, country, ZoneId.of(zone), LocalTime.parse(open), LocalTime.parse(close))

    // Regular continuous-trading hours by exchange MIC code (lower-cased). The country is where the
    // exchange lists, not the issuer's domicile — Saxo exposes no domicile, so an ADR reads as its
    // listing country (e.g. a US-listed foreign company shows "United States").
    private val sessions: Map<String, Session> = mapOf(
        "xnas" to session("NASDAQ", "United States", "America/New_York", "09:30", "16:00"),
        "xnys" to session("NYSE", "United States", "America/New_York", "09:30", "16:00"),
        "arcx" to session("NYSE Arca", "United States", "America/New_York", "09:30", "16:00"),
        "bats" to session("Cboe BZX", "United States", "America/New_York", "09:30", "16:00"),
        "xtse" to session("Toronto", "Canada", "America/Toronto", "09:30", "16:00"),
        "xcse" to session("Nasdaq Copenhagen", "Denmark", "Europe/Copenhagen", "09:00", "16:55"),
        "xsto" to session("Nasdaq Stockholm", "Sweden", "Europe/Stockholm", "09:00", "17:30"),
        "xhel" to session("Nasdaq Helsinki", "Finland", "Europe/Helsinki", "10:00", "18:30"),
        "xosl" to session("Oslo Børs", "Norway", "Europe/Oslo", "09:00", "16:20"),
        "xlon" to session("London", "United Kingdom", "Europe/London", "08:00", "16:30"),
        "xetr" to session("Xetra", "Germany", "Europe/Berlin", "09:00", "17:30"),
        "xpar" to session("Euronext Paris", "France", "Europe/Paris", "09:00", "17:30"),
        "xams" to session("Euronext Amsterdam", "Netherlands", "Europe/Amsterdam", "09:00", "17:30"),
        "xbru" to session("Euronext Brussels", "Belgium", "Europe/Brussels", "09:00", "17:30"),
        "xlis" to session("Euronext Lisbon", "Portugal", "Europe/Lisbon", "08:00", "16:30"),
        "xmil" to session("Borsa Italiana", "Italy", "Europe/Rome", "09:00", "17:30"),
        "xmad" to session("Madrid", "Spain", "Europe/Madrid", "09:00", "17:30"),
        "xswx" to session("SIX Swiss", "Switzerland", "Europe/Zurich", "09:00", "17:30"),
        "xwbo" to session("Vienna", "Austria", "Europe/Vienna", "09:00", "17:30"),
        "xhkg" to session("Hong Kong", "Hong Kong", "Asia/Hong_Kong", "09:30", "16:00"),
        "xtks" to session("Tokyo", "Japan", "Asia/Tokyo", "09:00", "15:00"),
        "xjpx" to session("Tokyo", "Japan", "Asia/Tokyo", "09:00", "15:00"),
        "xses" to session("Singapore", "Singapore", "Asia/Singapore", "09:00", "17:00"),
        "xasx" to session("ASX", "Australia", "Australia/Sydney", "10:00", "16:00"),
    )

    /** The exchange (MIC) code from a Saxo symbol like "AAPL:xnas", or null for symbols without one. */
    fun exchangeCode(symbol: String): String? =
        symbol.substringAfter(':', "").lowercase().ifBlank { null }

    /** A human-readable exchange name for display, or null when the exchange is unknown. */
    fun exchangeName(symbol: String): String? =
        exchangeCode(symbol)?.let { sessions[it]?.name }

    /** The country of the instrument's listing exchange, or null when the exchange is unknown. */
    fun country(symbol: String): String? =
        exchangeCode(symbol)?.let { sessions[it]?.country }

    /**
     * Whether the instrument's market is open right now. Prefers Saxo's live [marketState] when it
     * is present ("Open" vs anything else); otherwise derives it from the exchange's regular hours.
     * Returns null when neither source can answer (no market state and an unknown exchange).
     */
    fun isOpen(symbol: String, marketState: String?): Boolean? {
        marketState?.takeIf { it.isNotBlank() }?.let { return it.equals("Open", ignoreCase = true) }
        val session = exchangeCode(symbol)?.let { sessions[it] } ?: return null
        val now = clock.instant().atZone(session.zone)
        if (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) return false
        val t = now.toLocalTime()
        return !t.isBefore(session.open) && t.isBefore(session.close)
    }
}
