package mx.gasappmx

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import mx.gasappmx.data.CloudStationRepository
import mx.gasappmx.model.GasStation
import mx.gasappmx.ui.GasApp
import mx.gasappmx.ui.GasViewModel
import mx.gasappmx.ui.UserLocation
import mx.gasappmx.ui.theme.GasAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GasAppTheme {
                val viewModel: GasViewModel = viewModel(
                    factory = GasViewModelFactory(),
                )
                val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                ) { permissions ->
                    val hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    if (hasLocationPermission) {
                        loadCurrentLocation(viewModel)
                    } else {
                        viewModel.onLocationPermissionDenied()
                    }
                }
                val requestLocation = {
                    if (hasLocationPermission()) {
                        loadCurrentLocation(viewModel)
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    }
                }

                LaunchedEffect(Unit) {
                    requestLocation()
                }

                GasApp(
                    state = uiState,
                    onFuelTypeChange = viewModel::onFuelTypeChange,
                    onResultLimitChange = viewModel::onResultLimitChange,
                    onStationSelected = viewModel::onStationSelected,
                    onStationDetailDismissed = viewModel::onStationDetailDismissed,
                    onNavigateToStation = ::openGoogleMapsNavigation,
                    onRequestLocation = requestLocation,
                )
            }
        }
    }

    private fun openGoogleMapsNavigation(station: GasStation) {
        val uri = Uri.parse("google.navigation:q=${station.latitude},${station.longitude}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        startActivity(intent)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun loadCurrentLocation(viewModel: GasViewModel) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val currentLocationRequest = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            .setMaxUpdateAgeMillis(60_000)
            .setDurationMillis(10_000)
            .build()
        val cancellationTokenSource = CancellationTokenSource()

        fusedLocationClient
            .getCurrentLocation(currentLocationRequest, cancellationTokenSource.token)
            .addOnSuccessListener { location ->
                if (location == null) {
                    loadLastKnownLocation(viewModel)
                } else {
                    viewModel.onLocationAvailable(location.toUserLocation())
                }
            }
            .addOnFailureListener {
                loadLastKnownLocation(viewModel)
            }
    }

    @SuppressLint("MissingPermission")
    private fun loadLastKnownLocation(viewModel: GasViewModel) {
        LocationServices.getFusedLocationProviderClient(this)
            .lastLocation
            .addOnSuccessListener { location ->
                if (location == null) {
                    viewModel.onLocationUnavailable()
                } else {
                    viewModel.onLocationAvailable(location.toUserLocation())
                }
            }
            .addOnFailureListener {
                viewModel.onLocationUnavailable()
            }
    }

    private fun Location.toUserLocation(): UserLocation {
        return UserLocation(
            latitude = latitude,
            longitude = longitude,
        )
    }

    private class GasViewModelFactory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GasViewModel::class.java)) {
                return GasViewModel(repository = CloudStationRepository()) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
        }
    }
}
