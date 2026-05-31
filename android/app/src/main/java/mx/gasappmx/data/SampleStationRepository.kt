package mx.gasappmx.data

import mx.gasappmx.model.FuelType
import mx.gasappmx.model.GasStation
import mx.gasappmx.model.ResultLimit
import mx.gasappmx.model.sortForFuelType

/** In-memory fixtures for UI development without network/auth. Swap into the ViewModel factory
 *  in [mx.gasappmx.MainActivity] when iterating on the UI offline. */
class SampleStationRepository : StationRepository {
    private val stations = listOf(
        GasStation(
            stationId = "MX-001",
            name = "Servicio Centro",
            address = "Av. Reforma 123, CDMX",
            latitude = 19.4326,
            longitude = -99.1332,
            distanceMeters = 800,
            prices = mapOf(FuelType.Regular to 23.49, FuelType.Premium to 25.19, FuelType.Diesel to 24.95),
            lastUpdatedAt = "2026-05-12T17:45:00Z",
        ),
        GasStation(
            stationId = "MX-002",
            name = "Gasolinera Roma",
            address = "Calle Puebla 44, CDMX",
            latitude = 19.4194,
            longitude = -99.1627,
            distanceMeters = 1200,
            prices = mapOf(FuelType.Regular to 23.10, FuelType.Premium to 25.39),
            lastUpdatedAt = "2026-05-12T17:30:00Z",
        ),
        GasStation(
            stationId = "MX-003",
            name = "Estacion Juarez",
            address = "Av. Juarez 50, CDMX",
            latitude = 19.4352,
            longitude = -99.1412,
            distanceMeters = 950,
            prices = mapOf(FuelType.Regular to 23.10, FuelType.Diesel to 24.49),
            lastUpdatedAt = "2026-05-12T17:40:00Z",
        ),
    )

    override suspend fun getNearbyStations(
        latitude: Double,
        longitude: Double,
        fuelType: FuelType,
        resultLimit: ResultLimit,
    ): List<GasStation> {
        return stations
            .filter { it.prices.containsKey(fuelType) }
            .sortForFuelType(fuelType)
            .take(resultLimit.value)
    }

    override suspend fun getStation(stationId: String): GasStation? {
        return stations.firstOrNull { it.stationId == stationId }
    }
}
