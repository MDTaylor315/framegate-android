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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle.Companion.Stroke
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.framegate.domain.mapping.CoordinateMapper
import com.example.framegate.domain.mapping.ScaleMode
import com.example.framegate.domain.model.NormalizedRoi
import com.example.framegate.domain.model.height
import com.example.framegate.domain.model.width

@Composable
fun CaptureScreen(
    modifier: Modifier = Modifier,
    viewModel: CaptureViewModel = viewModel()
                  ){
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsState()

    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect{
            effect ->
            when(effect){
                is CaptureUiEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is CaptureUiEffect.StepCompleted -> {

                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged{
                newSize ->
                viewSize = newSize
            },
        contentAlignment = Alignment.Center
    ){
        val activeRoi = NormalizedRoi(x=0.50f,y=0.50f, width = 0.50f, height = 0.40f)

        if (viewSize.width > 0 && viewSize.height > 0){
            val mapping = CoordinateMapper.mapCoordinates(
                normalizedRoi = activeRoi,
                bufferWidth = 1920,
                bufferHeight = 1080,
                viewWidth = viewSize.width.toFloat(),
                viewHeight = viewSize.height.toFloat(),
                sensorRotation = 90,
                scaleMode = ScaleMode.CROP
            )
            // Dibuja el marco verde del ROI en pantalla
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rect = mapping.viewRect
                drawRect(
                    color = Color.Green,
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Loop de Captura",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Estado del obturador: ${uiState.gateStatusText}",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        // HUD: mediciones del último frame.
        Text(
            text = "Foco: ${"%.1f".format(uiState.focus)}  " +
                "Brillo: ${"%.1f".format(uiState.brightness)}  " +
                "Movimiento: ${"%.1f".format(uiState.motion)}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "FPS: ${"%.1f".format(uiState.fps)}",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.onEvent(CaptureUiEvent.StartCapture) }
        ) {
            Text("Iniciar Loop de Captura")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.onEvent(CaptureUiEvent.PauseCapture) }
        ) {
            Text("Pausar Loop")
        }
    }
}
}
