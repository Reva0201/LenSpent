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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import org.umn.lensspent.ui.theme.Spacing

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Photo picker: pops up the system gallery grid so any image (screenshots included) can be chosen.
    // PickVisualMedia needs no storage permission and grants read access to just the chosen item.
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { pickedUri ->
        if (pickedUri != null) {
            // The picker's grant does not outlive this process, and a capture row does: the image is
            // copied into app-private storage first, and the capture is recorded against the copy.
            scope.launch {
                val imported = ImageStorage.importToPrivateStorage(context, pickedUri)
                if (imported != null) {
                    viewModel.onImageCaptured(imported.toString(), CaptureSource.PICKER)
                }
            }
        }
    }

    // A stored capture opens the confirmation sheet, where the user decides whether it is true.
    //
    // This observer is deliberately above the permission branch. It used to sit below it, after the
    // early return for a denied camera, which meant "Choose a photo instead" imported the image and
    // recorded the capture but never navigated: the effect that watches for a pending capture was
    // part of the viewfinder that had not been composed. The picker exists precisely for the denied
    // case, so its result has to be observed in both states.
    val pendingCaptureId by viewModel.pendingCaptureId.collectAsStateWithLifecycle()
    LaunchedEffect(pendingCaptureId) {
        pendingCaptureId?.let { captureId ->
            viewModel.consumePendingCapture()
            onNavigateToReview(captureId)
        }
    }

    // The request is made when the user opens the camera, not from the dashboard: a person who only
    // types expenses is never asked for the camera at all. This runs once per entry into the screen.
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    if (!cameraPermissionState.status.isGranted) {
        CameraPermissionPane(
            onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
            onPickPhoto = {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onBack = onBack
        )
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    
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

    // The receipt just captured, shown in the left circle, plus its decoded bitmap. It starts null:
    // the app no longer scans the shared gallery to guess at a "latest photo", which would both need
    // a media permission and read images the user never chose for this app.
    var latestThumbnailUri by remember { mutableStateOf<Uri?>(null) }
    var thumbnail by remember { mutableStateOf<ImageBitmap?>(null) }

    // Decode off the main thread, downsampled to the circle size
    LaunchedEffect(latestThumbnailUri) {
        thumbnail = latestThumbnailUri?.let { uri ->
            withContext(Dispatchers.IO) { decodeSampledBitmap(context, uri, thumbnailTargetPx) }
        }
    }

    // A stored capture opens the confirmation sheet, where the user decides whether it is true.
    //
    // This observer lives above the permission branch: "Choose a photo instead" records a capture on
    // a device where camera access was denied, and an observer placed only inside the viewfinder would
    // never see it, leaving the imported photo with no way forward.

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
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A2A))
                    .border(1.dp, Color.White, CircleShape)
                    .clickable(
                        onClickLabel = "Choose a photo from your gallery"
                    ) {
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
                        // The container carries the action label; the picture itself is decorative.
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Choose a photo from your gallery",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            // CENTER: Native Circular Shutter Button
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .border(4.dp, Color.White, CircleShape)
                    .clickable(
                        onClickLabel = "Take a photo of the receipt"
                    ) {
                        // Straight into app-private storage: the receipt never lands in the shared
                        // gallery, so no media scanner, gallery app or photo-backup service can pick
                        // it up. CameraX writes the file and reports back on the main executor.
                        val receiptFile = ImageStorage.newReceiptFile(context)
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(receiptFile).build()

                        cameraController.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    val saved = Uri.fromFile(receiptFile)
                                    latestThumbnailUri = saved
                                    viewModel.onImageCaptured(saved.toString(), CaptureSource.CAMERA)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    // A failed shutter must not leave an empty file behind, and the
                                    // exception message can contain the file path, so it is not logged.
                                    receiptFile.delete()
                                    AppLog.warn("A receipt photo could not be captured")
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
                    .background(Color(0xFF2A2A2A).copy(alpha = 0.85f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription = "Switch camera",
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

/**
 * What the screen offers when the camera permission is not granted.
 *
 * It is a dark, viewfinder-shaped page rather than a black rectangle, because that is what the user
 * expects one step behind it. All three actions are full-width and at least 48dp: denial must not
 * leave someone with an unlabelled circle or a dead end. "Choose a photo instead" is a labelled
 * outline button here rather than a text link, because in this state it is a genuine alternative
 * path, not an afterthought.
 */
@Composable
private fun CameraPermissionPane(
    onRequestPermission: () -> Unit,
    onPickPhoto: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF12140F))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(Spacing.xxl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "Scan a receipt",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Camera access is needed only to scan a receipt. You can also add an " +
                    "expense by hand, or pick a photo you already have.",
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Allow camera", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = onPickPhoto,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.medium,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
            ) {
                Text(
                    text = "Choose a photo instead",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            TextButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Go back", color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

