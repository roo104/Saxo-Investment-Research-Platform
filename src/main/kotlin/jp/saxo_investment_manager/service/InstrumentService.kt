package jp.saxo_investment_manager.service

import jp.saxo_investment_manager.api.InstrumentDto
import jp.saxo_investment_manager.saxo.InstrumentSummary
import jp.saxo_investment_manager.saxo.ReferenceDataClient
import org.springframework.stereotype.Service

/** Searches Saxo for instruments and maps them into the public API contract. */
@Service
class InstrumentService(private val referenceDataClient: ReferenceDataClient) {

    suspend fun search(keywords: String?, assetTypes: String?, exchangeId: String?): List<InstrumentDto> =
        referenceDataClient.searchInstruments(keywords, toSaxoAssetTypes(assetTypes), exchangeId).map { it.toDto() }
}

/**
 * Translates the app's friendly asset-type names into Saxo's canonical OpenAPI values before
 * they hit reference data. We expose "Currency" to users; Saxo calls foreign exchange "FxSpot".
 */
private fun toSaxoAssetTypes(assetTypes: String?): String? =
    assetTypes?.split(',')?.joinToString(",") { token ->
        when (token.trim().lowercase()) {
            "currency" -> "FxSpot"
            else -> token.trim()
        }
    }

private fun InstrumentSummary.toDto() = InstrumentDto(
    uic = uic,
    symbol = symbol,
    description = description,
    assetType = assetType,
    exchangeId = exchangeId,
    currencyCode = currencyCode,
)
