package mx.gasappmx.data

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

data class PlaceResult(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
)

data class DirectionsResult(
    val distanceMeters: Int?,
    val durationSeconds: Int?,
    val durationText: String?,
)

class QuotaExhaustedException(message: String) : Exception(message)

/**
 * Thin wrapper around the Firebase callable functions that gate the paid Google Maps APIs.
 * Both functions enforce per-user daily quotas server-side (20 Directions / 30 Places).
 * A resource-exhausted error is surfaced as [QuotaExhaustedException] so the UI can show a
 * friendly "limit reached" message rather than a generic error.
 */
class FunctionsClient(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
) {
    suspend fun searchPlaces(query: String, lat: Double?, lon: Double?): List<PlaceResult> {
        val data = buildMap<String, Any> {
            put("query", query)
            if (lat != null && lon != null) {
                put("lat", lat)
                put("lon", lon)
            }
        }
        val result = callFunction("placesSearch", data)
        @Suppress("UNCHECKED_CAST")
        val results = (result["results"] as? List<Map<String, Any>>) ?: return emptyList()
        return results.mapNotNull { r ->
            PlaceResult(
                name = r["name"] as? String ?: return@mapNotNull null,
                address = r["address"] as? String ?: "",
                lat = (r["lat"] as? Number)?.toDouble() ?: return@mapNotNull null,
                lon = (r["lon"] as? Number)?.toDouble() ?: return@mapNotNull null,
            )
        }
    }

    suspend fun getDirections(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
    ): DirectionsResult {
        val data = mapOf(
            "originLat" to originLat,
            "originLon" to originLon,
            "destLat" to destLat,
            "destLon" to destLon,
        )
        val result = callFunction("directions", data)
        return DirectionsResult(
            distanceMeters = (result["distanceMeters"] as? Number)?.toInt(),
            durationSeconds = (result["durationSeconds"] as? Number)?.toInt(),
            durationText = result["durationText"] as? String,
        )
    }

    private suspend fun callFunction(name: String, data: Map<String, Any>): Map<String, Any> {
        return try {
            @Suppress("UNCHECKED_CAST")
            functions.getHttpsCallable(name).call(data).await().data as Map<String, Any>
        } catch (e: FirebaseFunctionsException) {
            if (e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED) {
                throw QuotaExhaustedException(e.message ?: "Límite diario alcanzado.")
            }
            throw e
        }
    }
}
