// Friendly, user-facing asset-type names. Saxo's OpenAPI uses its own values
// ("Stock", "Etf", "FxSpot", …); we present the common terms and translate at the
// boundaries — the backend maps "Currency" back to "FxSpot" for Saxo search requests.

export const ASSET_FILTERS = ['Stock', 'ETF', 'Currency'] as const
export type AssetFilter = (typeof ASSET_FILTERS)[number]

// Raw Saxo asset types (lower-cased) grouped under each friendly category.
const CATEGORIES: Record<AssetFilter, string[]> = {
    Stock: ['stock', 'cfdonstock'],
    ETF: ['etf', 'etc', 'etn'],
    Currency: ['fxspot'],
}

/** True when a raw Saxo asset type belongs to the given friendly filter. */
export function inCategory(assetType: string, filter: AssetFilter): boolean {
    return CATEGORIES[filter].includes(assetType.toLowerCase())
}

/** A friendly label for a raw Saxo asset type, so users never see "FxSpot" and friends. */
export function assetLabel(assetType: string): string {
    const type = assetType.toLowerCase()
    const category = ASSET_FILTERS.find((c) => CATEGORIES[c].includes(type))
    return category ?? assetType
}
