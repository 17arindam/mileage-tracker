package com.example.mileagetracker.view.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mileagetracker.R
import com.example.mileagetracker.data.model.CurrentTrack
import com.example.mileagetracker.data.model.TrackPoint
import com.example.mileagetracker.viewmodel.LocationMapViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

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
    val initialLocation by viewModel.initialLocation.collectAsState()
    var showStopFlag by remember { mutableStateOf(false) }
    val lastCompletedTrack by viewModel.lastCompletedTrack.collectAsState()
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // No permission needed for older versions
            }
        )
    }
    var currentAzimuthLocal by remember { mutableFloatStateOf(0f) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Dialog and Bottom Sheet states
    var showStopDialog by remember { mutableStateOf(false) }

    var showTrackSummarySheet by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState()

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true
        }
    }

    // Request permission if not granted
    // Replace the existing LaunchedEffect(Unit) with this:
    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()

        if (!hasLocationPermission) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
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

    // Stop Tracking Confirmation Dialog
    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop Tracking")
                }
            },
            text = {
                Text("Are you sure you want to stop tracking? This will end your current journey.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStopFlag = true
                        viewModel.stopTracking()
                        showStopDialog = false
                        showTrackSummarySheet = true
                    }
                ) {
                    Text("Stop", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showStopDialog = false }
                ) {
                    Text("Continue")
                }
            }
        )
    }

    // Track Summary Bottom Sheet
    if (showTrackSummarySheet) {
        ModalBottomSheet(
            onDismissRequest = { showTrackSummarySheet = false },
            sheetState = bottomSheetState,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        ) {
            TrackSummaryContent(
                trackPoints = trackPoints,
                activeTrack = lastCompletedTrack ?: activeTrack,
                onSave = {
                    // Handle save action
                    viewModel.clearTrackingData() // Add this new function
                    showTrackSummarySheet = false
                    showStopFlag = false
                },
                onCancel = {
                    viewModel.clearTrackingData() // Add this new function
                    showTrackSummarySheet = false
                    showStopFlag = false
                }
            )
        }
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

        // Replace the existing permission request UI with this:
        if (!hasLocationPermission || !hasNotificationPermission) {
            // Show permission request UI
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Permissions Required",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (!hasLocationPermission) {
                        Text(
                            text = "• Location permission is needed to track your mileage.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Text(
                            text = "• Notification permission is needed for background tracking.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val permissionsToRequest = mutableListOf<String>()

                            if (!hasLocationPermission) {
                                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                            }

                            if (permissionsToRequest.isNotEmpty()) {
                                permissionLauncher.launch(permissionsToRequest.toTypedArray())
                            }
                        }
                    ) {
                        Text("Grant Permissions")
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
                    initiallocation = initialLocation,
                    showStopFlag = showStopFlag,
                    onToggleTracking = {
                        if (isTracking) {
                            showStopDialog = true
                        } else {
                            showStopFlag = false
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
fun TrackSummaryContent(
    trackPoints: List<com.example.mileagetracker.data.model.TrackPoint>,
    activeTrack: com.example.mileagetracker.data.model.CurrentTrack?,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    viewModel: LocationMapViewModel = hiltViewModel() // Add this parameter
) {
    // Calculate distance
    val totalDistance = calculateTotalDistance(trackPoints)
    val duration = calculateDuration(trackPoints)

    // Dialog state
    var showSaveDialog by remember { mutableStateOf(false) }
    var trackName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Save Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = {
                Text("Save Track")
            },
            text = {
                Column {
                    Text("Enter a name for this track:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = trackName,
                        onValueChange = { trackName = it },
                        label = { Text("Track Name") },
                        placeholder = { Text("e.g., Morning Commute") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (trackName.isNotBlank()) {
                            scope.launch {
                                viewModel.saveTrackWithName(
                                    name = trackName,
                                    distance = totalDistance,
                                    duration = duration,
                                    currentTrack = activeTrack!!
                                )
                                showSaveDialog = false
                                onSave()
                            }
                        }
                    },
                    enabled = trackName.isNotBlank()
                ) {
                    Text("Save")
                }
            }
,
                    dismissButton = {
                TextButton(
                    onClick = { showSaveDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Journey Complete!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Stats Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Distance Card
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Route,
                title = "Distance",
                value = String.format("%.2f km", totalDistance / 1000),
                color = Color(0xFF2196F3)
            )

            // Duration Card
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Timer,
                title = "Duration",
                value = formatDuration(duration),
                color = Color(0xFF9C27B0)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Spacer(modifier = Modifier.height(32.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cancel Button
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.Red
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Discard", fontWeight = FontWeight.Medium)
            }

            // Save Button - Updated to show dialog
            Button(
                onClick = { showSaveDialog = true }, // Changed this line
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Trip", fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun StatRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// Helper functions
fun calculateTotalDistance(trackPoints: List<com.example.mileagetracker.data.model.TrackPoint>): Float {
    if (trackPoints.size < 2) return 0f

    var totalDistance = 0f
    for (i in 1 until trackPoints.size) {
        val prev = trackPoints[i - 1]
        val current = trackPoints[i]

        val results = FloatArray(1)
        Location.distanceBetween(
            prev.latitude, prev.longitude,
            current.latitude, current.longitude,
            results
        )
        totalDistance += results[0]
    }
    return totalDistance
}

fun calculateDuration(trackPoints: List<com.example.mileagetracker.data.model.TrackPoint>): Long {
    if (trackPoints.size < 2) return 0L

    val startTime = trackPoints.first().timestamp
    val endTime = trackPoints.last().timestamp
    return endTime - startTime
}



fun formatDuration(durationMillis: Long): String {
    val seconds = durationMillis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60

    return when {
        hours > 0 -> String.format(Locale.US, "%dh %dm", hours, minutes % 60)
        minutes > 0 -> String.format(Locale.US, "%dm %ds", minutes, seconds % 60)
        else -> String.format(Locale.US, "%ds", seconds)
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
    showStopFlag: Boolean,
    initiallocation: Location?,
    trackPoints: List<TrackPoint>,
    activeTrack: CurrentTrack?,
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

                // Start flag handling
                if ((isTracking || showStopFlag==true) && trackPoints.isNotEmpty()) {
                    val startPoint = GeoPoint(
                        initiallocation?.latitude ?: trackPoints.first().latitude,
                        initiallocation?.longitude ?: trackPoints.first().longitude
                    )
                    val startMarker = Marker(view).apply {
                        position = startPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = ContextCompat.getDrawable(view.context, R.drawable.green_flag)
                        infoWindow = null
                        setOnMarkerClickListener { _, _ -> true }
                        title = "start_flag"
                    }

                    // Remove previous start_flag and add new one
                    view.overlays.removeAll { it is Marker && it.title == "start_flag" }
                    view.overlays.add(startMarker)
                } else {
                    // Remove start flag only if not tracking
                    view.overlays.removeAll { it is Marker && it.title == "start_flag" }
                }

                // Stop flag handling — separate logic!
                if (showStopFlag && trackPoints.isNotEmpty()) {
                    val stopPoint = GeoPoint(trackPoints.last().latitude, trackPoints.last().longitude)
                    val stopMarker = Marker(view).apply {
                        position = stopPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = ContextCompat.getDrawable(view.context, R.drawable.red_flag)
                        infoWindow = null
                        setOnMarkerClickListener { _, _ -> true }
                        title = "stop_flag"
                    }

                    view.overlays.removeAll { it is Marker && it.title == "stop_flag" }
                    view.overlays.add(stopMarker)
                } else {
                    // Remove stop flag only
                    view.overlays.removeAll { it is Marker && it.title == "stop_flag" }
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