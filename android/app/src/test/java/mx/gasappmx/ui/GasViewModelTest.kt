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
        assertEquals("No se pudo conectar con el servicio. Intenta de nuevo.", state.errorMessage)
    }

    @Test
    fun `dataset unavailable failure exposes data not ready state`() = runTest {
        val viewModel = GasViewModel(
            FailingStationRepository(
                error = StationDatasetUnavailableException(responseBody = null),
            ),
        )

        viewModel.onLocationAvailable(UserLocation(latitude = 20.67, longitude = -103.35))

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.stations.isEmpty())
        assertEquals("El servicio esta activo, pero aun no tiene datos de precios disponibles.", state.errorMessage)
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
    fun `selecting station refreshes detail from repository`() = runTest {
        val repository = FakeStationRepository(
            stationDetail = GasStation(
                stationId = "regular-5",
                name = "Station detail",
                address = "Updated address",
                latitude = 20.67,
                longitude = -103.35,
                distanceMeters = 80,
                prices = mapOf(FuelType.Regular to 22.0, FuelType.Premium to 24.0),
                lastUpdatedAt = "2026-05-12T18:00:00Z",
            ),
        )
        val viewModel = GasViewModel(repository)

        viewModel.onLocationAvailable(UserLocation(latitude = 20.67, longitude = -103.35))
        viewModel.onStationSelected(viewModel.uiState.value.stations.first())

        val state = viewModel.uiState.value

        assertFalse(state.isStationDetailLoading)
        assertNull(state.stationDetailErrorMessage)
        assertEquals("Station detail", state.selectedStation?.name)
        assertEquals(24.0, state.selectedStation?.prices?.get(FuelType.Premium))
    }

    @Test
    fun `detail failure keeps selected list station and exposes detail message`() = runTest {
        val repository = FakeStationRepository(
            stationDetailError = IllegalStateException("detail unavailable"),
        )
        val viewModel = GasViewModel(repository)

        viewModel.onLocationAvailable(UserLocation(latitude = 20.67, longitude = -103.35))
        val selectedFromList = viewModel.uiState.value.stations.first()
        viewModel.onStationSelected(selectedFromList)

        val state = viewModel.uiState.value

        assertFalse(state.isStationDetailLoading)
        assertEquals(selectedFromList, state.selectedStation)
        assertEquals("No se pudo conectar con el servicio. Intenta de nuevo.", state.stationDetailErrorMessage)
    }
}

private data class RepositoryRequest(
    val fuelType: FuelType,
    val resultLimit: ResultLimit,
)

private class FakeStationRepository(
    private val stationDetail: GasStation? = null,
    private val stationDetailError: Throwable? = null,
) : StationRepository {
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

    override suspend fun getStation(stationId: String): GasStation? {
        stationDetailError?.let { throw it }
        return stationDetail
    }
}

private class FailingStationRepository(
    private val error: Throwable = IllegalStateException("backend unavailable"),
) : StationRepository {
    override suspend fun getNearbyStations(
        latitude: Double,
        longitude: Double,
        fuelType: FuelType,
        resultLimit: ResultLimit,
    ): List<GasStation> {
        throw error
    }

    override suspend fun getStation(stationId: String): GasStation? {
        throw error
    }
}
