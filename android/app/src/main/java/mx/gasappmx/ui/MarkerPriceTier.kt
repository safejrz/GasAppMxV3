package mx.gasappmx.ui

import mx.gasappmx.model.FuelType
import mx.gasappmx.model.GasStation

/**
 * Color tier for a price-label map marker, derived from how expensive a station's
 * price is relative to the cheapest and most expensive station in the current visible set.
 *
 * Tier boundaries use linear (equal-width) buckets over the observed min–max price range:
 *   [0%, 20%) → GREEN   — cheapest quintile
 *   [20%, 40%) → YELLOW_GREEN
 *   [40%, 60%) → YELLOW
 *   [60%, 80%) → ORANGE
 *   [80%, 100%] → RED   — most expensive quintile
 *
 * When all visible prices are identical, every marker receives YELLOW (mid-tier neutral).
 * When a station has no price for the selected fuel type it receives NONE (grey).
 *
 * Colors are stored as raw ARGB [Int] constants so this enum has no Android framework
 * dependency and can be used safely in JVM unit tests.
 */
@Suppress("MagicNumber")
enum class MarkerPriceTier(
    /** ARGB fill color for the bubble background. */
    val fillColor: Int,
    /** ARGB color for the price text drawn inside the bubble. */
    val textColor: Int,
) {
    GREEN(       fillColor = 0xFF27AE60.toInt(), textColor = 0xFFFFFFFF.toInt()),
    YELLOW_GREEN(fillColor = 0xFF82C341.toInt(), textColor = 0xFFFFFFFF.toInt()),
    YELLOW(      fillColor = 0xFFF1C40F.toInt(), textColor = 0xFF212121.toInt()),
    ORANGE(      fillColor = 0xFFE67E22.toInt(), textColor = 0xFFFFFFFF.toInt()),
    RED(         fillColor = 0xFFE74C3C.toInt(), textColor = 0xFFFFFFFF.toInt()),
    NONE(        fillColor = 0xFF9E9E9E.toInt(), textColor = 0xFFFFFFFF.toInt()),
}

/**
 * Returns the [MarkerPriceTier] for a single [price] given the [min] and [max] prices
 * in the currently visible station list.
 *
 * @param price  The station's price for the active fuel type, or null if unavailable.
 * @param min    The minimum price in the visible list (ignored when price is null).
 * @param max    The maximum price in the visible list (ignored when price is null).
 */
fun priceToTier(price: Double?, min: Double, max: Double): MarkerPriceTier {
    if (price == null) return MarkerPriceTier.NONE
    if (min >= max) return MarkerPriceTier.YELLOW  // all prices identical → neutral
    val t = (price - min) / (max - min)  // 0.0 = cheapest, 1.0 = most expensive
    return when {
        t < 0.2 -> MarkerPriceTier.GREEN
        t < 0.4 -> MarkerPriceTier.YELLOW_GREEN
        t < 0.6 -> MarkerPriceTier.YELLOW
        t < 0.8 -> MarkerPriceTier.ORANGE
        else    -> MarkerPriceTier.RED
    }
}

/**
 * Computes a [MarkerPriceTier] for every station in [stations] relative to the
 * minimum and maximum price observed across the whole list for [fuelType].
 *
 * Returns a map keyed by [GasStation.stationId].  Stations without a price for
 * [fuelType] are mapped to [MarkerPriceTier.NONE].
 */
fun stationPriceTiers(
    stations: List<GasStation>,
    fuelType: FuelType,
): Map<String, MarkerPriceTier> {
    val prices = stations.map { it.prices[fuelType] }
    val knownPrices = prices.filterNotNull()
    val min = knownPrices.minOrNull() ?: 0.0
    val max = knownPrices.maxOrNull() ?: 0.0

    return stations.zip(prices).associate { (station, price) ->
        station.stationId to priceToTier(price, min, max)
    }
}
