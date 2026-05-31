package mx.gasappmx.data

import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mx.gasappmx.model.FuelType
import mx.gasappmx.model.GasStation
import mx.gasappmx.model.ResultLimit
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Reads the normalized CNE dataset that the scheduled `cneIngest` Cloud Function caches as a
 * single gzipped JSON blob in Cloud Storage (see firebase/functions/src/cneIngest.ts). The blob
 * is downloaded once per session (refreshed when stale), then all nearby ranking — Haversine
 * distance + cheapest-first ordering — happens on-device for free. No paid API is involved here.
 *
 * Requires an authenticated user: the Storage security rules only allow signed-in reads.
 */
class CloudStationRepository(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
) : StationRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    @Volatile private var cache: List<GasStation> = emptyList()
    @Volatile private var cacheLoadedAtMs: Long = 0L
    @Volatile private var datasetUpdatedAt: String = ""
    @Volatile private var lastRanked: Map<String, GasStation> = emptyMap()

    override suspend fun getNearbyStations(
        latitude: Double,
        longitude: Double,
        fuelType: FuelType,
        resultLimit: ResultLimit,
    ): List<GasStation> {
        val stations = ensureDataset()

        val ranked = stations.asSequence()
            .mapNotNull { station ->
                val price = station.prices[fuelType] ?: return@mapNotNull null
                val km = haversineKm(latitude, longitude, station.latitude, station.longitude)
                if (km > NEARBY_RADIUS_KM) null
                else station.copy(distanceMeters = (km * 1000).roundToInt()) to price
            }
            .sortedWith(compareBy({ it.second }, { it.first.distanceMeters })) // cheapest, then nearest
            .map { it.first }
            .take(resultLimit.value)
            .toList()

        lastRanked = ranked.associateBy { it.stationId }
        return ranked
    }

    override suspend fun getStation(stationId: String): GasStation? {
        return lastRanked[stationId] ?: ensureDataset().firstOrNull { it.stationId == stationId }
    }

    private suspend fun ensureDataset(): List<GasStation> {
        val fresh = cache.isNotEmpty() &&
            (System.currentTimeMillis() - cacheLoadedAtMs) < CACHE_TTL_MS
        if (fresh) return cache

        return mutex.withLock {
            val stillFresh = cache.isNotEmpty() &&
                (System.currentTimeMillis() - cacheLoadedAtMs) < CACHE_TTL_MS
            if (stillFresh) return@withLock cache
            loadDataset().also {
                cache = it
                cacheLoadedAtMs = System.currentTimeMillis()
            }
        }
    }

    private suspend fun loadDataset(): List<GasStation> = withContext(Dispatchers.IO) {
        val bytes = try {
            storage.reference.child(DATASET_PATH).getBytes(MAX_DOWNLOAD_BYTES).await()
        } catch (e: Exception) {
            if (cache.isNotEmpty()) return@withContext cache // keep last-good on transient failure
            throw StationDatasetUnavailableException("No se pudo descargar el catálogo de estaciones.")
        }

        val text = GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader().use { it.readText() }
        val dataset = json.decodeFromString(StationDatasetDto.serializer(), text)
        if (dataset.stations.isEmpty()) {
            throw StationDatasetUnavailableException("El catálogo aún no tiene estaciones.")
        }
        datasetUpdatedAt = dataset.updatedAt

        dataset.stations.map { dto ->
            GasStation(
                stationId = dto.id,
                name = dto.name,
                address = dto.creId,
                latitude = dto.lat,
                longitude = dto.lon,
                distanceMeters = 0,
                prices = buildMap {
                    dto.regular?.let { put(FuelType.Regular, it) }
                    dto.premium?.let { put(FuelType.Premium, it) }
                    dto.diesel?.let { put(FuelType.Diesel, it) }
                },
                lastUpdatedAt = dataset.updatedAt,
            )
        }
    }

    private companion object {
        const val DATASET_PATH = "stations/latest.json.gz"
        const val MAX_DOWNLOAD_BYTES = 32L * 1024 * 1024 // 32 MB ceiling; blob is ~1 MB gzipped
        const val CACHE_TTL_MS = 30L * 60 * 1000 // mirrors the 30-min ingest cadence
        const val NEARBY_RADIUS_KM = 12.0
    }
}

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

@Serializable
private data class StationDatasetDto(
    val updatedAt: String = "",
    val source: String = "",
    val count: Int = 0,
    val stations: List<CneStationDto> = emptyList(),
)

@Serializable
private data class CneStationDto(
    val id: String,
    val name: String,
    val creId: String = "",
    val lat: Double,
    val lon: Double,
    val regular: Double? = null,
    val premium: Double? = null,
    val diesel: Double? = null,
)
