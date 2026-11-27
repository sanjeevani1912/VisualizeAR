package com.example.dhruvar

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.example.dhruvar.data.location.DefaultDeviceLocationControllerProvider
import com.example.dhruvar.data.repository.DefaultLayoutRepositoryProvider
import com.example.dhruvar.navigation.AppNavigation
import com.example.dhruvar.ui.ar.render.AssetModelRegistry
import com.example.dhruvar.ui.theme.DhruvARTheme
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Launch splash (DhruvAR logo), held for about two seconds so it can be seen, then the
        // normal Home screen. The app keeps loading behind it. Must run before super.onCreate.
        val splash = installSplashScreen()
        val splashStart = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition {
            SystemClock.uptimeMillis() - splashStart < SPLASH_MILLIS
        }
        super.onCreate(savedInstanceState)
        DefaultLayoutRepositoryProvider.init(filesDir.resolve("dhruvar_layouts"))
        // Map address saved at the last sign-in (<server>/map); blank keeps the build-time default.
        com.example.dhruvar.data.map.MapBasemapConfig.runtimeMapBase =
            com.example.dhruvar.data.remote.SessionStore(applicationContext).mapUrl.ifBlank { null }
        DefaultDeviceLocationControllerProvider.init(applicationContext)
        AssetModelRegistry.install { fileName ->
            try {
                assets.open("models/$fileName").use { it.readBytes() }
            } catch (_: java.io.IOException) {
                null
            }
        }
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

    private companion object {
        /** How long the launch splash stays on screen. */
        const val SPLASH_MILLIS = 2_000L
    }
}
