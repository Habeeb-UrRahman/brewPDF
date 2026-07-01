package com.pdfmerger.app.ui.screen

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ViewComfy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pdfmerger.app.ui.component.ToolBottomBar
import androidx.compose.material.icons.outlined.FolderOpen
import com.pdfmerger.app.ui.component.BrewPickerPrompt
import com.pdfmerger.app.ui.component.BrewScaffold
import com.pdfmerger.app.ui.component.ToolResultSheet
import com.pdfmerger.app.ui.theme.ToolPageEditor
import com.pdfmerger.app.util.FileProviderUtil
import com.pdfmerger.app.util.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import sh.calvin.reorderable.ReorderableItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(initialUri: Uri? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var cachedFile by remember { mutableStateOf<File?>(null) }
    var pageThumbnails by remember { mutableStateOf<List<Bitmap?>>(emptyList()) }
    var pageOrder by remember { mutableStateOf<List<Int>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var isDone by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var mergeResult by remember { mutableStateOf<com.pdfmerger.app.viewmodel.MergeResult?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    
    LaunchedEffect(initialUri) {
        if (initialUri != null && selectedUri == null) {
            val uri = initialUri

            selectedUri = uri
            fileName = FileProviderUtil.getFileName(context, uri)
            isDone = false
            errorMessage = null
            scope.launch {
                val file = withContext(Dispatchers.IO) {
                    FileProviderUtil.copyUriToStaging(context, uri, "pageedit_input_${System.currentTimeMillis()}.pdf")
                }
                cachedFile = file
                if (file != null) {
                    val thumbs = withContext(Dispatchers.IO) {
                        loadPageThumbnails(file)
                    }
                    pageThumbnails = thumbs
                    pageOrder = thumbs.indices.toList()
                }
            }
        
        }
    }
val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedUri = it
            fileName = FileProviderUtil.getFileName(context, it)
            isDone = false
            errorMessage = null
            scope.launch {
                val file = withContext(Dispatchers.IO) {
                    FileProviderUtil.copyUriToStaging(context, it, "pageedit_input_${System.currentTimeMillis()}.pdf")
                }
                cachedFile = file
                if (file != null) {
                    val thumbs = withContext(Dispatchers.IO) {
                        loadPageThumbnails(file)
                    }
                    pageThumbnails = thumbs
                    pageOrder = thumbs.indices.toList()
                }
            }
        }
    }

    val removedCount = pageThumbnails.size - pageOrder.size
    val subtitle = if (pageOrder.isNotEmpty()) {
        "${pageOrder.size} pages${if (removedCount > 0) " ($removedCount removed)" else ""}"
    } else {
        "Reorder, rotate & delete"
    }

    BrewScaffold(
        title = "Page Editor",
        subtitle = subtitle,
        onBack = onBack,
        bottomBar = {
            if (selectedUri != null) {
                ToolBottomBar(
                    leftIcon = Icons.Outlined.FolderOpen,
                    onLeftClick = { filePicker.launch(arrayOf("application/pdf")) },
                    leftContentDesc = "Change File",
                    showClearButton = true,
                    onClearClick = {
                        selectedUri = null
                        pageThumbnails = emptyList()
                        pageOrder = emptyList()
                        cachedFile = null
                        isDone = false
                        errorMessage = null
                    },
                    actionText = if (isDone) "Done ✓" else "Process PDF",
                    isActionEnabled = pageOrder.isNotEmpty() && !isProcessing && !isDone,
                    isProcessing = isProcessing,
                    actionColor = com.pdfmerger.app.ui.theme.ToolPageEditor,
                    onActionClick = {
                        isProcessing = true
                        errorMessage = null
                        scope.launch {
                            try {
                            withContext(Dispatchers.IO) {
                                val pagesOneBased = pageOrder.map { it + 1 }
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                val outputFile = File(context.cacheDir, "edited_${timestamp}.pdf")
                                PdfUtils.reorderAndDeletePages(cachedFile!!, outputFile, pagesOneBased)
                                val resultUri = FileProviderUtil.saveToDownloads(context, outputFile, "edited_$fileName")
                                if (resultUri != null) {
                                    mergeResult = com.pdfmerger.app.viewmodel.MergeResult(
                                        fileName = "edited_$fileName",
                                        fileSize = outputFile.length(),
                                        outputUri = resultUri,
                                        localFile = outputFile
                                    )
                                }
                                outputFile.delete()
                            }
                            isDone = true
                        } catch (e: Exception) {
                            errorMessage = e.localizedMessage ?: "Edit failed"
                        }
                            isProcessing = false
                        }
                    }
                )
            }
        }
    ) {
        if (selectedUri == null) {
            Spacer(modifier = Modifier.weight(1f))
            BrewPickerPrompt(
                icon = Icons.Outlined.ViewComfy,
                title = "Select a PDF",
                subtitle = "Choose a document to edit",
                accentColor = ToolPageEditor,
                onClick = { filePicker.launch(arrayOf("application/pdf")) }
            )
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Text(
                text = "Hold to drag · Tap ✕ to remove",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            val lazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            val reorderableLazyGridState = sh.calvin.reorderable.rememberReorderableLazyGridState(lazyGridState) { from, to ->
                pageOrder = pageOrder.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                }
            }

            LazyVerticalGrid(
                state = lazyGridState,
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(pageOrder, key = { _, item -> item }) { _, originalIndex ->
                    ReorderableItem(
                        reorderableLazyGridState,
                        key = originalIndex
                    ) { isDragging ->
                        val bitmap = pageThumbnails.getOrNull(originalIndex)
                        Box(
                            modifier = Modifier
                                .aspectRatio(0.7f)
                                .shadow(if (isDragging) 8.dp else 0.dp, RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .draggableHandle()
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Page ${originalIndex + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("${originalIndex + 1}", fontWeight = FontWeight.Bold)
                                }
                            }
                            // Page number badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("${originalIndex + 1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            // Remove button
                            IconButton(
                                onClick = { pageOrder = pageOrder.filter { it != originalIndex } },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.9f))
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "Remove page",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
            }

            
        }
    }

    mergeResult?.let { result ->
        ToolResultSheet(
            title = "PDF Edited Successfully!",
            result = result,
            sheetState = sheetState,
            onDismiss = {
                mergeResult = null
                onBack() 
            },
            onShare = {
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(android.content.Intent.EXTRA_STREAM, result.outputUri)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, result.fileName)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share PDF"))
            },
            onOpen = {
                val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(result.outputUri, "application/pdf")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(viewIntent)
                } catch (e: Exception) {
                    context.startActivity(android.content.Intent.createChooser(viewIntent, "Open with"))
                }
            }
        )
    }
}

private fun loadPageThumbnails(file: File): List<Bitmap?> {
    return try {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val thumbnails = (0 until renderer.pageCount).map { index ->
            try {
                val page = renderer.openPage(index)
                val bitmap = Bitmap.createBitmap(
                    (page.width * 1.5f).toInt(),
                    (page.height * 1.5f).toInt(),
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmap
            } catch (e: Exception) { null }
        }
        renderer.close()
        pfd.close()
        thumbnails
    } catch (e: Exception) { emptyList() }
}
