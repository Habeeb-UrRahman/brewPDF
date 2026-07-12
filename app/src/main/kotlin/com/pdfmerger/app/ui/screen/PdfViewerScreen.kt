package com.pdfmerger.app.ui.screen

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import com.pdfmerger.app.ui.component.BrewScaffold
import com.pdfmerger.app.ui.theme.ToolPdfToImages
import com.pdfmerger.app.viewmodel.MergeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.IOException
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    onBack: () -> Unit,
    viewModel: MergeViewModel,
    initialUri: Uri?,
    onNavigateToTool: (Tool) -> Unit
) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(initialUri) }
    
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    val rendererMutex = remember { Mutex() }
    
    var showToolSheet by remember { mutableStateOf(false) }
    var isNightMode by remember { mutableStateOf(false) }

    // Locked PDF state
    var needsPassword by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var tempDecryptedFile by remember { mutableStateOf<java.io.File?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            needsPassword = false
            passwordInput = ""
            passwordError = null
        }
    }

    // Initialize renderer when URI changes
    LaunchedEffect(selectedUri) {
        val uri = selectedUri
        if (uri != null) {
            withContext(Dispatchers.IO) {
                try {
                    fileDescriptor?.close()
                    pdfRenderer?.close()
                    val fd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (fd != null) {
                        fileDescriptor = fd
                        pdfRenderer = PdfRenderer(fd).also {
                            pageCount = it.pageCount
                        }
                        needsPassword = false
                    }
                } catch (e: SecurityException) {
                    // PDF is password-protected
                    withContext(Dispatchers.Main) {
                        needsPassword = true
                        pageCount = 0
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        } else {
            pageCount = 0
            needsPassword = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pdfRenderer?.close()
            fileDescriptor?.close()
            tempDecryptedFile?.delete()
        }
    }

    BrewScaffold(
        title = "PDF Viewer",
        subtitle = "Read and preview documents natively",
        onBack = onBack,
        actions = {
            if (selectedUri != null) {
                IconButton(onClick = { isNightMode = !isNightMode }) {
                    Icon(if (isNightMode) Icons.Outlined.LightMode else Icons.Outlined.DarkMode, contentDescription = "Toggle Night Mode")
                }
                IconButton(onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, selectedUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share PDF via"))
                }) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share PDF")
                }
            }
        },
        bottomBar = {}
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (selectedUri == null) {
                // Empty State
                Column(
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Text(
                        "No PDF Selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(
                        onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                        colors = ButtonDefaults.buttonColors(containerColor = ToolPdfToImages)
                    ) {
                        Text("Select PDF")
                    }
                }
            } else if (needsPassword) {
                // Locked PDF — password prompt
                val scope = rememberCoroutineScope()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Lock icon
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Text(
                        "This PDF is password-protected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        "Enter the password to view this document",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Password field
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            passwordError = null
                        },
                        label = { Text("Password") },
                        singleLine = true,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (passwordVisible) 
                            androidx.compose.ui.text.input.VisualTransformation.None 
                        else 
                            androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        isError = passwordError != null,
                        supportingText = passwordError?.let { err -> { Text(err) } },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ToolPdfToImages,
                            focusedLabelColor = ToolPdfToImages
                        )
                    )

                    // Open button
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val decrypted = withContext(Dispatchers.IO) {
                                        // Copy URI to temp, decrypt with iText
                                        val inputFile = com.pdfmerger.app.util.FileProviderUtil.copyUriToStaging(
                                            context, selectedUri!!, "viewer_temp_${System.currentTimeMillis()}.pdf"
                                        ) ?: throw Exception("Could not read file")
                                        val outFile = java.io.File(context.cacheDir, "decrypted_viewer_${System.currentTimeMillis()}.pdf")
                                        com.pdfmerger.app.util.PdfUtils.unlockPdf(inputFile, outFile, passwordInput)
                                        inputFile.delete()
                                        outFile
                                    }
                                    // Open decrypted file with PdfRenderer
                                    withContext(Dispatchers.IO) {
                                        fileDescriptor?.close()
                                        pdfRenderer?.close()
                                        tempDecryptedFile?.delete()
                                        tempDecryptedFile = decrypted
                                        val fd = ParcelFileDescriptor.open(decrypted, ParcelFileDescriptor.MODE_READ_ONLY)
                                        fileDescriptor = fd
                                        pdfRenderer = PdfRenderer(fd).also { pageCount = it.pageCount }
                                    }
                                    needsPassword = false
                                    passwordError = null
                                } catch (e: Exception) {
                                    passwordError = "Incorrect password"
                                }
                            }
                        },
                        enabled = passwordInput.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ToolPdfToImages),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                    ) {
                        Text("Open", fontWeight = FontWeight.Bold)
                    }

                    // Subtle "Create unlocked version?" button
                    TextButton(
                        onClick = {
                            viewModel.pendingToolUris.value = listOf(selectedUri!!)
                            onNavigateToTool(Tool.Unlock)
                        }
                    ) {
                        Icon(
                            Icons.Outlined.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Create unlocked version?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Viewer
                val listState = rememberLazyListState()
                var scale by remember { mutableFloatStateOf(1f) }
                var offsetX by remember { mutableFloatStateOf(0f) }
                var size by remember { mutableStateOf(IntSize.Zero) }
                val scaleState = rememberUpdatedState(scale)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { size = it }
                        .pointerInput(Unit) {
                            detectPdfGestures(scaleState) { centroid, pan, zoom ->
                                scale = maxOf(1f, minOf(scale * zoom, 5f))
                                if (scale > 1f) {
                                    val maxX = (size.width * (scale - 1)) / 2f
                                    offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                    if (pan.y != 0f) {
                                        listState.dispatchRawDelta(-pan.y / scale)
                                    }
                                } else {
                                    offsetX = 0f
                                }
                            }
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                            },
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pageCount) { index ->
                            PdfPage(
                                renderer = pdfRenderer,
                                mutex = rendererMutex,
                                pageIndex = index,
                                isNightMode = isNightMode
                            )
                        }
                    }
                }
                
                // Draggable FAB
                var fabOffsetX by remember { mutableFloatStateOf(0f) }
                var fabOffsetY by remember { mutableFloatStateOf(0f) }
                
                FloatingActionButton(
                    onClick = { showToolSheet = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 32.dp, end = 16.dp)
                        .offset { IntOffset(fabOffsetX.roundToInt(), fabOffsetY.roundToInt()) }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                fabOffsetX += dragAmount.x
                                fabOffsetY += dragAmount.y
                            }
                        },
                    containerColor = ToolPdfToImages
                ) {
                    Icon(Icons.Outlined.Build, contentDescription = "Tools")
                }
            }
        }
    }
    
    if (showToolSheet) {
        ModalBottomSheet(
            onDismissRequest = { showToolSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
            ) {
                Text(
                    text = "Open in Tool",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                val tools = listOf(
                    ToolItem(Tool.Merge, Icons.Outlined.Description, "Add to Merge", "", Color.Unspecified),
                    ToolItem(Tool.Extract, Icons.Outlined.Description, "Extract Pages", "", Color.Unspecified),
                    ToolItem(Tool.PageEditor, Icons.Outlined.Description, "Page Editor", "", Color.Unspecified),
                    ToolItem(Tool.Encrypt, Icons.Outlined.Description, "Lock PDF", "", Color.Unspecified),
                    ToolItem(Tool.Unlock, Icons.Outlined.Description, "Unlock PDF", "", Color.Unspecified),
                    ToolItem(Tool.PdfToImages, Icons.Outlined.Description, "PDF → Images", "", Color.Unspecified)
                )
                
                tools.forEach { toolItem ->
                    ListItem(
                        headlineContent = { Text(toolItem.name) },
                        leadingContent = { Icon(toolItem.icon, contentDescription = null) },
                        modifier = Modifier.clickable {
                            showToolSheet = false
                            viewModel.pendingToolUris.value = listOf(selectedUri!!)
                            onNavigateToTool(toolItem.tool)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfPage(
    renderer: PdfRenderer?,
    mutex: Mutex,
    pageIndex: Int,
    isNightMode: Boolean
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(renderer, pageIndex) {
        if (renderer == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val page = renderer.openPage(pageIndex)
                    // Render at high resolution (e.g. 2x) for better quality
                    val destBitmap = Bitmap.createBitmap(
                        page.width * 2,
                        page.height * 2,
                        Bitmap.Config.ARGB_8888
                    )
                    // Fill background white
                    destBitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(destBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmap = destBitmap
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val nightModeMatrix = remember {
        ColorMatrix(
            floatArrayOf(
                -1f,  0f,  0f, 0f, 255f,
                 0f, -1f,  0f, 0f, 255f,
                 0f,  0f, -1f, 0f, 255f,
                 0f,  0f,  0f, 1f,   0f
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (bitmap != null) bitmap!!.width.toFloat() / bitmap!!.height.toFloat() else 1f / 1.4f)
            .background(if (isNightMode) Color(0xFF1E1E1E) else Color.White)
            .shadow(4.dp),
        contentAlignment = Alignment.Center
    ) {
        val bm = bitmap
        if (bm != null) {
            Image(
                bitmap = bm.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = if (isNightMode) ColorFilter.colorMatrix(nightModeMatrix) else null
            )
        } else {
            CircularProgressIndicator()
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectPdfGestures(
    scaleState: State<Float>,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit
) {
    awaitEachGesture {
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop
        var pan = Offset.Zero
        var zoom = 1f

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                val pointerCount = event.changes.filter { it.pressed }.size

                // If scale is 1f and we only have 1 pointer, let LazyColumn handle the scroll.
                if (scaleState.value == 1f && pointerCount == 1) {
                    continue
                }

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange
                    
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop || panMotion > touchSlop) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (zoomChange != 1f || panChange != Offset.Zero) {
                        onGesture(centroid, panChange, zoomChange)
                    }
                    event.changes.forEach {
                        if (it.positionChange() != Offset.Zero) {
                            it.consume()
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}
