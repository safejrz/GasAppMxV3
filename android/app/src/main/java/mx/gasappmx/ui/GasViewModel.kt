package mx.gasappmx.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val userLocation: UserLocation? = null,
    val locationPermissionDenied: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class GasViewModel(
    private val repository: StationRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GasUiState())
    val uiState: StateFlow<GasUiState> = _uiState.asStateFlow()

    fun onFuelTypeChange(fuelType: FuelType) {
        _uiState.update {
            it.copy(
                fuelType = fuelType,
                selectedStation = null,
            )
        }
        refreshStations()
    }

    fun onResultLimitChange(resultLimit: ResultLimit) {
        _uiState.update {
            it.copy(
                resultLimit = resultLimit,
                selectedStation = null,
            )
        }
        refreshStations()
    }

    fun onLocationAvailable(userLocation: UserLocation) {
        _uiState.update {
            it.copy(
                userLocation = userLocation,
                locationPermissionDenied = false,
                errorMessage = null,
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
                isLoading = false,
                errorMessage = "No pudimos obtener tu ubicacion actual. Revisa que la ubicacion del dispositivo este activa.",
            )
        }
    }

    fun onStationSelected(station: GasStation) {
        _uiState.update {
            it.copy(
                selectedStation = station,
                isStationDetailLoading = true,
                stationDetailErrorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                repository.getStation(station.stationId)
            }.onSuccess { stationDetail ->
                _uiState.update {
                    it.copy(
                        selectedStation = stationDetail ?: station,
                        isStationDetailLoading = false,
                        stationDetailErrorMessage = if (stationDetail == null) {
                            "No encontramos el detalle actualizado de esta estacion."
                        } else {
                            null
                        },
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isStationDetailLoading = false,
                        stationDetailErrorMessage = error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun onStationDetailDismissed() {
        _uiState.update {
            it.copy(
                selectedStation = null,
                isStationDetailLoading = false,
                stationDetailErrorMessage = null,
            )
        }
    }

    fun refreshStations() {
        viewModelScope.launch {
            val current = _uiState.value
            val userLocation = current.userLocation
            if (userLocation == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        stations = emptyList(),
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }

            runCatching {
                repository.getNearbyStations(
                    latitude = userLocation.latitude,
                    longitude = userLocation.longitude,
                    fuelType = current.fuelType,
                    resultLimit = current.resultLimit,
                )
            }.onSuccess { stations ->
                _uiState.update {
                    it.copy(
                        stations = stations,
                        isLoading = false,
                        selectedStation = it.selectedStation?.takeIf { selected ->
                            stations.any { station -> station.stationId == selected.stationId }
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

    private fun Throwable.toUserMessage(): String {
        return when (this) {
            is StationDatasetUnavailableException -> {
                "El servicio esta activo, pero aun no tiene datos de precios disponibles."
            }
            else -> "No se pudo conectar con el servicio. Intenta de nuevo."
        }
    }
}
