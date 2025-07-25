package com.example.curositybox

import android.content.ContentValues
import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.ui.geometry.Size
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

data class TextBox(val text: String, val boundingBox: Rect?)

class CameraPreviewViewModel : ViewModel() {
    // Used to set up a link between the Camera and your UI.
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest

    private var currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private val imageCapture = ImageCapture.Builder().build()

    private val _capturedImageUri = MutableStateFlow<Uri?>(null)
    val capturedImageUri: StateFlow<Uri?> = _capturedImageUri

    private val _rotation = MutableStateFlow<Int>(0)
    val rotation: StateFlow<Int> = _rotation

    private val _size = MutableStateFlow<Size>(Size(0f,0f))
    val size: StateFlow<Size> = _size

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText

    private lateinit var imageAnalyzer: ImageAnalysis
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private fun setupAnalyzer() {
        imageAnalyzer =
            ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(Surface.ROTATION_0)
                .build().apply {
                    setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }
    }

    private val _recognizedElements = MutableStateFlow<List<TextBox>>(emptyList())
    val recognizedElements: StateFlow<List<TextBox>> = _recognizedElements

    fun updateRecognizedText(elements: List<TextBox>) {
        _recognizedElements.value = elements
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
            _size.value = Size(inputImage.width.toFloat(), inputImage.height.toFloat())

            _rotation.value = rotationDegrees

            recognizer.process(inputImage).addOnSuccessListener { visionText ->
                val recognizedElements = mutableListOf<TextBox>()
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        for (element in line.elements) {
                            recognizedElements.add(
                                TextBox(
                                    text = element.text,
                                    boundingBox = element.boundingBox
                                )
                            )
                        }
                    }
                }
                updateRecognizedText(recognizedElements)

                _recognizedText.value = visionText.text
                    Log.d("asdfasdfasdfasd", "Detected text: ${visionText.text}")
                }.addOnFailureListener { e ->
                    Log.e("asdfasdfasdfasd", "Text recognition error: ${e.message}")
                }.addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    fun switchCamera(appContext: Context, lifecycleOwner: LifecycleOwner) {
        currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        viewModelScope.launch {
            bindToCamera(appContext, lifecycleOwner)
        }
    }

    private val cameraPreviewUseCase = Preview.Builder().build().apply {
        setSurfaceProvider { newSurfaceRequest ->
            _surfaceRequest.update { newSurfaceRequest }
        }
    }

    suspend fun bindToCamera(appContext: Context, lifecycleOwner: LifecycleOwner) {
        val cameraProvider = ProcessCameraProvider.awaitInstance(appContext)
        cameraProvider.unbindAll()

        setupAnalyzer()

        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector = currentCameraSelector,
            cameraPreviewUseCase,
            imageCapture,
            imageAnalyzer
        )

        // Cancellation signals we're done with the camera
        try {
            awaitCancellation()
        } finally {
            cameraProvider.unbindAll()
        }
    }

    fun captureImage(context: Context) {
        val picturesDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val appFolder = File(picturesDir, "CurosityBox").apply { mkdirs() }
        val photoFile = File(appFolder, "IMG_${System.currentTimeMillis()}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    Log.d("CameraXLogadsfgfasd", "Saved to file: $savedUri")

                    _capturedImageUri.value = savedUri

                    // ✅ Manually insert to MediaStore (so Gallery can see it)
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, photoFile.name)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/CurosityBox"
                        )
                    }
                    context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values
                    )
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraXLogadsfgfasd", "Capture failed", exception)
                }
            })
    }

    override fun onCleared() {
        super.onCleared()
        cameraExecutor.shutdown()
    }

}