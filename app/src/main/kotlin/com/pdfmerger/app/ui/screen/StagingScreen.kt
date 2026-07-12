package com.pdfmerger.app.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pdfmerger.app.model.PdfItem
import com.pdfmerger.app.ui.component.PasswordDialog
import com.pdfmerger.app.ui.component.PdfListItem
import com.pdfmerger.app.ui.component.PdfViewer
import com.pdfmerger.app.ui.component.StagingBottomBar
import com.pdfmerger.app.ui.component.ToolResultSheet
import com.pdfmerger.app.ui.theme.BrewAmber
import com.pdfmerger.app.ui.theme.BrewTerracotta
import com.pdfmerger.app.util.FileProviderUtil
import com.pdfmerger.app.viewmodel.MergeViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.pdfmerger.app.util.PdfUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StagingScreen(
    viewModel: MergeViewModel,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val showDeleteConfirmation = remember { mutableStateOf(false) }
    val showClearStageDialog = remember { mutableStateOf(false) }
    val showSessionSelector = remember { mutableStateOf(false) }

    var pdfToUnlock by remember { mutableStateOf<PdfItem?>(null) }
    var pdfToView by remember { mutableStateOf<PdfItem?>(null) }
    
    var showPreviewViewer by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<File?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addPdfs(uris, context)
        }
    }

    val snackbarMessage = viewModel.snackbarMessage.value
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadState(context)
    }

    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.movePdf(from.index, to.index, context)
    }

    // Storage calculation
    val totalSize = viewModel.pdfItems.sumOf { it.fileSize }
    val maxSize = 100L * 1024 * 1024
    val progress = (totalSize.toFloat() / maxSize.toFloat()).coerceIn(0f, 1f)

    val activeSession = viewModel.sessions.find { it.id == viewModel.activeSessionId.value }
    val activeSessionName = activeSession?.name ?: "Merge Stage"

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                StagingTopBar(
                    scrollBehavior = scrollBehavior,
                    itemCount = viewModel.pdfItems.size,
                    activeSessionName = activeSessionName,
                    onSessionSelectorClick = { showSessionSelector.value = true },
                    onBack = onBack
                )
                // Animated Storage Gauge
                AnimatedVisibility(visible = viewModel.pdfItems.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    StorageGauge(progress = progress, totalSize = totalSize)
                }
            }
        },
        bottomBar = {
            StagingBottomBar(
                itemCount = viewModel.pdfItems.size,
                isSortAscending = viewModel.sortAscending.value,
                onAddClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                onSortClick = { viewModel.toggleSort(context) },
                onSaveClick = { viewModel.saveStagedToDownloads(context) },
                onResetClick = { 
                    viewModel.reset(context)
                    previewFile?.delete()
                    previewFile = null
                },
                onPreviewClick = {
                    val inputFiles = viewModel.pdfItems.mapNotNull { it.cachedFile }
                    if (inputFiles.size >= 2 && !viewModel.pdfItems.any { it.isLocked }) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val outputFile = File(context.cacheDir, "preview_merged_${System.currentTimeMillis()}.pdf")
                                PdfUtils.mergePdfs(inputFiles, outputFile)
                                withContext(Dispatchers.Main) {
                                    previewFile?.delete()
                                    previewFile = outputFile
                                    showPreviewViewer = true
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    viewModel.snackbarMessage.value = "Failed to generate preview"
                                }
                            }
                        }
                    } else if (viewModel.pdfItems.any { it.isLocked }) {
                        viewModel.snackbarMessage.value = "Please unlock all PDFs before previewing."
                    } else {
                        viewModel.snackbarMessage.value = "Add at least 2 PDFs to preview."
                    }
                },
                onMergeClick = { viewModel.mergePdfs(context) },
                isMerging = viewModel.isMerging.value
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (viewModel.pdfItems.isEmpty() && !viewModel.isLoading.value) {
                EmptyState(
                    onAddClick = {
                        filePickerLauncher.launch(arrayOf("application/pdf"))
                    }
                )
            } else {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 120.dp  // extra space for FAB
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = viewModel.pdfItems,
                        key = { _, item -> item.id }
                    ) { _, item ->
                        ReorderableItem(
                            reorderableLazyListState,
                            key = item.id
                        ) { isDragging ->
                            PdfListItem(
                                item = item,
                                isDragging = isDragging,
                                onRemove = { viewModel.removePdf(item, context) },
                                onClick = {
                                    if (item.isLocked) {
                                        pdfToUnlock = item
                                    } else {
                                        pdfToView = item
                                    }
                                },
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

            AnimatedVisibility(
                visible = viewModel.isMerging.value,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = BrewAmber,
                            strokeWidth = 6.dp
                        )
                        Text(
                            text = "Fusing PDFs…",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = viewModel.isLoading.value,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 4.dp,
                    color = BrewAmber
                )
            }
        }
    }

    pdfToUnlock?.let { item ->
        PasswordDialog(
            fileName = item.fileName,
            onDismiss = { pdfToUnlock = null },
            onSubmit = { password ->
                viewModel.attemptUnlock(item, password, context)
                pdfToUnlock = null
            }
        )
    }

    pdfToView?.cachedFile?.let { file ->
        PdfViewer(
            file = file,
            fileName = pdfToView!!.fileName,
            onDismiss = { pdfToView = null }
        )
    }

    if (showPreviewViewer && previewFile != null) {
        PdfViewer(
            file = previewFile!!,
            fileName = "Preview - Merged PDF",
            onSave = {
                showPreviewViewer = false
                viewModel.mergePdfs(context) // Calling mergePdfs directly triggers the real save dialog
            },
            onDismiss = { showPreviewViewer = false }
        )
    }

    viewModel.mergeResult.value?.let { result ->
        ToolResultSheet(
            title = "PDFs Merged Successfully!",
            result = result,
            sheetState = sheetState,
            onDismiss = {
                viewModel.clearMergeResult()
                showClearStageDialog.value = true
            },
            onShare = onShare,
            onOpen = onOpen,
            onDeleteOriginals = {
                showDeleteConfirmation.value = true
            }
        )
    }

    if (showDeleteConfirmation.value) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation.value = false },
            title = { Text("Delete Original Files?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete the individual source PDF files from your device? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation.value = false
                        viewModel.clearMergeResult()
                        viewModel.deleteSourceFiles(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation.value = false }) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    if (showClearStageDialog.value) {
        AlertDialog(
            onDismissRequest = { showClearStageDialog.value = false },
            title = { Text("Clear Stage", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove all staged PDFs? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearStageDialog.value = false
                        viewModel.reset(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrewAmber,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear Stage", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearStageDialog.value = false }) {
                    Text("Keep Files", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    if (showSessionSelector.value) {
        SessionSelectorSheet(
            viewModel = viewModel,
            onDismiss = { showSessionSelector.value = false },
            context = context
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSelectorSheet(
    viewModel: MergeViewModel,
    onDismiss: () -> Unit,
    context: android.content.Context,
    isForShare: Boolean = false,
    onSessionSelected: ((String, Boolean) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var showNewSessionDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<String?>(null) } // holds session ID to rename
    var setAsDefault by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Merge Stages",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            if (isForShare) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { setAsDefault = !setAsDefault }
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = setAsDefault,
                        onCheckedChange = { setAsDefault = it }
                    )
                    Text("Set as default for 10 minutes", style = MaterialTheme.typography.bodyMedium)
                }
            }
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                items(viewModel.sessions.size) { index ->
                    val session = viewModel.sessions[index]
                    val isSelected = session.id == viewModel.activeSessionId.value
                    
                    ListItem(
                        modifier = Modifier.clickable {
                            if (!isSelected || onSessionSelected != null) {
                                viewModel.switchSession(session.id)
                                if (onSessionSelected != null) {
                                    onSessionSelected(session.id, setAsDefault)
                                } else {
                                    onDismiss()
                                }
                            }
                        },
                        headlineContent = { 
                            Text(
                                session.name, 
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) BrewAmber else MaterialTheme.colorScheme.onSurface
                            ) 
                        },
                        supportingContent = {
                            Text("${session.items.size} file${if (session.items.size != 1) "s" else ""}")
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { showRenameDialog = session.id }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Rename")
                                }
                                if (viewModel.sessions.size > 1) {
                                    IconButton(onClick = { 
                                        viewModel.deleteSession(session.id, context)
                                        if (isSelected) onDismiss()
                                    }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                                    }
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { showNewSessionDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrewAmber)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Stage")
            }
        }
    }

    if (showNewSessionDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewSessionDialog = false },
            title = { Text("New Stage") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Stage Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.createNewSession(name.trim(), context)
                        showNewSessionDialog = false
                        if (onSessionSelected != null) {
                            onSessionSelected(viewModel.activeSessionId.value!!, setAsDefault)
                        } else {
                            onDismiss()
                        }
                    }
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewSessionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    showRenameDialog?.let { sessionId ->
        val session = viewModel.sessions.find { it.id == sessionId }
        if (session != null) {
            var name by remember { mutableStateOf(session.name) }
            AlertDialog(
                onDismissRequest = { showRenameDialog = null },
                title = { Text("Rename Stage") },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Stage Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (name.isNotBlank()) {
                            viewModel.renameSession(sessionId, name.trim(), context)
                            showRenameDialog = null
                        }
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun StorageGauge(progress: Float, totalSize: Long) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "progress"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Storage Used",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${FileProviderUtil.formatFileSize(totalSize)} / 100 MB",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(BrewAmber, BrewTerracotta)))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StagingTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    itemCount: Int,
    activeSessionName: String,
    onSessionSelectorClick: () -> Unit,
    onBack: () -> Unit
) {
    LargeTopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
        },
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onSessionSelectorClick)
                        .padding(end = 8.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = activeSessionName,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Switch Stage"
                    )
                }
                if (itemCount > 0) {
                    Text(
                        text = "$itemCount file${if (itemCount != 1) "s" else ""} staged",
                        style = MaterialTheme.typography.titleMedium,
                        color = BrewAmber
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    )
}

@Composable
private fun EmptyState(onAddClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            BrewAmber.copy(alpha = alpha),
                            BrewTerracotta.copy(alpha = alpha)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                tint = BrewAmber,
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "No PDFs Staged",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Share PDFs from any app or tap the button below to start fusing.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onAddClick,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Add PDFs",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
