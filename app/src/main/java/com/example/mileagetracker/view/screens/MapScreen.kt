package com.example.mileagetracker.view.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mileagetracker.R
import com.example.mileagetracker.viewmodel.LocationMapViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationMapScreen(
    viewModel: LocationMapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Collect states from ViewModel
    val currentLocation by viewModel.currentLocation.collectAsState()
    val currentAzimuth by viewModel.currentAzimuth.collectAsState()
    val isTracking by viewModel.isTracking.collectAsState()
    val trackPoints by viewModel.trackPoints.collectAsState()
    val activeTrack by viewModel.activeTrack.collectAsState()

    var currentAzimuthLocal by remember { mutableFloatStateOf(0f) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
        if (isGranted) {
            // Permission granted, start location updates immediately
            // This will trigger the LaunchedEffect below
        }
    }

    // Request permission if not granted
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Start location updates when permission is granted
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            // Get last known location immediately
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        viewModel.updateLocation(it)
                    }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    // Start location updates if permission is granted
    if (hasLocationPermission) {
        LocationUpdater(fusedLocationClient, viewModel) { location ->
            // Location updates are handled by ViewModel
        }
    }

    // Start compass/magnetometer updates
    CompassUpdater { azimuth ->
        currentAzimuthLocal = azimuth
        viewModel.updateAzimuth(azimuth)
    }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("Mileage Tracker") },
            actions = {
                IconButton(onClick = {
                    // TODO: Handle menu action
                }) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu"
                    )
                }
            }
        )
    }) { paddingValues ->

        if (!hasLocationPermission) {
            // Show permission request UI
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Location Permission Required",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "This app needs location permission to track your mileage.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    ) {
                        Text("Grant Permission")
                    }
                }
            }
        } else {
            currentLocation?.let { location ->
                MapViewContainer(
                    location = location,
                    azimuth = currentAzimuthLocal,
                    isTracking = isTracking,
                    trackPoints = trackPoints,
                    activeTrack = activeTrack,
                    onToggleTracking = {
                        if (isTracking) {
                            viewModel.stopTracking()
                        } else {
                            viewModel.startTracking(location)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            } ?: Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Getting your location...")
                }
            }
        }
    }
}

@Composable
fun LocationUpdater(
    fusedLocationClient: FusedLocationProviderClient,
    viewModel: LocationMapViewModel,
    onLocationUpdate: (Location) -> Unit
) {
    var lastLocation by remember { mutableStateOf<Location?>(null) }
    val distanceThresholdMeters = 5f

    DisposableEffect(Unit) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(4000L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val shouldUpdate = lastLocation?.distanceTo(location)?.let { it >= distanceThresholdMeters } ?: true
                    if (shouldUpdate) {
                        lastLocation = location
                        viewModel.updateLocation(location)
                        onLocationUpdate(location)
                    }
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        onDispose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }
}

@Composable
fun CompassUpdater(onAzimuthUpdate: (Float) -> Unit) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                    }
                }

                if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    val normalizedAzimuth = if (azimuth < 0) azimuth + 360 else azimuth
                    onAzimuthUpdate(normalizedAzimuth)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        magnetometer?.let {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        accelerometer?.let {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager.unregisterListener(sensorEventListener)
        }
    }
}

@Composable
fun MapViewContainer(
    location: Location,
    azimuth: Float,
    isTracking: Boolean,
    trackPoints: List<com.example.mileagetracker.data.model.TrackPoint>,
    activeTrack: com.example.mileagetracker.data.model.CurrentTrack?,
    onToggleTracking: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var currentMarker by remember { mutableStateOf<Marker?>(null) }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }

    val rotationOffset = -45f
    val adjustedAzimuth = (-azimuth + rotationOffset + 360f) % 360f

    Box(modifier = modifier) {
        AndroidView(
            factory = { context ->
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    mapView = this
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)

                    val geoPoint = GeoPoint(location.latitude, location.longitude)
                    controller.setZoom(17.0)
                    controller.setCenter(geoPoint)

                    val marker = Marker(this).apply {
                        position = geoPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = ContextCompat.getDrawable(context, R.drawable.direction)
                        rotation = adjustedAzimuth
                        infoWindow = null
                        setOnMarkerClickListener { _, _ -> true }
                    }

                    overlays.add(marker)
                    currentMarker = marker

                    // Initialize route polyline
                    val polyline = Polyline().apply {
                        outlinePaint.color = android.graphics.Color.BLUE
                        outlinePaint.strokeWidth = 12f
                    }
                    overlays.add(polyline)
                    routePolyline = polyline
                }
            },
            update = { view ->
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                view.controller.animateTo(geoPoint)
                currentMarker?.let { marker ->
                    marker.position = geoPoint
                    marker.rotation = adjustedAzimuth
                }

                // Update route polyline with track points
                routePolyline?.let { polyline ->
                    val geoPoints = trackPoints.map { trackPoint ->
                        GeoPoint(trackPoint.latitude, trackPoint.longitude)
                    }
                    polyline.setPoints(geoPoints)
                    polyline.outlinePaint.color = android.graphics.Color.BLUE
                }

                if (isTracking && trackPoints.isNotEmpty()) {
                    val startPoint = GeoPoint(trackPoints.first().latitude, trackPoints.first().longitude)
                    val startMarker = Marker(view).apply {
                        position = startPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = ContextCompat.getDrawable(view.context, R.drawable.green_flag)
                        infoWindow = null
                        setOnMarkerClickListener { _, _ -> true }
                    }

                    view.overlays.removeAll { it is Marker && it.title == "start_flag" }
                    startMarker.title = "start_flag"
                    view.overlays.add(startMarker)
                }

                if (isTracking == false && trackPoints.isNotEmpty()) {
                    val stopPoint = GeoPoint(trackPoints.last().latitude, trackPoints.last().longitude)
                    val stopMarker = Marker(view).apply {
                        position = stopPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = ContextCompat.getDrawable(view.context, R.drawable.red_flag)
                        infoWindow = null
                        setOnMarkerClickListener { _, _ -> true }
                    }

                    view.overlays.removeAll { it is Marker && it.title == "stop_flag" }
                    stopMarker.title = "stop_flag"
                    view.overlays.add(stopMarker)
                }

                view.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Center FAB
        FloatingActionButton(
            onClick = {
                mapView?.controller?.animateTo(
                    GeoPoint(location.latitude, location.longitude)
                )
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 150.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Center Map"
            )
        }

        // Toggle Tracking Button
        Button(
            onClick = onToggleTracking,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .width(250.dp)
                .height(60.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isTracking) Color.Red else Color(0xFF4CAF50)
            )
        ) {
            Icon(
                imageVector = if (isTracking) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isTracking) "Stop" else "Start",
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isTracking) "Stop Tracking" else "Start Tracking",
                color = Color.White
            )
        }
    }
}