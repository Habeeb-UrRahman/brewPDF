package com.pdfmerger.app.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pdfmerger.app.model.PdfItem
import com.pdfmerger.app.ui.component.BrewPickerPrompt
import com.pdfmerger.app.ui.component.BrewScaffold
import com.pdfmerger.app.ui.component.PdfListItem
import com.pdfmerger.app.ui.component.ToolBottomBar
import com.pdfmerger.app.ui.component.ToolResultSheet
import com.pdfmerger.app.ui.theme.BrewAmber
import com.pdfmerger.app.ui.theme.ToolMerge
import com.pdfmerger.app.util.FileProviderUtil
import com.pdfmerger.app.util.PdfUtils
import com.pdfmerger.app.viewmodel.MergeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeScreen(initialUris: List<Uri> = emptyList(), onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // UI State
    val items = remember { mutableStateListOf<PdfItem>() }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var mergeResult by remember { mutableStateOf<MergeResult?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Preview & Rename States
    var previewFile by remember { mutableStateOf<File?>(null) }
    var showPreviewViewer by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var suggestedOutputName by remember { mutableStateOf("") }
    
    // Add multiple files
    fun addFiles(uris: List<Uri>) {
        errorMessage = null
        scope.launch {
            isProcessing = true
            for (uri in uris) {
                val fileName = FileProviderUtil.getFileName(context, uri)
                val fileSize = FileProviderUtil.getFileSize(context, uri)
                // Copy to temp cache for merging
                val file = withContext(Dispatchers.IO) {
                    FileProviderUtil.copyUriToStaging(context, uri, "quickmerge_${UUID.randomUUID()}.pdf")
                }
                if (file != null) {
                    val isLocked = withContext(Dispatchers.IO) { PdfUtils.isPdfLocked(file) }
                    val pageCount = if (isLocked) 0 else withContext(Dispatchers.IO) { PdfUtils.getPageCount(file) }
                    items.add(PdfItem(
                        id = UUID.randomUUID().toString(),
                        uri = uri,
                        fileName = fileName,
                        fileSize = fileSize,
                        pageCount = pageCount,
                        cachedFile = file,
                        isLocked = isLocked
                    ))
                }
            }
            isProcessing = false
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            addFiles(uris)
        }
    }

    LaunchedEffect(initialUris) {
        if (initialUris.isNotEmpty() && items.isEmpty()) {
            addFiles(initialUris)
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val item = items.removeAt(from.index)
        items.add(to.index, item)
    }

    BrewScaffold(
        title = "Quick Merge",
        subtitle = "Combine PDFs instantly",
        onBack = onBack,
        bottomBar = {
            if (items.isNotEmpty()) {
                ToolBottomBar(
                    leftIcon = Icons.Default.Add,
                    onLeftClick = { filePicker.launch(arrayOf("application/pdf")) },
                    leftContentDesc = "Add Files",
                    showClearButton = true,
                    onClearClick = {
                        items.forEach { it.cachedFile?.delete() }
                        items.clear()
                    },
                    actionText = "Merge PDFs",
                    isActionEnabled = items.size > 1 && !items.any { it.isLocked },
                    isProcessing = isProcessing,
                    actionColor = ToolMerge,
                    onActionClick = {
                        isProcessing = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val inputFiles = items.mapNotNull { it.cachedFile }
                                val outDir = File(context.cacheDir, "merge_output")
                                outDir.mkdirs()
                                val outFile = File(outDir, "preview_merge_${System.currentTimeMillis()}.pdf")
                                
                                withContext(Dispatchers.IO) {
                                    PdfUtils.mergePdfs(inputFiles, outFile)
                                }
                                
                                previewFile = outFile
                                showPreviewViewer = true
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Failed to merge PDFs"
                            } finally {
                                isProcessing = false
                            }
                        }
                    }
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (items.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BrewPickerPrompt(
                        icon = Icons.Rounded.Layers,
                        title = "Select PDFs to Merge",
                        subtitle = "Choose multiple files to combine",
                        accentColor = ToolMerge,
                        onClick = { filePicker.launch(arrayOf("application/pdf")) }
                    )
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        if (items.any { it.isLocked }) {
                            Text(
                                text = "Please unlock all files before merging. Currently, Quick Merge does not support unlocking. Use the Unlock tool first.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        } else if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                    itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                        ReorderableItem(reorderableLazyListState, key = item.id) { isDragging ->
                            PdfListItem(
                                item = item,
                                isDragging = isDragging,
                                onRemove = { 
                                    item.cachedFile?.delete()
                                    items.removeAt(index)
                                },
                                onClick = {}, // No viewer in quick merge
                                dragHandleModifier = Modifier.draggableHandle(),
                                modifier = Modifier
                                    .animateItem(
                                        fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                    )
                            )
                        }
                    }
                }
            }
            
            // Loading Overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = isProcessing,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BrewAmber)
                }
            }
        }
    }

    if (showPreviewViewer && previewFile != null) {
        com.pdfmerger.app.ui.component.PdfViewer(
            file = previewFile!!,
            fileName = "Preview - Merged PDF",
            onSave = {
                showPreviewViewer = false
                suggestedOutputName = FileProviderUtil.generateSmartName("merge", items.map { it.fileName })
                showRenameDialog = true
            },
            onDismiss = { showPreviewViewer = false }
        )
    }

    if (showRenameDialog && previewFile != null) {
        com.pdfmerger.app.ui.component.RenameDialog(
            suggestedName = suggestedOutputName,
            onConfirm = { finalName ->
                showRenameDialog = false
                isProcessing = true
                scope.launch {
                    try {
                        val outputUri = FileProviderUtil.saveToDownloads(context, previewFile!!, finalName)
                        if (outputUri != null) {
                            mergeResult = MergeResult(
                                fileName = finalName,
                                fileSize = previewFile!!.length(),
                                outputUri = outputUri,
                                localFile = previewFile!!
                            )
                        } else {
                            errorMessage = "Failed to save merged PDF"
                        }
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Failed to save PDF"
                    } finally {
                        isProcessing = false
                    }
                }
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    mergeResult?.let { result ->
        ToolResultSheet(
            title = "Merged Successfully!",
            result = result,
            sheetState = sheetState,
            onDismiss = { mergeResult = null },
            onShare = {
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(android.content.Intent.EXTRA_STREAM, result.outputUri)
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
                context.startActivity(viewIntent)
            }
        )
    }
}
