package mx.gasappmx.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import mx.gasappmx.data.StationDatasetUnavailableException
import mx.gasappmx.data.StationRepository
import mx.gasappmx.model.FuelType
import mx.gasappmx.model.GasStation
import mx.gasappmx.model.ResultLimit
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
class GasViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `waits for current location before loading stations`() = runTest {
        val repository = FakeStationRepository()
        val viewModel = GasViewModel(repository)

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertTrue(state.stations.isEmpty())
        assertTrue(repository.requests.isEmpty())
    }

    @Test
    fun `loads nearby stations when current location is available`() = runTest {
        val viewModel = GasViewModel(FakeStationRepository())

        viewModel.onLocationAvailable(UserLocation(latitude = 20.67, longitude = -103.35))

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(listOf("regular-5"), state.stations.map { it.stationId })
        assertEquals(UserLocation(latitude = 20.67, longitude = -103.35), state.userLocation)
    }

    @Test
    fun `changing filters reloads repository with selected options`() = runTest {
        val repository = FakeStationRepository()
        val viewModel = GasViewModel(repository)

        viewModel.onLocationAvailable(UserLocation(latitude = 20.67, longitude = -103.35))
        viewModel.onFuelTypeChange(FuelType.Diesel)
        viewModel.onResultLimitChange(ResultLimit.Top25)

        assertEquals(FuelType.Diesel, repository.requests.last().fuelType)
        assertEquals(ResultLimit.Top25, repository.requests.last().resultLimit)
        assertEquals(listOf("diesel-25"), viewModel.uiState.value.stations.map { it.stationId })
    }

    @Test
    fun `repository failure clears stations and exposes degraded state`() = runTest {
        val viewModel = GasViewModel(FailingStationRepository())

        viewModel.onLocationAvailable(UserLocation(latitude = 20.67, longitude = -103.35))

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.stations.isEmpty())
        assertEquals("No se pudo conectar. Intenta de nuevo.", state.errorMessage)
    }

    @Test
    fun `dataset unavailable failure exposes data not ready state`() = runTest {
        val viewModel = GasViewModel(
            FailingStationRepository(
                error = StationDatasetUnavailableException("not ready"),
            ),
        )

        viewModel.onLocationAvailable(UserLocation(latitude = 20.67, longitude = -103.35))

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.stations.isEmpty())
        assertEquals(
            "El servicio aún no tiene datos de precios disponibles. Intenta en unos minutos.",
            state.errorMessage,
        )
    }

    @Test
    fun `permission denied exposes location permission state`() = runTest {
        val viewModel = GasViewModel(FakeStationRepository())

        viewModel.onLocationPermissionDenied()

        val state = viewModel.uiState.value

        assertTrue(state.locationPermissionDenied)
        assertNull(state.userLocation)
        assertTrue(state.stations.isEmpty())
    }

    @Test
    fun `selecting station sets selectedStation immediately`() = runTest {
        val repository = FakeStationRepository()
        val viewModel = GasViewModel(repository)

        viewModel.onLocationAvailable(UserLocation(latitude = 20.67, longitude = -103.35))
        val station = viewModel.uiState.value.stations.first()
        viewModel.onStationSelected(station)

        val state = viewModel.uiState.value
        assertEquals(station, state.selectedStation)
        assertFalse(state.isStationDetailLoading)
        assertNull(state.stationDetailErrorMessage)
    }

    @Test
    fun `dismissing station detail clears selection and directions`() = runTest {
        val viewModel = GasViewModel(FakeStationRepository())

        viewModel.onLocationAvailable(UserLocation(latitude = 20.67, longitude = -103.35))
        viewModel.onStationSelected(viewModel.uiState.value.stations.first())
        viewModel.onStationDetailDismissed()

        val state = viewModel.uiState.value
        assertNull(state.selectedStation)
        assertNull(state.directions)
        assertFalse(state.isDirectionsLoading)
    }
}

private data class RepositoryRequest(
    val fuelType: FuelType,
    val resultLimit: ResultLimit,
)

private class FakeStationRepository : StationRepository {
    val requests = mutableListOf<RepositoryRequest>()

    override suspend fun getNearbyStations(
        latitude: Double,
        longitude: Double,
        fuelType: FuelType,
        resultLimit: ResultLimit,
    ): List<GasStation> {
        requests += RepositoryRequest(fuelType = fuelType, resultLimit = resultLimit)
        return listOf(
            GasStation(
                stationId = "${fuelType.apiValue}-${resultLimit.value}",
                name = "Station",
                address = "Address",
                latitude = latitude,
                longitude = longitude,
                distanceMeters = 100,
                prices = mapOf(fuelType to 23.0),
                lastUpdatedAt = "2026-05-12T17:00:00Z",
            ),
        )
    }

    override suspend fun getStation(stationId: String): GasStation? = null
}

private class FailingStationRepository(
    private val error: Throwable = IllegalStateException("backend unavailable"),
) : StationRepository {
    override suspend fun getNearbyStations(
        latitude: Double,
        longitude: Double,
        fuelType: FuelType,
        resultLimit: ResultLimit,
    ): List<GasStation> = throw error

    override suspend fun getStation(stationId: String): GasStation? = throw error
}
