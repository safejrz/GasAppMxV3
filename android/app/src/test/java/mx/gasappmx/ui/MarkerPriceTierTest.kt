package mx.gasappmx.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import mx.gasappmx.model.FuelType
import mx.gasappmx.model.GasStation

class MarkerPriceTierTest {

    // -------------------------------------------------------------------------
    // priceToTier — basic bucket boundaries
    // -------------------------------------------------------------------------

    @Test
    fun `priceToTier returns NONE when price is null`() {
        assertEquals(MarkerPriceTier.NONE, priceToTier(null, 20.0, 25.0))
    }

    @Test
    fun `priceToTier returns YELLOW when min equals max`() {
        assertEquals(MarkerPriceTier.YELLOW, priceToTier(23.0, 23.0, 23.0))
    }

    @Test
    fun `priceToTier returns GREEN for the minimum price`() {
        assertEquals(MarkerPriceTier.GREEN, priceToTier(20.0, 20.0, 25.0))
    }

    @Test
    fun `priceToTier returns RED for the maximum price`() {
        assertEquals(MarkerPriceTier.RED, priceToTier(25.0, 20.0, 25.0))
    }

    @Test
    fun `priceToTier assigns correct tier for each quintile`() {
        // Range 0–100 for easy math; bucket size = 20
        val min = 0.0
        val max = 100.0

        assertEquals(MarkerPriceTier.GREEN,        priceToTier(0.0,  min, max))   // t=0.00
        assertEquals(MarkerPriceTier.GREEN,        priceToTier(10.0, min, max))   // t=0.10
        assertEquals(MarkerPriceTier.YELLOW_GREEN, priceToTier(20.0, min, max))   // t=0.20
        assertEquals(MarkerPriceTier.YELLOW_GREEN, priceToTier(30.0, min, max))   // t=0.30
        assertEquals(MarkerPriceTier.YELLOW,       priceToTier(40.0, min, max))   // t=0.40
        assertEquals(MarkerPriceTier.YELLOW,       priceToTier(50.0, min, max))   // t=0.50
        assertEquals(MarkerPriceTier.ORANGE,       priceToTier(60.0, min, max))   // t=0.60
        assertEquals(MarkerPriceTier.ORANGE,       priceToTier(70.0, min, max))   // t=0.70
        assertEquals(MarkerPriceTier.RED,          priceToTier(80.0, min, max))   // t=0.80
        assertEquals(MarkerPriceTier.RED,          priceToTier(100.0,min, max))   // t=1.00
    }

    // -------------------------------------------------------------------------
    // stationPriceTiers — list-level tier assignment
    // -------------------------------------------------------------------------

    @Test
    fun `stationPriceTiers assigns NONE to station with no price for active fuel type`() {
        val stations = listOf(
            station("A", regular = null, premium = 25.0),
            station("B", regular = 23.0, premium = 24.0),
        )
        val tiers = stationPriceTiers(stations, FuelType.Regular)

        assertEquals(MarkerPriceTier.NONE, tiers["A"])
        // B is the only priced station → range is 0, so YELLOW
        assertEquals(MarkerPriceTier.YELLOW, tiers["B"])
    }

    @Test
    fun `stationPriceTiers assigns GREEN to cheapest and RED to most expensive`() {
        val stations = listOf(
            station("cheap",      regular = 20.0),
            station("expensive",  regular = 25.0),
        )
        val tiers = stationPriceTiers(stations, FuelType.Regular)

        assertEquals(MarkerPriceTier.GREEN, tiers["cheap"])
        assertEquals(MarkerPriceTier.RED,   tiers["expensive"])
    }

    @Test
    fun `stationPriceTiers all YELLOW when all prices are identical`() {
        val stations = listOf(
            station("X", regular = 23.49),
            station("Y", regular = 23.49),
            station("Z", regular = 23.49),
        )
        val tiers = stationPriceTiers(stations, FuelType.Regular)

        assertEquals(MarkerPriceTier.YELLOW, tiers["X"])
        assertEquals(MarkerPriceTier.YELLOW, tiers["Y"])
        assertEquals(MarkerPriceTier.YELLOW, tiers["Z"])
    }

    @Test
    fun `stationPriceTiers returns five distinct tiers for five evenly spaced prices`() {
        val stations = listOf(
            station("1", regular = 0.0),    // GREEN
            station("2", regular = 25.0),   // YELLOW_GREEN
            station("3", regular = 50.0),   // YELLOW
            station("4", regular = 75.0),   // ORANGE
            station("5", regular = 100.0),  // RED
        )
        val tiers = stationPriceTiers(stations, FuelType.Regular)

        assertEquals(MarkerPriceTier.GREEN,        tiers["1"])
        assertEquals(MarkerPriceTier.YELLOW_GREEN, tiers["2"])
        assertEquals(MarkerPriceTier.YELLOW,       tiers["3"])
        assertEquals(MarkerPriceTier.ORANGE,       tiers["4"])
        assertEquals(MarkerPriceTier.RED,          tiers["5"])
    }

    @Test
    fun `stationPriceTiers respects the active fuel type`() {
        val stations = listOf(
            station("A", regular = 20.0, premium = 99.0),
            station("B", regular = 25.0, premium = 20.0),
        )

        val regularTiers = stationPriceTiers(stations, FuelType.Regular)
        assertEquals(MarkerPriceTier.GREEN, regularTiers["A"])
        assertEquals(MarkerPriceTier.RED,   regularTiers["B"])

        val premiumTiers = stationPriceTiers(stations, FuelType.Premium)
        assertEquals(MarkerPriceTier.RED,   premiumTiers["A"])
        assertEquals(MarkerPriceTier.GREEN, premiumTiers["B"])
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun station(
        id: String,
        regular: Double? = null,
        premium: Double? = null,
        diesel: Double? = null,
    ): GasStation {
        val prices = buildMap<FuelType, Double> {
            regular?.let { put(FuelType.Regular, it) }
            premium?.let { put(FuelType.Premium, it) }
            diesel?.let  { put(FuelType.Diesel,  it) }
        }
        return GasStation(
            stationId = id,
            name = "Station $id",
            address = "Calle $id",
            latitude = 19.4,
            longitude = -99.1,
            distanceMeters = 500,
            prices = prices,
            lastUpdatedAt = "2026-05-28T00:00:00Z",
        )
    }
}
