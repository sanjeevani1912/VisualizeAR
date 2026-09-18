package com.example.dhruvar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.dhruvar.data.repository.DefaultLayoutRepositoryProvider
import com.example.dhruvar.navigation.AppNavigation
import com.example.dhruvar.ui.theme.DhruvARTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize offline local repository with persistent app files directory
        DefaultLayoutRepositoryProvider.init(filesDir.resolve("dhruvar_layouts"))
        enableEdgeToEdge()
        setContent {
            DhruvARTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }
}