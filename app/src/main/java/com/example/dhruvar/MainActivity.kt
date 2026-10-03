package com.example.dhruvar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.example.dhruvar.data.location.DefaultDeviceLocationControllerProvider
import com.example.dhruvar.data.repository.DefaultLayoutRepositoryProvider
import com.example.dhruvar.navigation.AppNavigation
import com.example.dhruvar.ui.theme.DhruvARTheme
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DefaultLayoutRepositoryProvider.init(filesDir.resolve("dhruvar_layouts"))
        DefaultDeviceLocationControllerProvider.init(applicationContext)
        MapLibre.getInstance(this)
        enableEdgeToEdge()
        // Field Kit is light-only, so the status/navigation icons must be dark to stay legible
        // over Mist. enableEdgeToEdge() otherwise picks them from the system theme.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            DhruvARTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
