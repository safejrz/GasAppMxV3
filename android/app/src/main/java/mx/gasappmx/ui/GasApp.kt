package mx.gasappmx.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import mx.gasappmx.model.FuelType
import mx.gasappmx.model.GasStation
import mx.gasappmx.model.ResultLimit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GasApp(
    state: GasUiState,
    onFuelTypeChange: (FuelType) -> Unit,
    onResultLimitChange: (ResultLimit) -> Unit,
    onStationSelected: (GasStation) -> Unit,
    onStationDetailDismissed: () -> Unit,
    onNavigateToStation: (GasStation) -> Unit,
    onRequestLocation: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Gasolina MX") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NearbyMap(
                state = state,
                onStationSelected = onStationSelected,
            )

            FilterRow(
                title = "Combustible",
                selected = state.fuelType,
                values = FuelType.entries,
                label = { it.label },
                onSelected = onFuelTypeChange,
            )

            FilterRow(
                title = "Resultados",
                selected = state.resultLimit,
                values = ResultLimit.entries,
                label = { it.label },
                onSelected = onResultLimitChange,
            )

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.locationPermissionDenied -> LocationPermissionState(onRequestLocation = onRequestLocation)
                    state.userLocation == null -> LocationWaitingState(onRequestLocation = onRequestLocation)
                    state.isLoading -> StatusMessage(text = "Buscando estaciones cercanas...")
                    state.errorMessage != null -> ErrorState(
                        message = state.errorMessage,
                        onRetry = onRequestLocation,
                    )
                    state.selectedStation != null -> StationDetail(
                        station = state.selectedStation,
                        fuelType = state.fuelType,
                        isLoading = state.isStationDetailLoading,
                        errorMessage = state.stationDetailErrorMessage,
                        onDismiss = onStationDetailDismissed,
                        onNavigate = { onNavigateToStation(state.selectedStation) },
                    )
                    state.stations.isEmpty() -> StatusMessage(
                        text = "No hay estaciones cercanas con precio ${state.fuelType.label}.",
                    )
                    else -> StationList(
                        state = state,
                        onStationSelected = onStationSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun NearbyMap(
    state: GasUiState,
    onStationSelected: (GasStation) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        val userLocation = state.userLocation
        if (userLocation == null) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Mapa cercano",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Esperando tu ubicacion actual",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            val center = LatLng(userLocation.latitude, userLocation.longitude)
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(center, 14f)
            }
            LaunchedEffect(center) {
                cameraPositionState.position = CameraPosition.fromLatLngZoom(center, 14f)
            }

            val context = LocalContext.current
            val tierMap = remember(state.stations, state.fuelType) {
                stationPriceTiers(state.stations, state.fuelType)
            }

            GoogleMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = true),
            ) {
                state.stations.forEach { station ->
                    val isSelected = state.selectedStation?.stationId == station.stationId
                    val tier = tierMap[station.stationId] ?: MarkerPriceTier.NONE
                    val priceText = station.prices[state.fuelType]
                        ?.let { "$%.2f".format(it) }
                        ?: "—"
                    val icon = remember(tier, isSelected, priceText) {
                        priceLabelBitmapDescriptor(
                            context = context,
                            priceText = priceText,
                            tier = tier,
                            isSelected = isSelected,
                        )
                    }
                    Marker(
                        state = MarkerState(position = LatLng(station.latitude, station.longitude)),
                        title = station.name,
                        snippet = station.prices[state.fuelType]?.let { "$it MXN" } ?: "Precio no disponible",
                        icon = icon,
                        zIndex = if (isSelected) 1f else 0f,
                        onClick = {
                            onStationSelected(station)
                            false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationPermissionState(onRequestLocation: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Activa el permiso de ubicacion para buscar gasolineras cerca de ti.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onRequestLocation) {
            Text("Usar mi ubicacion")
        }
    }
}

@Composable
private fun LocationWaitingState(onRequestLocation: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Necesitamos tu ubicacion actual para ordenar estaciones por distancia.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onRequestLocation) {
            Text("Obtener ubicacion")
        }
    }
}

@Composable
private fun <T> FilterRow(
    title: String,
    selected: T,
    values: List<T>,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            values.forEach { value ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelected(value) },
                    label = { Text(label(value)) },
                )
            }
        }
    }
}

@Composable
private fun StationList(
    state: GasUiState,
    onStationSelected: (GasStation) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.stations, key = { it.stationId }) { station ->
            StationCard(
                station = station,
                fuelType = state.fuelType,
                onClick = { onStationSelected(station) },
            )
        }
    }
}

@Composable
private fun StationCard(
    station: GasStation,
    fuelType: FuelType,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = station.name, style = MaterialTheme.typography.titleMedium)
            Text(text = station.address, style = MaterialTheme.typography.bodyMedium)
            Text(text = "Distancia: ${station.distanceMeters} m")
            Text(
                text = "Precio ${fuelType.label}: ${station.prices[fuelType]?.let { "$$it MXN" } ?: "No disponible"}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(text = "Actualizado: ${station.lastUpdatedAt}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StationDetail(
    station: GasStation,
    fuelType: FuelType,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onNavigate: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = station.name, style = MaterialTheme.typography.titleMedium)
            Text(text = station.address, style = MaterialTheme.typography.bodyMedium)
            if (isLoading) {
                Text(
                    text = "Actualizando detalle...",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FuelType.entries.forEach { type ->
                Text(
                    text = "${type.label}: ${station.prices[type]?.let { "$$it MXN" } ?: "No disponible"}",
                    style = if (type == fuelType) {
                        MaterialTheme.typography.bodyLarge
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onDismiss,
                    label = { Text("Cerrar") },
                )
                Button(onClick = onNavigate) {
                    Text("Navegar")
                }
            }
        }
    }
}

@Composable
private fun StatusMessage(text: String) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRetry) {
            Text("Reintentar")
        }
    }
}
