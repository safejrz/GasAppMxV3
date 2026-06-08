package mx.gasappmx.data

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

data class DirectionsResult(
    val distanceMeters: Int?,
    val durationSeconds: Int?,
    val durationText: String?,
)

class QuotaExhaustedException(message: String) : Exception(message)

/**
 * Thin wrapper around the Firebase callable functions that gate the paid Google Maps APIs.
 * The remaining function enforces the per-user daily Directions quota server-side (20/day).
 * A resource-exhausted error is surfaced as [QuotaExhaustedException] so the UI can show a
 * friendly "limit reached" message rather than a generic error.
 */
class FunctionsClient(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
) {
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
            val result = functions.getHttpsCallable(name).call(data).await()
            @Suppress("UNCHECKED_CAST")
            result.getData() as Map<String, Any>
        } catch (e: FirebaseFunctionsException) {
            if (e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED) {
                throw QuotaExhaustedException(e.message ?: "Límite diario alcanzado.")
            }
            throw e
        }
    }
}
