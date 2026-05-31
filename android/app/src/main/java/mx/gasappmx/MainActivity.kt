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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import mx.gasappmx.auth.AuthManager
import mx.gasappmx.data.CloudStationRepository
import mx.gasappmx.model.GasStation
import mx.gasappmx.ui.GasApp
import mx.gasappmx.ui.GasViewModel
import mx.gasappmx.ui.SignInScreen
import mx.gasappmx.ui.UserLocation
import mx.gasappmx.ui.theme.GasAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GasAppTheme {
                val authManager = remember { AuthManager(applicationContext) }
                val auth = remember { FirebaseAuth.getInstance() }
                var currentUser by remember { mutableStateOf(auth.currentUser) }

                DisposableEffect(Unit) {
                    val listener = FirebaseAuth.AuthStateListener { currentUser = it.currentUser }
                    auth.addAuthStateListener(listener)
                    onDispose { auth.removeAuthStateListener(listener) }
                }

                if (currentUser == null) {
                    val scope = rememberCoroutineScope()
                    val context = LocalContext.current
                    var signingIn by remember { mutableStateOf(false) }
                    var error by remember { mutableStateOf<String?>(null) }
                    SignInScreen(
                        isSigningIn = signingIn,
                        errorMessage = error,
                        onSignInClick = {
                            signingIn = true
                            error = null
                            scope.launch {
                                authManager.signInWithGoogle(context)
                                    .onFailure { error = it.message ?: "No se pudo iniciar sesión." }
                                signingIn = false
                            }
                        },
                    )
                } else {
                    SignedInApp(
                        authManager = authManager,
                        userLabel = currentUser?.email ?: currentUser?.displayName,
                    )
                }
            }
        }
    }

    @Composable
    private fun SignedInApp(authManager: AuthManager, userLabel: String?) {
        val viewModel: GasViewModel = viewModel(factory = GasViewModelFactory())
        val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
        val scope = rememberCoroutineScope()

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

        LaunchedEffect(Unit) { requestLocation() }

        GasApp(
            state = uiState,
            onFuelTypeChange = viewModel::onFuelTypeChange,
            onResultLimitChange = viewModel::onResultLimitChange,
            onStationSelected = viewModel::onStationSelected,
            onStationDetailDismissed = viewModel::onStationDetailDismissed,
            onNavigateToStation = ::openGoogleMapsNavigation,
            onRequestLocation = requestLocation,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onSearchSubmit = viewModel::onSearchSubmit,
            onSearchResultSelected = viewModel::onSearchResultSelected,
            onSearchDismissed = viewModel::onSearchDismissed,
            onDirectionsRequested = viewModel::onDirectionsRequested,
            userLabel = userLabel,
            onSignOut = { scope.launch { authManager.signOut() } },
        )
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
