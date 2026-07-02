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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
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
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        } else {
            pageCount = 0
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pdfRenderer?.close()
            fileDescriptor?.close()
        }
    }

    BrewScaffold(
        title = "PDF Viewer",
        subtitle = "Read and preview documents natively",
        onBack = onBack,
        actions = {
            if (selectedUri != null) {
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
            } else {
                // Viewer
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                var size by remember { mutableStateOf(IntSize.Zero) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { size = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = maxOf(1f, minOf(scale * zoom, 5f))
                                if (scale > 1f) {
                                    val maxX = (size.width * (scale - 1)) / 2f
                                    val maxY = (size.height * (scale - 1)) / 2f
                                    offset = Offset(
                                        x = (offset.x + pan.x * scale).coerceIn(-maxX, maxX),
                                        y = (offset.y + pan.y * scale).coerceIn(-maxY, maxY)
                                    )
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        }
                ) {
                    LazyColumn(
                        userScrollEnabled = scale == 1f,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pageCount) { index ->
                            PdfPage(
                                renderer = pdfRenderer,
                                mutex = rendererMutex,
                                pageIndex = index
                            )
                        }
                    }
                }
                
                // Draggable FAB
                var offsetX by remember { mutableFloatStateOf(0f) }
                var offsetY by remember { mutableFloatStateOf(0f) }
                
                FloatingActionButton(
                    onClick = { showToolSheet = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 32.dp, end = 16.dp)
                        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
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
    pageIndex: Int
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (bitmap != null) bitmap!!.width.toFloat() / bitmap!!.height.toFloat() else 1f / 1.4f)
            .background(Color.White)
            .shadow(4.dp),
        contentAlignment = Alignment.Center
    ) {
        val bm = bitmap
        if (bm != null) {
            Image(
                bitmap = bm.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            CircularProgressIndicator()
        }
    }
}
