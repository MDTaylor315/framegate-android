package com.example.framegate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.framegate.di.AppGraph
import com.example.framegate.ui.capture.CaptureScreen
import com.example.framegate.ui.queue.QueueScreen
import com.example.framegate.ui.theme.FrameGateTheme

enum class AppScreen {
    CAPTURE,
    QUEUE,
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The asset (requires Context) is read here; AppGraph only parses it.
        val planJson = assets.open("fixtures/plan_messy.json").bufferedReader().use { it.readText() }
        AppGraph.init(filesDir, planJson)
        enableEdgeToEdge()
        setContent {
            FrameGateTheme {
                FrameGateApp()
            }
        }
    }
}

@Composable
fun FrameGateApp() {
    // rememberSaveable: preserves the active screen upon Activity recreation (e.g. screen rotation).
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.CAPTURE) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == AppScreen.CAPTURE,
                    onClick = { currentScreen = AppScreen.CAPTURE },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Captura") },
                    label = { Text("Captura") }
                )
                NavigationBarItem(
                    selected = currentScreen == AppScreen.QUEUE,
                    onClick = { currentScreen = AppScreen.QUEUE },
                    icon = { Icon(Icons.AutoMirrored.Default.List, contentDescription = "Cola") },
                    label = { Text("Cola") }
                )
            }
        }
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (currentScreen) {
            AppScreen.CAPTURE -> CaptureScreen(modifier = modifier)
            AppScreen.QUEUE -> QueueScreen(modifier = modifier)
        }
    }
}
