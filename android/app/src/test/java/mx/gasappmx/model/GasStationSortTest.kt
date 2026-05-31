package mx.gasappmx.model

import kotlin.test.Test
import kotlin.test.assertEquals

class GasStationSortTest {
    @Test
    fun `sorts by price first and distance second`() {
        val stations = listOf(
            GasStation(
                stationId = "1",
                name = "A",
                address = "Dir A",
                latitude = 19.0,
                longitude = -99.0,
                distanceMeters = 700,
                prices = mapOf(FuelType.Regular to 23.20),
                lastUpdatedAt = "2026-05-12T17:00:00Z",
            ),
            GasStation(
                stationId = "2",
                name = "B",
                address = "Dir B",
                latitude = 19.0,
                longitude = -99.0,
                distanceMeters = 400,
                prices = mapOf(FuelType.Regular to 23.20),
                lastUpdatedAt = "2026-05-12T17:00:00Z",
            ),
            GasStation(
                stationId = "3",
                name = "C",
                address = "Dir C",
                latitude = 19.0,
                longitude = -99.0,
                distanceMeters = 900,
                prices = mapOf(FuelType.Regular to 22.90),
                lastUpdatedAt = "2026-05-12T17:00:00Z",
            ),
        )

        val sortedIds = stations.sortForFuelType(FuelType.Regular).map { it.stationId }

        assertEquals(listOf("3", "2", "1"), sortedIds)
    }
}
