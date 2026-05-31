package mx.gasappmx.data

import mx.gasappmx.model.FuelType
import mx.gasappmx.model.GasStation
import mx.gasappmx.model.ResultLimit

/** Source of normalized gas-station data + nearby ranking. */
interface StationRepository {
    suspend fun getNearbyStations(
        latitude: Double,
        longitude: Double,
        fuelType: FuelType,
        resultLimit: ResultLimit,
    ): List<GasStation>

    suspend fun getStation(stationId: String): GasStation?
}

/** Thrown when the cached station dataset has not been produced yet (e.g. first ingest pending). */
class StationDatasetUnavailableException(message: String? = null) : Exception(message)
