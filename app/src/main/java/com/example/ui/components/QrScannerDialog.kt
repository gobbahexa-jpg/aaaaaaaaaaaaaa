package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.core.QrCodeUtils
import com.example.ui.theme.DlRadius
import com.example.ui.theme.DlSizing
import com.example.ui.theme.DlSpacing
import com.example.ui.theme.LocalDlTokens
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Suppress("DEPRECATION")
@Composable
fun QrDisplayDialog(
    title: String,
    qrContent: String,
    subtitle: String,
    onDismiss: () -> Unit
) {
    val tokens = LocalDlTokens.current
    val clipboard = LocalClipboardManager.current
    val qrBitmap = remember(qrContent) {
        QrCodeUtils.generateQrBitmap(qrContent, sizePx = 540)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceElevated,
        title = { Text(title, color = tokens.textPrimary) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (qrBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(DlRadius.sheet),
                        color = tokens.qrCanvas,
                        border = BorderStroke(DlSizing.divider, tokens.hairline)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.qr_display_cd),
                                modifier = Modifier.size(200.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                    textAlign = TextAlign.Center
                )
                DlSecondaryButton(
                    text = stringResource(R.string.devices_copy_qr_uri),
                    onClick = { clipboard.setText(AnnotatedString(qrContent)) },
                    icon = Icons.Filled.ContentCopy,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun QrScannerDialog(
    title: String = stringResource(R.string.qr_scanner_default_title),
    hint: String = stringResource(R.string.qr_scanner_default_hint),
    onDismiss: () -> Unit,
    onQrScanned: (String) -> Unit
) {
    val tokens = LocalDlTokens.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var manualUri by remember { mutableStateOf("") }
    var scannedOnce by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            cameraError = context.getString(R.string.qr_camera_denied)
        }
    }

    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            } catch (_: Exception) {
            }
            barcodeScanner.close()
            analyzerExecutor.shutdown()
        }
    }

    val reticleColor = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = reticleColor)
                Spacer(Modifier.width(8.dp))
                Text(title, color = tokens.textPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)

                if (hasCameraPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(DlRadius.lg))
                            .background(tokens.overlayBackdrop)
                    ) {
                        AndroidView(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("qr_camera_preview"),
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                                providerFuture.addListener({
                                    try {
                                        val cameraProvider = providerFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.surfaceProvider = previewView.surfaceProvider
                                        }
                                        val analysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()
                                        analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                                            val mediaImage = imageProxy.image
                                            if (mediaImage == null || scannedOnce) {
                                                imageProxy.close()
                                                return@setAnalyzer
                                            }
                                            val inputImage = InputImage.fromMediaImage(
                                                mediaImage,
                                                imageProxy.imageInfo.rotationDegrees
                                            )
                                            barcodeScanner.process(inputImage)
                                                .addOnSuccessListener { barcodes ->
                                                    val raw = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                                                    if (!raw.isNullOrBlank() && !scannedOnce) {
                                                        scannedOnce = true
                                                        onQrScanned(raw)
                                                        onDismiss()
                                                    }
                                                }
                                                .addOnCompleteListener {
                                                    imageProxy.close()
                                                }
                                        }
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            analysis
                                        )
                                    } catch (e: Exception) {
                                        cameraError = e.localizedMessage ?: context.getString(R.string.qr_camera_unavailable)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            }
                        )
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = 3.dp.toPx()
                            val arm = 24.dp.toPx()
                            val pad = 26.dp.toPx()
                            val left = (size.width / 2f) - 68.dp.toPx().coerceAtMost(size.width / 2f - pad)
                            val right = size.width - left
                            val top = pad
                            val bottom = size.height - pad

                            // Top-left
                            drawLine(reticleColor, Offset(left, top), Offset(left + arm, top), stroke, StrokeCap.Round)
                            drawLine(reticleColor, Offset(left, top), Offset(left, top + arm), stroke, StrokeCap.Round)
                            // Top-right
                            drawLine(reticleColor, Offset(right, top), Offset(right - arm, top), stroke, StrokeCap.Round)
                            drawLine(reticleColor, Offset(right, top), Offset(right, top + arm), stroke, StrokeCap.Round)
                            // Bottom-left
                            drawLine(reticleColor, Offset(left, bottom), Offset(left + arm, bottom), stroke, StrokeCap.Round)
                            drawLine(reticleColor, Offset(left, bottom), Offset(left, bottom - arm), stroke, StrokeCap.Round)
                            // Bottom-right
                            drawLine(reticleColor, Offset(right, bottom), Offset(right - arm, bottom), stroke, StrokeCap.Round)
                            drawLine(reticleColor, Offset(right, bottom), Offset(right, bottom - arm), stroke, StrokeCap.Round)
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(DlRadius.pillInput),
                        color = tokens.surfaceCard,
                        border = BorderStroke(DlSizing.divider, tokens.hairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(DlSpacing.sm)
                        ) {
                            Text(
                                text = stringResource(R.string.qr_camera_permission_rationale),
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) },
                                shape = RoundedCornerShape(DlRadius.md),
                                modifier = Modifier.testTag("grant_camera_qr_button")
                            ) {
                                Text(stringResource(R.string.qr_enable_camera_btn))
                            }
                        }
                    }
                }

                if (cameraError != null) {
                    Text(
                        text = cameraError.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                DlTextField(
                    value = manualUri,
                    onValueChange = { manualUri = it },
                    label = stringResource(R.string.qr_paste_label),
                    modifier = Modifier.testTag("manual_qr_uri_input")
                )
            }
        },
        confirmButton = {
            Button(
                enabled = manualUri.isNotBlank(),
                shape = RoundedCornerShape(DlRadius.md),
                onClick = {
                    onQrScanned(manualUri.trim())
                    onDismiss()
                },
                modifier = Modifier.testTag("submit_manual_qr_button")
            ) {
                Text(stringResource(R.string.qr_process_btn))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
