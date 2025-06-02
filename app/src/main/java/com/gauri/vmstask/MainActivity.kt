package com.gauri.vmstask

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.gauri.vmstask.location.GeofenceHelper
import com.gauri.vmstask.location.LocationForegroundService
import com.gauri.vmstask.ui.theme.VmsTaskTheme
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var locationSettingsLauncher: ActivityResultLauncher<IntentSenderRequest>

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach { entry ->
            val permission = entry.key
            val granted = entry.value
            if (granted) {
                println("$permission granted ✅")
            } else {
                println("$permission denied ❌")
            }
        }
        val fineLocationGranted =
            permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineLocationGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestBackgroundLocationPermission()
            } else {
                bindToService()
            }
        } else {
            showDialog {
                requestAllPermissions()
            }
        }
    }
    private val requestBackgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            bindToService()
        } else {
            showDialog {
                requestBackgroundLocationPermission()
            }
        }
    }

    fun showDialog(onGrant: () -> Unit) {

        AlertDialog.Builder(this)
            .setTitle("Location Permission Needed")
            .setMessage("This app requires location access to track your location accurately. Please grant the permission.")
            .setPositiveButton("Grant") { dialog, _ ->
                onGrant()

                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(
                    this,
                    "Location permission is necessary for this feature.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    private var locationService: LocationForegroundService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocationForegroundService.LocationBinder
            locationService = binder.getService()
            mainViewModel.setBounded(true)
            lifecycleScope.launch {
                locationService?.locationFlow?.collect { location ->
                    mainViewModel.setLocation(location)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            locationService = null
        }
    }


    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationSettingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.d("LocationSettings", "User enabled location.")
                bindToService()
            } else {
                Log.w("LocationSettings", "User denied location enable.")
            }
        }

        // Call this to prompt location enable dialog
        checkAndPromptLocationEnable(this, locationSettingsLauncher)
        requestAllPermissions()
        enableEdgeToEdge()
        setContent {


            VmsTaskTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GeoMapScreenWithSearch(
                        this@MainActivity,
                        innerPadding,
                        mainViewModel = mainViewModel
                    )
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun requestAllPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        val allPermissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.FOREGROUND_SERVICE,
            android.Manifest.permission.POST_NOTIFICATIONS
        )

        allPermissions.forEach { permission ->
            if (ContextCompat.checkSelfPermission(
                    this, permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(permission)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            bindToService()
        }
    }

    private fun requestBackgroundLocationPermission() {
        val backgroundLocationPermission = android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
        requestBackgroundLocationLauncher.launch(backgroundLocationPermission)
    }

    private fun bindToService() {
        val intent = Intent(this, LocationForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun stopLocationService() {
        if (bound) {
            unbindService(connection)
            bound = false
            locationService = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationService()
    }

    private fun checkAndPromptLocationEnable(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000L
        ).build()

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)

        val client: SettingsClient = LocationServices.getSettingsClient(activity)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            Log.d("LocationSettings", "Location is already ON.")
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    val intentSenderRequest =
                        IntentSenderRequest.Builder(exception.resolution).build()
                    launcher.launch(intentSenderRequest)
                } catch (sendEx: Exception) {
                    Log.e("LocationSettings", "Resolution failed: ${sendEx.message}")
                }
            } else {
                Log.e("LocationSettings", "Location settings are inadequate and cannot be fixed.")
            }
        }
    }


}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!", modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    VmsTaskTheme {
        Greeting("Android")
    }
}


