package mx.gasappmx.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.gasappmx.data.DirectionsResult
import mx.gasappmx.data.FunctionsClient
import mx.gasappmx.data.QuotaExhaustedException
import mx.gasappmx.data.StationDatasetUnavailableException
import mx.gasappmx.data.StationRepository
import mx.gasappmx.model.FuelType
import mx.gasappmx.model.GasStation
import mx.gasappmx.model.ResultLimit

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
)

data class GasUiState(
    val fuelType: FuelType = FuelType.Regular,
    val resultLimit: ResultLimit = ResultLimit.Top5,
    val stations: List<GasStation> = emptyList(),
    val selectedStation: GasStation? = null,
    val isStationDetailLoading: Boolean = false,
    val stationDetailErrorMessage: String? = null,
    // Directions ETA for the selected station (fetched on demand via F8 callable).
    val directions: DirectionsResult? = null,
    val isDirectionsLoading: Boolean = false,
    val directionsError: String? = null,
    val userLocation: UserLocation? = null,
    val stationAnchor: UserLocation? = null,
    val followDeviceLocation: Boolean = true,
    val locationPermissionDenied: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class GasViewModel(
    private val repository: StationRepository,
    private val functionsClient: FunctionsClient = FunctionsClient(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(GasUiState())
    val uiState: StateFlow<GasUiState> = _uiState.asStateFlow()

    private var directionsJob: Job? = null

    // ── Fuel / result-limit filters ───────────────────────────────────────────

    fun onFuelTypeChange(fuelType: FuelType) {
        _uiState.update { it.copy(fuelType = fuelType, selectedStation = null) }
        refreshStations()
    }

    fun onResultLimitChange(resultLimit: ResultLimit) {
        _uiState.update { it.copy(resultLimit = resultLimit, selectedStation = null) }
        refreshStations()
    }

    // ── Location ──────────────────────────────────────────────────────────────

    fun onLocationAvailable(userLocation: UserLocation) {
        _uiState.update {
            val shouldTrackLocation = it.followDeviceLocation || it.stationAnchor == null
            it.copy(
                userLocation = userLocation,
                stationAnchor = if (shouldTrackLocation) userLocation else it.stationAnchor,
                locationPermissionDenied = false,
                errorMessage = null,
            )
        }
        refreshStations()
    }

    fun onFollowDeviceLocationRequested() {
        _uiState.update {
            it.copy(
                followDeviceLocation = true,
                stationAnchor = it.userLocation ?: it.stationAnchor,
                selectedStation = null,
            )
        }
        refreshStations()
    }

    fun onViewportRefreshRequested(center: UserLocation) {
        _uiState.update {
            it.copy(
                followDeviceLocation = false,
                stationAnchor = center,
                selectedStation = null,
            )
        }
        refreshStations()
    }

    fun onLocationPermissionDenied() {
        _uiState.update {
            it.copy(
                stations = emptyList(),
                selectedStation = null,
                isStationDetailLoading = false,
                stationDetailErrorMessage = null,
                userLocation = null,
                stationAnchor = null,
                locationPermissionDenied = true,
                isLoading = false,
                errorMessage = null,
            )
        }
    }

    fun onLocationUnavailable() {
        _uiState.update {
            it.copy(
                stations = emptyList(),
                selectedStation = null,
                isStationDetailLoading = false,
                stationDetailErrorMessage = null,
                stationAnchor = null,
                isLoading = false,
                errorMessage = "No pudimos obtener tu ubicación. Revisa que la ubicación del dispositivo esté activa.",
            )
        }
    }

    // ── Station selection + directions ────────────────────────────────────────

    fun onStationSelected(station: GasStation) {
        _uiState.update {
            it.copy(
                selectedStation = station,
                isStationDetailLoading = false,
                stationDetailErrorMessage = null,
                directions = null,
                directionsError = null,
                isDirectionsLoading = false,
            )
        }
    }

    fun onStationDetailDismissed() {
        directionsJob?.cancel()
        _uiState.update {
            it.copy(
                selectedStation = null,
                isStationDetailLoading = false,
                stationDetailErrorMessage = null,
                directions = null,
                isDirectionsLoading = false,
                directionsError = null,
            )
        }
    }

    /** Fetches a real driving ETA for the selected station. Consumes one Directions quota token. */
    fun onDirectionsRequested() {
        val station = _uiState.value.selectedStation ?: return
        val userLoc = _uiState.value.userLocation ?: return

        directionsJob?.cancel()
        _uiState.update { it.copy(isDirectionsLoading = true, directionsError = null, directions = null) }

        directionsJob = viewModelScope.launch {
            runCatching {
                functionsClient.getDirections(
                    originLat = userLoc.latitude,
                    originLon = userLoc.longitude,
                    destLat = station.latitude,
                    destLon = station.longitude,
                )
            }.onSuccess { result ->
                _uiState.update { it.copy(directions = result, isDirectionsLoading = false) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isDirectionsLoading = false,
                        directionsError = error.toUserMessage(),
                    )
                }
            }
        }
    }

    // ── Stations refresh ──────────────────────────────────────────────────────

    fun refreshStations() {
        viewModelScope.launch {
            val current = _uiState.value
            val anchor = current.stationAnchor ?: current.userLocation
            if (anchor == null) {
                _uiState.update { it.copy(isLoading = false, stations = emptyList()) }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            runCatching {
                repository.getNearbyStations(
                    latitude = anchor.latitude,
                    longitude = anchor.longitude,
                    fuelType = current.fuelType,
                    resultLimit = current.resultLimit,
                )
            }.onSuccess { stations ->
                _uiState.update {
                    it.copy(
                        stations = stations,
                        isLoading = false,
                        selectedStation = it.selectedStation?.takeIf { selected ->
                            stations.any { s -> s.stationId == selected.stationId }
                        },
                        isStationDetailLoading = false,
                        stationDetailErrorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        stations = emptyList(),
                        isLoading = false,
                        selectedStation = null,
                        isStationDetailLoading = false,
                        stationDetailErrorMessage = null,
                        errorMessage = error.toUserMessage(),
                    )
                }
            }
        }
    }

    private fun Throwable.toUserMessage(): String = when (this) {
        is StationDatasetUnavailableException ->
            "El servicio aún no tiene datos de precios disponibles. Intenta en unos minutos."
        is QuotaExhaustedException ->
            "Límite diario alcanzado. El mapa y los precios siguen funcionando normalmente."
        else -> "No se pudo conectar. Intenta de nuevo."
    }
}
