package mx.gasappmx.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import mx.gasappmx.data.DirectionsResult
import mx.gasappmx.model.FuelType
import mx.gasappmx.model.GasStation
import mx.gasappmx.model.ResultLimit
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow

private val DEFAULT_CENTER = LatLng(20.6597, -103.3496)

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
    onViewportRefreshRequested: (UserLocation) -> Unit,
    onDirectionsRequested: () -> Unit,
    onReportInconsistency: (GasStation) -> Unit,
    userLabel: String?,
    onSignOut: () -> Unit,
) {
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true,
        ),
    )

    LaunchedEffect(state.selectedStation?.stationId) {
        if (state.selectedStation != null) scaffoldState.bottomSheetState.expand()
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 280.dp,
        sheetContent = {
            SheetContent(
                state = state,
                onFuelTypeChange = onFuelTypeChange,
                onResultLimitChange = onResultLimitChange,
                onStationSelected = onStationSelected,
                onStationDetailDismissed = onStationDetailDismissed,
                onNavigateToStation = onNavigateToStation,
                onRequestLocation = onRequestLocation,
                onDirectionsRequested = onDirectionsRequested,
                onReportInconsistency = onReportInconsistency,
            )
        },
    ) { _ ->
        var mapCenter by remember { mutableStateOf(DEFAULT_CENTER) }
        Box(modifier = Modifier.fillMaxSize()) {
            NearbyMap(
                state = state,
                onStationSelected = onStationSelected,
                onMapCenterChanged = { mapCenter = it },
            )
            TopOverlay(
                userLabel = userLabel,
                onSignOut = onSignOut,
            )
            FloatingActionButton(
                onClick = {
                    onViewportRefreshRequested(UserLocation(mapCenter.latitude, mapCenter.longitude))
                },
                containerColor = Color(0xFF2471A3),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 86.dp, end = 12.dp),
            ) {
                Text("↻", style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

// ── Top overlay: title row ───────────────────────────────────────────────────

@Composable
private fun TopOverlay(
    userLabel: String?,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Title row
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Gasolina MX",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (userLabel != null) {
                        Text(text = userLabel, style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(onClick = onSignOut) { Text("Salir") }
            }
        }
    }
}

// ── Full-screen map ───────────────────────────────────────────────────────────

@Composable
private fun NearbyMap(
    state: GasUiState,
    onStationSelected: (GasStation) -> Unit,
    onMapCenterChanged: (LatLng) -> Unit,
) {
    val anchor = state.stationAnchor ?: state.userLocation
    val center = anchor?.let { LatLng(it.latitude, it.longitude) } ?: DEFAULT_CENTER
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, 13.5f)
    }

    LaunchedEffect(state.stationAnchor) {
        state.stationAnchor?.let {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 13.5f),
            )
        }
    }

    LaunchedEffect(state.selectedStation?.stationId) {
        state.selectedStation?.let {
            cameraPositionState.animate(CameraUpdateFactory.newLatLng(LatLng(it.latitude, it.longitude)))
        }
    }

    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.position.target }
            .distinctUntilChanged()
            .collectLatest { onMapCenterChanged(it) }
    }

    val context = LocalContext.current
    val tierMap = remember(state.stations, state.fuelType) {
        stationPriceTiers(state.stations, state.fuelType)
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = state.userLocation != null),
        uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false),
    ) {
        state.stations.forEach { station ->
            val isSelected = state.selectedStation?.stationId == station.stationId
            val tier = tierMap[station.stationId] ?: MarkerPriceTier.NONE
            val priceText = station.prices[state.fuelType]?.let { "$%.2f".format(it) } ?: "—"
            val icon = remember(tier, isSelected, priceText) {
                priceLabelBitmapDescriptor(context, priceText, tier, isSelected)
            }
            Marker(
                state = MarkerState(LatLng(station.latitude, station.longitude)),
                title = station.name,
                snippet = station.prices[state.fuelType]?.let { "$it MXN" } ?: "Precio no disponible",
                icon = icon,
                zIndex = if (isSelected) 1f else 0f,
                onClick = { onStationSelected(station); false },
            )
        }
    }
}

// ── Bottom sheet content ──────────────────────────────────────────────────────

