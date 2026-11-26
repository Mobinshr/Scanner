package com.mobin.scanner

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun CameraScannerView(
    scanner: BarcodeScanner,
    torchEnabled: Boolean,
    scanLock: AtomicBoolean,
    onDetected: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var imageAnalysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    val currentOnDetected by rememberUpdatedState(onDetected)
    val currentScanLock by rememberUpdatedState(scanLock)

    DisposableEffect(cameraExecutor) {
        onDispose {
            imageAnalysis?.clearAnalyzer()
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
        }
    }

    LaunchedEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                try {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder()
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage == null) {
                            imageProxy.close()
                        } else {
                            try {
                                val inputImage = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )
                                scanner.process(inputImage)
                                    .addOnSuccessListener { barcodes ->
                                        val barcode = barcodes.firstOrNull {
                                            !it.rawValue.isNullOrBlank()
                                        }
                                        val value = barcode?.rawValue
                                        if (
                                            value != null &&
                                            currentScanLock.compareAndSet(false, true)
                                        ) {
                                            val format = if (
                                                barcode.format == Barcode.FORMAT_QR_CODE
                                            ) "QR Code" else "Barcode"
                                            currentOnDetected(value, format)
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } catch (_: Exception) {
                                imageProxy.close()
                            }
                        }
                    }

                    provider.unbindAll()
                    val camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                    cameraProvider = provider
                    boundCamera = camera
                    imageAnalysis = analysis
                } catch (_: Exception) {
                    // Permission or device-camera errors are handled by the screen state.
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    LaunchedEffect(torchEnabled, boundCamera) {
        try {
            boundCamera?.cameraControl?.enableTorch(torchEnabled)
        } catch (_: Exception) {
            // Some cameras do not provide a flash unit.
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
