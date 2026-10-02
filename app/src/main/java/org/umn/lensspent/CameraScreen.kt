package org.umn.lensspent

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.umn.lensspent.data.model.CaptureSource

/** The bottom control bar: the gallery, the shutter and the lens flip all sit inside it. */
private val ControlsBarHeight = 140.dp

/** How far above that bar the framing tips sit, so the two never touch. */
private val TipsGapAboveControls = 12.dp

/** The tips' sun, amber so it reads as light against the dark viewfinder. */
private val TipAccent = Color(0xFFFFD54F)

/**
 * Custom Camera screen with the requested layout:
 * - Top-left back button.
 * - Center shutter button.
 * - Left gallery preview (opens the system photo picker).
 * - Right camera lens flip toggle.
 * - Framing tips above the controls, so what makes a receipt readable is on screen while it is being
 *   framed rather than only after it has been read badly.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onBack: () -> Unit,
    onNavigateToReview: (Long) -> Unit,
    viewModel: CameraViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    if (!cameraPermissionState.status.isGranted) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Camera permission is required to scan receipts.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                    Text("Grant Permission")
                }
                TextButton(onClick = onBack) {
                    Text("Go Back", color = Color.White)
                }
            }
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    // Camera Engine States
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    // The framing tips: on by default, and the X hides them for this visit to the viewfinder
    var showCaptureTips by remember { mutableStateOf(true) }
    // The controller owns preview + capture, and handles rotation, pinch-to-zoom and tap-to-focus
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            // Receipts are shot in bursts: shutter-to-save latency beats maximum quality here
            setImageCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        }
    }
    
    // Gallery circle size, and the decode target (2x it) so a full resolution frame is never loaded
    val thumbnailSize = 60.dp
    val thumbnailTargetPx = with(LocalDensity.current) { (thumbnailSize * 2).roundToPx() }
    
    // The latest native image shown in the left circle, plus its decoded bitmap
    var latestThumbnailUri by remember { mutableStateOf<Uri?>(null) }
    var thumbnail by remember { mutableStateOf<ImageBitmap?>(null) }
    
    // Photo picker: pops up the gallery grid so any image (screenshots included) can be chosen
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { pickedUri ->
        if (pickedUri != null) {
            // The pick is a capture like any other, but the picker's read grant does not outlive this
            // process: the image is copied into the app's own gallery folder first, and the capture is
            // recorded against the copy so the review sheet can still open it after a restart.
            scope.launch {
                val imported = ImageStorage.importToGallery(context, pickedUri)
                if (imported != null) {
                    latestThumbnailUri = imported
                    viewModel.onImageCaptured(imported.toString(), CaptureSource.PICKER)
                }
            }
        }
    }
    
    // Initial thumbnail load (queries the MediaStore off the main thread)
    LaunchedEffect(Unit) {
        latestThumbnailUri = ImageStorage.latestNativePhoto(context)
    }
    
    // Decode off the main thread, downsampled to the circle size
    LaunchedEffect(latestThumbnailUri) {
        thumbnail = latestThumbnailUri?.let { uri ->
            withContext(Dispatchers.IO) { decodeSampledBitmap(context, uri, thumbnailTargetPx) }
        }
    }

    // A stored capture opens the confirmation sheet, where the user decides whether it is true
    val pendingCaptureId by viewModel.pendingCaptureId.collectAsStateWithLifecycle()
    LaunchedEffect(pendingCaptureId) {
        pendingCaptureId?.let { captureId ->
            viewModel.consumePendingCapture()
            onNavigateToReview(captureId)
        }
    }
    
    // Lifecycle-aware: binding to the NavBackStackEntry releases the camera when the screen pops
    DisposableEffect(lifecycleOwner) {
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose { cameraController.unbind() }
    }

    // Switching lenses is now a single assignment on the controller
    LaunchedEffect(lensFacing) {
        cameraController.cameraSelector =
            CameraSelector.Builder().requireLensFacing(lensFacing).build()
    }
    
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Fullscreen Viewfinder
        AndroidView(
            // Built inside the factory and bound to the controller, so the surface is wired exactly once
            factory = { viewContext ->
                PreviewView(viewContext).apply { controller = cameraController }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Top Back Header Bar Overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f))
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Go back",
                    tint = Color.White
                )
            }
        }
        
        // Bottom Camera Controls Bar Overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ControlsBarHeight)
                .background(Color.Black.copy(alpha = 0.5f))
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT: Native Gallery Circle Preview
            Box(
                modifier = Modifier
                    .size(thumbnailSize)
                    .clip(CircleShape)
                    .background(Color.DarkGray)
                    .border(1.dp, Color.White, CircleShape)
                    .clickable {
                        // Open the system photo picker: a pop up of the whole gallery (screenshots included)
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val decodedThumbnail = thumbnail
                if (decodedThumbnail != null) {
                    Image(
                        bitmap = decodedThumbnail,
                        contentDescription = "Choose a photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            
            // CENTER: Native Circular Shutter Button
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .border(4.dp, Color.White, CircleShape)
                    .clickable {
                        val contentValues = ImageStorage.createNativeImageContentValues()
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(
                            context.contentResolver,
                            ImageStorage.captureCollection(),
                            contentValues
                        ).build()
                        
                        cameraController.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    // The capture is complete: clear IS_PENDING and show it in the circle
                                    val saved = outputFileResults.savedUri
                                    if (saved != null) {
                                        scope.launch { ImageStorage.publishImage(context, saved) }
                                        viewModel.onImageCaptured(saved.toString(), CaptureSource.CAMERA)
                                        latestThumbnailUri = saved
                                    } else {
                                        scope.launch { latestThumbnailUri = ImageStorage.latestNativePhoto(context) }
                                    }
                                }
                                override fun onError(exception: ImageCaptureException) {
                                    exception.printStackTrace()
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
            
            // RIGHT: Lens Switch Button
            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.DarkGray.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription = "Switch Camera",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // The framing tips, directly above the controls: the one thing that decides whether the
        // recognizer has anything to read, said while the shot is still being framed.
        if (showCaptureTips) {
            CaptureTips(
                onDismiss = { showCaptureTips = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        start = 24.dp,
                        end = 24.dp,
                        bottom = ControlsBarHeight + TipsGapAboveControls
                    )
            )
        }
    }
}

/**
 * What makes a receipt photograph readable, shown while the user is framing it.
 *
 * The recognizer reads print, not intent: a slip shot in shadow, at an angle, or with half of it out
 * of frame is the first reason the confirmation sheet comes up empty. Saying so here - where it is
 * seen before the picture is taken rather than after - costs less than reading the failure off the
 * sheet. The X hides it for this visit; leaving and returning to the viewfinder shows it again.
 */
@Composable
private fun CaptureTips(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Translucent, like the header and control bars, so the viewfinder stays visible behind it
            .background(Color.Black.copy(alpha = 0.55f), MaterialTheme.shapes.medium)
            .padding(start = 14.dp, top = 10.dp, end = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.WbSunny,
                contentDescription = null,
                tint = TipAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "For a clean read",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Hide these tips",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        TipLine(text = "Bright, even light - no shadow or glare on the print")
        TipLine(text = "Lay it flat, and fit the whole receipt in the frame")
    }
}

/** One tip, indented to line up under the panel's title. */
@Composable
private fun TipLine(text: String) {
    Text(
        text = "•  $text",
        color = Color.White.copy(alpha = 0.9f),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(start = 26.dp)
    )
}

