package com.example.framegate.ui.capture

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private const val STROKE_WIDTH = 3f

@Composable
fun CaptureScreen(
    modifier: Modifier = Modifier,
    viewModel: CaptureViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is CaptureUiEffect.ShowToast ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { newSize ->
                viewModel.onEvent(CaptureUiEvent.ViewSizeChanged(newSize.width, newSize.height))
            },
        contentAlignment = Alignment.Center,
    ) {
        // El recuadro viene calculado por el ViewModel; la UI solo lo dibuja.
        uiState.overlayRect?.let { rect ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    color = Color.Green,
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    style = Stroke(width = STROKE_WIDTH.dp.toPx()),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "Loop de Captura", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Estado del obturador: ${uiState.gateStatusText}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Hud(uiState)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { viewModel.onEvent(CaptureUiEvent.StartCapture) }) {
                Text("Iniciar Loop de Captura")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { viewModel.onEvent(CaptureUiEvent.PauseCapture) }) {
                Text("Pausar Loop")
            }
        }
    }
}

@Composable
private fun Hud(uiState: CaptureUiState) {
    Text(
        text = "Foco: ${"%.1f".format(uiState.focus)}  " +
            "Brillo: ${"%.1f".format(uiState.brightness)}  " +
            "Movimiento: ${"%.1f".format(uiState.motion)}",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = "FPS: ${"%.1f".format(uiState.fps)}  " +
            "ms/frame: ${"%.2f".format(uiState.msPerFrame)}  " +
            "descartados: ${uiState.droppedFrames}",
        style = MaterialTheme.typography.bodySmall,
    )
}
