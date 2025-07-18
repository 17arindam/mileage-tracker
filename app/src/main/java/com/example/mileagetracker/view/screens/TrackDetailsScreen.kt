package com.example.mileagetracker.view.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.mileagetracker.data.model.TrackPoint
import com.example.mileagetracker.viewmodel.TrackDetailViewModel
import com.example.mileagetracker.view.screens.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailScreen(
    routeId: String,
    onNavigateBack: () -> Unit,
    viewModel: TrackDetailViewModel = hiltViewModel()
) {
    val savedTrack by viewModel.savedTrack.collectAsState()
    val trackPoints by viewModel.trackPoints.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var showStatsCard by remember { mutableStateOf(true) }

    // Load track data when screen is opened
    LaunchedEffect(routeId) {
        viewModel.loadTrackData(routeId)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(savedTrack?.name ?: "Track Details")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (savedTrack != null && trackPoints.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                AndroidView(
                    factory = { context ->
                        MapView(context).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            mapView = this
                            zoomController.setVisibility(
                                org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
                            )
                        }
                    },
                    update = { view ->
                        if (trackPoints.isNotEmpty()) {
                            coroutineScope.launch {
                                setupRouteAsync(view, trackPoints)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay the stats card on top
                if (showStatsCard) {
                    TrackStatsCard(
                        track = savedTrack!!,
                        onCancel = { showStatsCard = false },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                    )
                }



                // Center Map FAB
                FloatingActionButton(
                    onClick = {
                        if (trackPoints.isNotEmpty()) {
                            val centerPoint = GeoPoint(
                                trackPoints.first().latitude,
                                trackPoints.first().longitude
                            )
                            mapView?.controller?.animateTo(centerPoint)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Center Map"
                    )
                }
            }

        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Route,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Track not found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
@Composable
fun TrackStatsCard(
    track: com.example.mileagetracker.data.model.SavedTrack,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = formatDate(track.createdAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                onCancel?.let {
                    Icon(
                        imageVector = Icons.Default.Close, // or use Icons.Default.Close if available
                        contentDescription = "Close Stats Card",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                            .clickable { it() }
                            .padding(4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Start & End time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Start: ${formatTime(track.startTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "End: ${formatTime(track.endTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Route,
                    title = "Distance",
                    value = String.format("%.2f km", track.distance / 1000),
                    color = Color(0xFF2196F3)
                )

                StatItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Timer,
                    title = "Duration",
                    value = formatDuration(track.duration),
                    color = Color(0xFF9C27B0)
                )
            }
        }
    }
}


@Composable
fun StatItem(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    color: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(12.dp)
    ) {
        Column(
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

private suspend fun setupRouteAsync(mapView: MapView, trackPoints: List<TrackPoint>) {
    if (trackPoints.isEmpty()) return

    withContext(Dispatchers.Main) {
        val context = mapView.context

        // Clear existing overlays
        mapView.overlays.clear()

        // Create route polyline
        val polyline = Polyline().apply {
            outlinePaint.color = android.graphics.Color.BLUE
            outlinePaint.strokeWidth = 12f
        }

        // Set points on IO thread
        withContext(Dispatchers.IO) {
            val geoPoints = trackPoints.map { point ->
                GeoPoint(point.latitude, point.longitude)
            }
            withContext(Dispatchers.Main) {
                polyline.setPoints(geoPoints)
            }
        }

        mapView.overlays.add(polyline)

        // Add start marker (green flag)
        val startPoint = GeoPoint(trackPoints.first().latitude, trackPoints.first().longitude)
        val startMarker = Marker(mapView).apply {
            position = startPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = ContextCompat.getDrawable(context, R.drawable.green_flag)
            infoWindow = null
            setOnMarkerClickListener { _, _ -> true }
        }
        mapView.overlays.add(startMarker)

        // Add end marker (red flag)
        val endPoint = GeoPoint(trackPoints.last().latitude, trackPoints.last().longitude)
        val endMarker = Marker(mapView).apply {
            position = endPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = ContextCompat.getDrawable(context, R.drawable.red_flag)
            infoWindow = null
            setOnMarkerClickListener { _, _ -> true }
        }
        mapView.overlays.add(endMarker)

        // Fit the map to show the entire route with delay
        // Fit the map to show the entire route with delay
        mapView.post {
            if (trackPoints.size > 1) {
                val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPoints(
                    trackPoints.map { GeoPoint(it.latitude, it.longitude) }
                )
                mapView.zoomToBoundingBox(boundingBox.increaseByScale(1.2f), true)
            } else {
                mapView.controller.setZoom(17.0)
                mapView.controller.setCenter(startPoint)
            }
            mapView.invalidate()
        }

    }
}

// Helper functions
private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("EEEE, MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