@Composable
private fun SheetContent(
    state: GasUiState,
    onFuelTypeChange: (FuelType) -> Unit,
    onResultLimitChange: (ResultLimit) -> Unit,
    onStationSelected: (GasStation) -> Unit,
    onStationDetailDismissed: () -> Unit,
    onNavigateToStation: (GasStation) -> Unit,
    onRequestLocation: () -> Unit,
    onDirectionsRequested: () -> Unit,
    onReportInconsistency: (GasStation) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilterRow("Combustible", state.fuelType, FuelType.entries, { it.label }, onFuelTypeChange)
        FilterRow("Resultados", state.resultLimit, ResultLimit.entries, { it.label }, onResultLimitChange)

        when {
            state.locationPermissionDenied -> LocationPermissionState(onRequestLocation)
            state.userLocation == null -> LocationWaitingState(onRequestLocation)
            state.isLoading -> LoadingState()
            state.errorMessage != null -> ErrorState(state.errorMessage, onRetry = onRequestLocation)
            state.selectedStation != null -> StationDetail(
                station = state.selectedStation,
                fuelType = state.fuelType,
                directions = state.directions,
                isDirectionsLoading = state.isDirectionsLoading,
                directionsError = state.directionsError,
                onDismiss = onStationDetailDismissed,
                onNavigate = { onNavigateToStation(state.selectedStation) },
                onRequestDirections = onDirectionsRequested,
                onReportInconsistency = onReportInconsistency,
            )
            state.stations.isEmpty() -> StatusMessage(
                "No hay estaciones cercanas con precio ${state.fuelType.label}.",
            )
            else -> StationList(state = state, onStationSelected = onStationSelected)
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun LocationPermissionState(onRequestLocation: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Activa el permiso de ubicación para buscar gasolineras cerca de ti.", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRequestLocation) { Text("Usar mi ubicación") }
    }
}

@Composable
private fun LocationWaitingState(onRequestLocation: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Necesitamos tu ubicación actual para ordenar estaciones por distancia.", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRequestLocation) { Text("Obtener ubicación") }
    }
}

@Composable
private fun LoadingState() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator()
        Text("Buscando estaciones cercanas...", style = MaterialTheme.typography.bodyMedium)
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
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun StationList(state: GasUiState, onStationSelected: (GasStation) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.stations, key = { it.stationId }) { station ->
            StationCard(station, state.fuelType) { onStationSelected(station) }
        }
    }
}

@Composable
private fun StationCard(station: GasStation, fuelType: FuelType, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(station.name, style = MaterialTheme.typography.titleMedium)
            Text("${station.distanceMeters} m", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Precio ${fuelType.label}: ${station.prices[fuelType]?.let { "$$it MXN" } ?: "No disponible"}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun StationDetail(
    station: GasStation,
    fuelType: FuelType,
    directions: DirectionsResult?,
    isDirectionsLoading: Boolean,
    directionsError: String?,
    onDismiss: () -> Unit,
    onNavigate: () -> Unit,
    onRequestDirections: () -> Unit,
    onReportInconsistency: (GasStation) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(station.name, style = MaterialTheme.typography.titleMedium)
            Text("${station.distanceMeters} m aprox.", style = MaterialTheme.typography.bodyMedium)

            FuelType.entries.forEach { type ->
                Text(
                    "${type.label}: ${station.prices[type]?.let { "$$it MXN" } ?: "No disponible"}",
                    style = if (type == fuelType) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (type == fuelType) FontWeight.SemiBold else FontWeight.Normal,
                )
            }

            // Directions ETA section
            when {
                isDirectionsLoading -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text("Calculando ruta...", style = MaterialTheme.typography.bodySmall)
                }
                directions != null -> Text(
                    "🚗 ${directions.durationText ?: "—"}  ·  ${directions.distanceMeters?.let { "${it / 1000.0} km" } ?: "—"}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                directionsError != null -> Text(
                    directionsError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onDismiss, label = { Text("Cerrar") })
                if (directions == null && !isDirectionsLoading) {
                    AssistChip(onClick = onRequestDirections, label = { Text("Ver ruta exacta") })
                }
                AssistChip(onClick = { onReportInconsistency(station) }, label = { Text("PROFECO") })
                Button(onClick = onNavigate) { Text("Navegar") }
            }
        }
    }
}

@Composable
private fun StatusMessage(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}
