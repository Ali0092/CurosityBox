package com.example.curositybox

import android.graphics.Rect
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.example.curositybox.ui.theme.CurosityBoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CurosityBoxTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CameraPreviewScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPreviewScreen(modifier: Modifier = Modifier) {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    if (cameraPermissionState.status.isGranted) {
        val viewModel = remember { CameraPreviewViewModel() }
        CameraPreviewContent(viewModel = viewModel, modifier = Modifier.fillMaxSize())
    } else {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val textToShow = if (cameraPermissionState.status.shouldShowRationale) {
                "The camera is important for this app. Please grant the permission."
            } else {
                "Camera permission required for this feature to be available. " + "Please grant the permission"
            }

            Text(textToShow, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                Text("Request permission")
            }
        }
    }
}

@Composable
fun CameraPreviewContent(
    viewModel: CameraPreviewViewModel,
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val imageUri by viewModel.capturedImageUri.collectAsStateWithLifecycle()
    val recognizedElements by viewModel.recognizedElements.collectAsState()
    val recognizedText by viewModel.recognizedText.collectAsState()
    val imageSize by viewModel.size.collectAsState()
    val rotation by viewModel.rotation.collectAsState()

    val context = LocalContext.current
    LaunchedEffect(lifecycleOwner) {
        viewModel.bindToCamera(context, lifecycleOwner)
    }

    Box(modifier = modifier) {

        // CameraX
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request, modifier = modifier
            )
        }

        // Overlay layer
        Canvas(modifier = Modifier.fillMaxSize()) {
            val (srcWidth, srcHeight) = if (rotation == 0 || rotation == 180) {
                imageSize.width to imageSize.height
            } else {
                imageSize.height to imageSize.width
            }

            val scaleX = size.width / srcWidth
            val scaleY = size.height / srcHeight

            recognizedElements.forEach { element ->
                element.boundingBox?.let { rect ->
                    val mappedRect = Rect(
                        (rect.left * scaleX).toInt(),
                        (rect.top * scaleY).toInt(),
                        (rect.right * scaleX).toInt(),
                        (rect.bottom * scaleY).toInt()
                    )

                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(mappedRect.left.toFloat(), mappedRect.top.toFloat()),
                        size = Size(mappedRect.width().toFloat(), mappedRect.height().toFloat()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        Text(
            text = recognizedText,
            modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(top = 44.dp)
                .align(Alignment.TopCenter),
            textAlign = TextAlign.Center
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (imageUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(model = imageUri),
                    contentDescription = "Captured Image",
                    modifier = Modifier.size(height = 50.dp, width = 35.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(com.example.curositybox.R.drawable.photo),
                    contentDescription = null,
                    modifier = Modifier.size(height = 50.dp, width = 35.dp)
                )
            }

            // Capture image
            Image(
                painter = painterResource(com.example.curositybox.R.drawable.camera),
                contentDescription = null,
                modifier = Modifier
                    .clickable {
                        viewModel.captureImage(context)
                    }
                    .size(80.dp))

            // Camera switch button....
            Image(
                painter = painterResource(com.example.curositybox.R.drawable.camera_change_icon),
                contentDescription = null,
                modifier = Modifier
                    .clickable {
                        viewModel.switchCamera(context, lifecycleOwner)
                    }
                    .size(35.dp))
        }
    }

}

