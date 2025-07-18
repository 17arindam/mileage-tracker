    package com.example.mileagetracker

    import android.content.Context.MODE_PRIVATE
    import android.os.Bundle
    import androidx.activity.ComponentActivity
    import androidx.activity.compose.setContent
    import androidx.compose.material3.*
    import androidx.compose.runtime.Composable
    import androidx.navigation.NavHostController
    import androidx.navigation.compose.NavHost
    import androidx.navigation.compose.composable
    import androidx.navigation.compose.rememberNavController
    import com.example.mileagetracker.view.screens.LocationMapScreen
    import com.example.mileagetracker.view.screens.SavedTracksScreen
    import com.example.mileagetracker.view.screens.TrackDetailScreen
    import dagger.hilt.android.AndroidEntryPoint
    import org.osmdroid.config.Configuration

    @AndroidEntryPoint
    class MainActivity : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
            setContent {
                MaterialTheme {
                    MileageTrackerNavigation()
                }
            }
        }
    }

    @Composable
    fun MileageTrackerNavigation() {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = "location_map"
        ) {
            composable("location_map") {
                LocationMapScreen(
                    onNavigateToSavedTracks = {
                        navController.navigate("saved_tracks")
                    }
                )
            }

            composable("saved_tracks") {
                SavedTracksScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onTrackClick = { routeId ->
                        navController.navigate("track_detail/$routeId")
                    }
                )
            }

            composable("track_detail/{routeId}") { backStackEntry ->
                val routeId = backStackEntry.arguments?.getString("routeId") ?: ""
                TrackDetailScreen(
                    routeId = routeId,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
