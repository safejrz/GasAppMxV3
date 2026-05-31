package mx.gasappmx.model

data class GasStation(
    val stationId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int,
    val prices: Map<FuelType, Double>,
    val lastUpdatedAt: String,
)

enum class FuelType(val apiValue: String, val label: String) {
    Regular("regular", "Regular"),
    Premium("premium", "Premium"),
    Diesel("diesel", "Diesel"),
}

enum class ResultLimit(val value: Int, val label: String) {
    Top5(5, "Top 5"),
    Top10(10, "Top 10"),
    Top25(25, "Top 25"),
    Top50(50, "Top 50"),
}

fun List<GasStation>.sortForFuelType(fuelType: FuelType): List<GasStation> {
    return sortedWith(
        compareBy<GasStation> { station -> station.distanceMeters }
            .thenBy { station -> station.prices.getValue(fuelType) },
    )
}