@OptIn(FlowPreview::class)
@Composable
fun GeoMapScreenWithSearch(
    activity: Activity,
    paddingValues: PaddingValues = PaddingValues(0.dp),
    mainViewModel: MainViewModel = hiltViewModel()
) {
    var query by remember { mutableStateOf("") }
    val location by mainViewModel.locationState.collectAsState()
    val searchResults = mainViewModel.predictions
    val cameraPositionState = rememberCameraPositionState()
    val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(activity)
    val geofenceHelper = remember { GeofenceHelper(activity, geofencingClient) }
    val geofenceRadius = 60f
    LaunchedEffect(query) {
        snapshotFlow { query }
            .debounce(500)
            .filter { it.isNotBlank() }
            .collect { mainViewModel.search(it) }
    }
    val markerState =
        rememberMarkerState(position = mainViewModel.selectedLatLng ?: LatLng(0.0, 0.0))
    LaunchedEffect(mainViewModel.selectedLatLng) {
        mainViewModel.selectedLatLng?.let {
            markerState.position = it
        }
    }
    LaunchedEffect(markerState.position) {
        if (markerState.position.latitude != 0.0 && markerState.position.longitude != 0.0) {
            mainViewModel.selectedLatLng = markerState.position
            geofenceHelper.addGeofence(
                markerState.position.latitude,
                markerState.position.longitude,
                geofenceRadius,
                "GEOFENCE"
            )
        }
    }
    LaunchedEffect(location, mainViewModel.selectedLatLng) {
        // Build the LatLngBounds to include both markers
        var useZoom = true
        val builder = LatLngBounds.Builder()
        location?.let {
            builder.include(LatLng(it.latitude, it.longitude))
        }
        mainViewModel.selectedLatLng?.let {
            if (it.latitude != 0.0 && it.longitude != 0.0){
                builder.include(it)
                useZoom = false
            }

        }
        location?.let {
            if(useZoom){
                cameraPositionState.animate(
                    update = CameraUpdateFactory.newLatLngZoom(
                        LatLng(it.latitude, it.longitude),
                        14f
                    ),
                    durationMs = 1000
                )
            }else{
                cameraPositionState.animate(
                    update = CameraUpdateFactory.newLatLngBounds(
                        builder.build(),
                        400
                    ),
                    durationMs = 1000
                )
            }
        }
    }
    Box(
        Modifier
            .padding(paddingValues)
            .fillMaxSize()
    ) {

        Box(Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                onMapLongClick = { latLng ->
                    if (mainViewModel.selectedLatLng?.latitude != latLng.latitude && mainViewModel.selectedLatLng?.latitude != latLng.longitude) {
                        mainViewModel.selectedLatLng = latLng
                        geofenceHelper.addGeofence(
                            latLng.latitude,
                            latLng.longitude,
                            geofenceRadius,
                            "GEOFENCE"
                        )
                    }
                }
            ) {
                location?.let {
                    Marker(
                        state = MarkerState(LatLng(it.latitude, it.longitude)),
                        title = "You are here",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    )
                }
                if (markerState.position.latitude != 0.0 && markerState.position.longitude != 0.0) {
                    Marker(state = markerState, title = "Geofence", draggable = true)
                    Circle(
                        center = markerState.position,
                        radius = geofenceRadius.toDouble(),
                        strokeColor = Color.Red,
                        fillColor = Color(0x22FF0000),
                        strokeWidth = 4f
                    )
                    location?.let { cur ->
                        Polyline(
                            points = listOf(
                                LatLng(
                                    cur.latitude,
                                    cur.longitude
                                ), markerState.position
                            ), color = Color.Green, width = 5f
                        )
                    }
                }

            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .fillMaxWidth(0.95f)
                .shadow(4.dp, RoundedCornerShape(12.dp))
                .padding(2.dp)
                .background(Color.White)
        ) {
            val keyboardController = LocalSoftwareKeyboardController.current
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                },
                placeholder = { Text("Search location…") },
            )
            if (searchResults.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .background(Color.White)
                ) {
                    items(searchResults.size) { index ->

                        Text(
                            text = searchResults[index].getFullText(null).toString(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    keyboardController?.hide()
                                    query = ""
                                    mainViewModel.clearSearch()
                                    mainViewModel.fetchPlaceDetails(searchResults[index].placeId)
                                }
                                .padding(8.dp)
                        )
                    }
                }
            }
        }

    }

}