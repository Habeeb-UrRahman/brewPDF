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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pdfmerger.app.model.PdfItem
import com.pdfmerger.app.model.PipelineAction
import com.pdfmerger.app.ui.component.PasswordDialog
import com.pdfmerger.app.ui.component.PdfListItem
import com.pdfmerger.app.ui.component.PdfViewer
import com.pdfmerger.app.ui.component.ToolResultSheet
import com.pdfmerger.app.ui.theme.BrewAmber
import com.pdfmerger.app.ui.theme.BrewTerracotta
import com.pdfmerger.app.ui.theme.BrewSage
import com.pdfmerger.app.util.FileProviderUtil
import com.pdfmerger.app.util.PipelineProgress
import com.pdfmerger.app.viewmodel.MergeViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.pdfmerger.app.util.PdfUtils
import coil.compose.AsyncImage
import androidx.compose.foundation.border
import com.pdfmerger.app.ui.component.SignatureDialog

// ── Data for pipeline action chips ──────────────────────────────────────

data class ActionChipData(
    val label: String,
    val icon: ImageVector,
    val category: String, // "pre", "core", "post", "terminal"
    val createDefault: () -> PipelineAction
)

val availablePipelineActions = listOf(
    ActionChipData("Watermark", Icons.Outlined.FontDownload, "pre") {
        PipelineAction.Watermark(color = android.graphics.Color.RED)
    },
    ActionChipData("Merge", Icons.Rounded.Layers, "core") {
        PipelineAction.Merge
    },
    ActionChipData("Page Numbers", Icons.Outlined.FormatListNumbered, "post") {
        PipelineAction.PageNumbers(format = "Page {n} of {total}", position = "bottom-center")
    },
    ActionChipData("Compress", Icons.Outlined.Compress, "post") {
        PipelineAction.Compress(quality = 9f)
    },
    ActionChipData("Lock", Icons.Outlined.Lock, "post") {
        PipelineAction.Lock(password = "")
    },
    ActionChipData("PDF → Images", Icons.Outlined.PhotoLibrary, "terminal") {
        PipelineAction.PdfToImages(format = "JPEG", dpi = 300)
    },
)

// ── Main Staging Screen ─────────────────────────────────────────────────

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
    var showActionConfig by remember { mutableStateOf(false) }
    var lockPasswordInput by remember { mutableStateOf("") }
    var watermarkTextInput by remember { mutableStateOf("") }

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
        val fromId = from.key as? String
        val toId = to.key as? String
        if (fromId != null && toId != null) {
            val fromIndex = viewModel.pdfItems.indexOfFirst { it.id == fromId }
            val toIndex = viewModel.pdfItems.indexOfFirst { it.id == toId }
            if (fromIndex != -1 && toIndex != -1) {
                viewModel.movePdf(fromIndex, toIndex, context)
            }
        }
    }

    // Storage calculation
    val totalSize = viewModel.pdfItems.sumOf { it.fileSize }
    val maxSize = 100L * 1024 * 1024
    val progress = (totalSize.toFloat() / maxSize.toFloat()).coerceIn(0f, 1f)

    val activeSession = viewModel.sessions.find { it.id == viewModel.activeSessionId.value }
    val activeSessionName = activeSession?.name ?: "PDF Studio"

    val pipelineProgress = viewModel.pipelineProgress.value
    
    val isExecuting = viewModel.isExecutingPipeline.value

    Box(modifier = Modifier.fillMaxSize()) {
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
                // Storage gauge
                AnimatedVisibility(visible = viewModel.pdfItems.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    StorageGauge(progress = progress, totalSize = totalSize)
                }
            }
        },
        bottomBar = {
            PipelineBottomBar(
                itemCount = viewModel.pdfItems.size,
                selectedActionCount = viewModel.selectedActions.size,
                isExecuting = isExecuting,
                onAddClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                onSortClick = { viewModel.toggleSort(context) },
                onResetClick = {
                    viewModel.reset(context)
                    previewFile?.delete()
                    previewFile = null
                },
                onExecuteClick = { viewModel.executePipeline(context) },
                onLegacyMergeClick = { viewModel.mergePdfs(context) }
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
                        top = 8.dp,
                        bottom = 200.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // ── Action Selector Panel ──
                    item {
                        PipelineActionPanel(
                            selectedActions = viewModel.selectedActions,
                            itemCount = viewModel.pdfItems.size,
                            onToggleAction = { chipData ->
                                val existing = viewModel.selectedActions.find {
                                    it.displayName == chipData.label
                                }
                                if (existing != null) {
                                    viewModel.selectedActions.remove(existing)
                                } else {
                                    // Terminal actions are exclusive with each other
                                    if (chipData.category == "terminal") {
                                        viewModel.selectedActions.removeAll { it.order >= 400 }
                                    }
                                    val action = chipData.createDefault()
                                    viewModel.selectedActions.add(action)
                                    if (action is PipelineAction.Watermark || action is PipelineAction.Lock || action is PipelineAction.Compress || action is PipelineAction.PdfToImages) {
                                        showActionConfig = true
                                    }
                                }
                            },
                            onConfigureClick = { showActionConfig = true }
                        )
                    }

                    // ── Pipeline Summary ──
                    if (viewModel.selectedActions.isNotEmpty()) {
                        item {
                            PipelineSummary(
                                actions = viewModel.selectedActions,
                                fileCount = viewModel.pdfItems.size
                            )
                        }
                    }

                    // ── File List Header ──
                    item {
                        Text(
                            text = "STUDIO FILES",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
                        )
                    }

                    // ── File List ──
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

            // ── Pipeline Executing Overlay ──
            AnimatedVisibility(
                visible = isExecuting,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { pipelineProgress?.overallPercent ?: 0f },
                            modifier = Modifier.size(80.dp),
                            color = BrewAmber,
                            strokeWidth = 6.dp,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                        Text(
                            text = pipelineProgress?.currentStep ?: "Processing…",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        pipelineProgress?.currentFile?.let { fileName ->
                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${((pipelineProgress?.overallPercent ?: 0f) * 100).toInt()}%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = BrewAmber
                        )
                    }
                }
            }

            // ── Legacy merging overlay ──
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

    // ── Action Configuration Sheet ──
    if (showActionConfig) {
        ActionConfigSheet(
            selectedActions = viewModel.selectedActions,
            onDismiss = { showActionConfig = false },
            onUpdateAction = { old, new ->
                val index = viewModel.selectedActions.indexOf(old)
                if (index != -1) {
                    viewModel.selectedActions[index] = new
                }
            }
        )
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
                viewModel.mergePdfs(context)
            },
            onDismiss = { showPreviewViewer = false }
        )
    }

    // ── Pipeline Preview ─────────────────────────────────────────────────
    // When the pipeline finishes, show the output in PdfViewer for preview.
    // Only for single-PDF output; multi-file or image outputs save directly.
    val pipelineResult = viewModel.pipelineResult.value
    var showPipelineRenameDialog by remember { mutableStateOf(false) }
    var suggestedPipelineName by remember { mutableStateOf("") }
    
    if (pipelineResult != null && viewModel.mergeResult.value == null) {
        if (!pipelineResult.isImages && pipelineResult.outputFiles.size == 1) {
            val outputFile = pipelineResult.outputFiles.first()
            if (outputFile.exists()) {
                if (showPipelineRenameDialog) {
                    com.pdfmerger.app.ui.component.RenameDialog(
                        suggestedName = suggestedPipelineName,
                        onConfirm = { finalName ->
                            showPipelineRenameDialog = false
                            viewModel.savePipelineResult(context, finalName)
                        },
                        onDismiss = {
                            showPipelineRenameDialog = false
                        }
                    )
                } else {
                    PdfViewer(
                        file = outputFile,
                        fileName = "Preview - Pipeline Output",
                        onSave = {
                            suggestedPipelineName = FileProviderUtil.generateSmartName("pipeline", viewModel.pdfItems.map { it.fileName })
                            showPipelineRenameDialog = true
                        },
                        onDismiss = {
                            viewModel.clearPipelineResult()
                        }
                    )
                }
            }
        } else {
            // Multi-file or image output — save immediately, no single-page preview
            // But we should prompt for rename first
            if (showPipelineRenameDialog) {
                com.pdfmerger.app.ui.component.RenameDialog(
                    suggestedName = suggestedPipelineName,
                    onConfirm = { finalName ->
                        showPipelineRenameDialog = false
                        viewModel.savePipelineResult(context, finalName)
                        viewModel.clearPipelineResult()
                    },
                    onDismiss = {
                        showPipelineRenameDialog = false
                        viewModel.clearPipelineResult()
                    }
                )
            } else {
                LaunchedEffect(pipelineResult) {
                    suggestedPipelineName = FileProviderUtil.generateSmartName("pipeline", viewModel.pdfItems.map { it.fileName })
                    showPipelineRenameDialog = true
                }
            }
        }
    }

    // ── Result Sheet (after save) ────────────────────────────────────────
    viewModel.mergeResult.value?.let { result ->
        ToolResultSheet(
            title = "Studio Complete!",
            result = result,
            sheetState = sheetState,
            onDismiss = {
                viewModel.clearMergeResult()
                viewModel.clearPipelineResult()
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
            title = { Text("Clear Studio", fontWeight = FontWeight.Bold) },
            text = { Text("Remove all PDFs and reset the studio?") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearStageDialog.value = false
                        viewModel.reset(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrewAmber, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear Studio", fontWeight = FontWeight.Bold)
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

    // Intro overlay — rendered ON TOP of everything via the wrapping Box
    val prefs = context.getSharedPreferences("pdf_merger_prefs", Context.MODE_PRIVATE)
    var showIntroOverlay by remember { 
        mutableStateOf(!prefs.getBoolean("has_seen_studio_intro_v2.3.1", false))
    }
    if (showIntroOverlay) {
        com.pdfmerger.app.ui.component.PdfStudioIntroOverlay(
            onDismiss = {
                showIntroOverlay = false
                prefs.edit().putBoolean("has_seen_studio_intro_v2.3.1", true).apply()
            }
        )
    }

    } // end Box
}

// ── Pipeline Action Panel ───────────────────────────────────────────────

@Composable
fun PipelineActionPanel(
    selectedActions: List<PipelineAction>,
    itemCount: Int,
    onToggleAction: (ActionChipData) -> Unit,
    onConfigureClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STUDIO ACTIONS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (selectedActions.isNotEmpty()) {
                    TextButton(onClick = onConfigureClick) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Configure", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Pre-file actions row
            Text(
                text = "Per-File",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val preChips = availablePipelineActions.filter { it.category == "pre" }
                items(preChips.size) { index ->
                    val chip = preChips[index]
                    val isSelected = selectedActions.any { it.displayName == chip.label }
                    PipelineChip(
                        label = chip.label,
                        icon = chip.icon,
                        isSelected = isSelected,
                        onClick = { onToggleAction(chip) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Core + Post + Terminal in a flow
            Text(
                text = "Core & Post-Processing",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val chips = availablePipelineActions.filter { it.category != "pre" }
                items(chips.size) { index ->
                    val chip = chips[index]
                    val isSelected = selectedActions.any { it.displayName == chip.label }
                    PipelineChip(
                        label = chip.label,
                        icon = chip.icon,
                        isSelected = isSelected,
                        onClick = { onToggleAction(chip) },
                        accentColor = when (chip.category) {
                            "core" -> BrewAmber
                            "terminal" -> BrewTerracotta
                            else -> BrewSage
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineChip(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    accentColor: Color = BrewAmber
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accentColor.copy(alpha = 0.15f),
            selectedLabelColor = accentColor,
            selectedLeadingIconColor = accentColor
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

// ── Pipeline Summary ────────────────────────────────────────────────────

@Composable
fun PipelineSummary(
    actions: List<PipelineAction>,
    fileCount: Int
) {
    val sorted = actions.sortedBy { it.order }
    val hasMerge = sorted.any { it is PipelineAction.Merge }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = BrewAmber.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = "STUDIO RECIPE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = BrewAmber
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Build the visual flow
            val steps = mutableListOf<String>()
            steps.add("$fileCount File${if (fileCount != 1) "s" else ""}")
            sorted.forEach { steps.add(it.displayName) }
            if (hasMerge) {
                steps.add("1 Output")
            } else {
                steps.add("$fileCount Output${if (fileCount != 1) "s" else ""}")
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                steps.forEachIndexed { index, step ->
                    Text(
                        text = step,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (index == 0 || index == steps.lastIndex) FontWeight.Bold else FontWeight.Medium,
                        color = if (index == 0 || index == steps.lastIndex)
                            MaterialTheme.colorScheme.onSurface
                        else BrewAmber,
                        maxLines = 1
                    )
                    if (index < steps.lastIndex) {
                        Text(
                            text = " → ",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

// ── Action Configuration Sheet ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionConfigSheet(
    selectedActions: List<PipelineAction>,
    onDismiss: () -> Unit,
    onUpdateAction: (PipelineAction, PipelineAction) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stampsDir = remember { File(context.filesDir, "stamps").apply { mkdirs() } }
    var savedStamps by remember { mutableStateOf(stampsDir.listFiles()?.toList() ?: emptyList()) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var pendingStampToSave by remember { mutableStateOf<File?>(null) }
    var currentWatermarkAction by remember { mutableStateOf<PipelineAction.Watermark?>(null) }

    val stampPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch {
                withContext(Dispatchers.IO) {
                    val file = FileProviderUtil.copyUriToStaging(context, it, "temp_stamp_${System.currentTimeMillis()}.png")
                    if (file != null) {
                        pendingStampToSave = file
                    }
                }
            }
        }
    }

    if (showSignatureDialog) {
        SignatureDialog(
            onDismiss = { showSignatureDialog = false },
            onSave = { file ->
                showSignatureDialog = false
                pendingStampToSave = file
            },
            cacheDir = context.cacheDir
        )
    }

    if (pendingStampToSave != null) {
        AlertDialog(
            onDismissRequest = { pendingStampToSave = null },
            title = { Text("Save Stamp") },
            text = { Text("Do you want to save this stamp/signature to your library for future use?") },
            confirmButton = {
                TextButton(onClick = {
                    val dest = File(stampsDir, "stamp_${System.currentTimeMillis()}.png")
                    pendingStampToSave?.copyTo(dest)
                    savedStamps = stampsDir.listFiles()?.toList() ?: emptyList()
                    currentWatermarkAction?.let { action ->
                        onUpdateAction(action, action.copy(stampPath = dest.absolutePath))
                    }
                    pendingStampToSave = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    currentWatermarkAction?.let { action ->
                        onUpdateAction(action, action.copy(stampPath = pendingStampToSave?.absolutePath))
                    }
                    pendingStampToSave = null 
                }) { Text("Just Use Once") }
            }
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Configure Actions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            selectedActions.sortedBy { it.order }.forEach { action ->
                when (action) {
                    is PipelineAction.Watermark -> {
                        currentWatermarkAction = action
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Watermark", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                TabRow(selectedTabIndex = if (action.isImageMode) 1 else 0, containerColor = Color.Transparent, contentColor = BrewAmber) {
                                    Tab(selected = !action.isImageMode, onClick = { onUpdateAction(action, action.copy(isImageMode = false)) }, text = { Text("Text") })
                                    Tab(selected = action.isImageMode, onClick = { onUpdateAction(action, action.copy(isImageMode = true)) }, text = { Text("Stamp / Signature") })
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                if (action.isImageMode) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                    ) {
                                        items(savedStamps) { stampFile ->
                                            val isSelected = action.stampPath == stampFile.absolutePath
                                            Box(
                                                modifier = Modifier
                                                    .size(60.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(
                                                        width = if (isSelected) 2.dp else 1.dp,
                                                        color = if (isSelected) BrewAmber else MaterialTheme.colorScheme.outlineVariant,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .background(Color.White)
                                                    .clickable { onUpdateAction(action, action.copy(stampPath = stampFile.absolutePath)) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AsyncImage(
                                                    model = stampFile,
                                                    contentDescription = "Saved Stamp",
                                                    modifier = Modifier.fillMaxSize().padding(4.dp)
                                                )
                                                if (isSelected) {
                                                    IconButton(
                                                        onClick = {
                                                            stampFile.delete()
                                                            savedStamps = stampsDir.listFiles()?.toList() ?: emptyList()
                                                            onUpdateAction(action, action.copy(stampPath = null))
                                                        },
                                                        modifier = Modifier.align(Alignment.TopEnd).size(20.dp).background(Color(0x88000000), CircleShape)
                                                    ) {
                                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(12.dp))
                                                    }
                                                }
                                            }
                                        }
                                        item {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { stampPicker.launch(arrayOf("image/*")) }
                                                    .padding(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Outlined.Image, contentDescription = "Pick Image", tint = BrewAmber)
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Image", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        item {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { showSignatureDialog = true }
                                                    .padding(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Outlined.Create, contentDescription = "Draw Signature", tint = BrewAmber)
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Draw", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text("Scale: ${(action.stampScale * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                                    Slider(
                                        value = action.stampScale,
                                        onValueChange = { onUpdateAction(action, action.copy(stampScale = it)) },
                                        valueRange = 0.1f..3.0f,
                                        colors = SliderDefaults.colors(thumbColor = BrewAmber, activeTrackColor = BrewAmber)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onUpdateAction(action, action.copy(removeBackground = !action.removeBackground)) }) {
                                        Checkbox(checked = action.removeBackground, onCheckedChange = { onUpdateAction(action, action.copy(removeBackground = it)) }, colors = CheckboxDefaults.colors(checkedColor = BrewAmber))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Make white background transparent", style = MaterialTheme.typography.bodyLarge)
                                    }
                                } else {
                                    OutlinedTextField(
                                        value = action.text,
                                        onValueChange = { onUpdateAction(action, action.copy(text = it)) },
                                        label = { Text("Watermark Text") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Color", style = MaterialTheme.typography.titleSmall)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        listOf(
                                            android.graphics.Color.RED to Color(0xFFE53935),
                                            android.graphics.Color.BLACK to Color(0xFF212121),
                                            android.graphics.Color.BLUE to Color(0xFF1E88E5),
                                            android.graphics.Color.GRAY to Color(0xFF757575)
                                        ).forEach { (androidColor, composeColor) ->
                                            val isSelected = action.color == androidColor
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(composeColor)
                                                    .clickable { onUpdateAction(action, action.copy(color = androidColor)) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Color.White))
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Font Size: ${action.fontSize.toInt()}", style = MaterialTheme.typography.titleSmall)
                                    Slider(
                                        value = action.fontSize,
                                        onValueChange = { onUpdateAction(action, action.copy(fontSize = it)) },
                                        valueRange = 12f..150f,
                                        colors = SliderDefaults.colors(thumbColor = BrewAmber, activeTrackColor = BrewAmber)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Opacity: ${(action.opacity * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                                Slider(
                                    value = action.opacity,
                                    onValueChange = { onUpdateAction(action, action.copy(opacity = it)) },
                                    valueRange = 0.1f..1.0f,
                                    colors = SliderDefaults.colors(thumbColor = BrewAmber, activeTrackColor = BrewAmber)
                                )
                            }
                        }
                    }
                    is PipelineAction.Lock -> {
                        var password by remember { mutableStateOf(action.password) }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Lock PDF", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = {
                                        password = it
                                        onUpdateAction(action, action.copy(password = it))
                                    },
                                    label = { Text("Password") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                    is PipelineAction.Compress -> {
                        var quality by remember { mutableStateOf(action.quality) }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Compress", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Compression Level: ${quality.toInt()}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Slider(
                                    value = quality,
                                    onValueChange = {
                                        quality = it
                                        onUpdateAction(action, action.copy(quality = it))
                                    },
                                    valueRange = 1f..9f,
                                    steps = 7,
                                    colors = SliderDefaults.colors(
                                        thumbColor = BrewAmber,
                                        activeTrackColor = BrewAmber
                                    )
                                )
                            }
                        }
                    }
                    is PipelineAction.PdfToImages -> {
                        var format by remember { mutableStateOf(action.format) }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("PDF → Images", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("JPEG", "PNG").forEach { fmt ->
                                        FilterChip(
                                            selected = format == fmt,
                                            onClick = {
                                                format = fmt
                                                onUpdateAction(action, action.copy(format = fmt))
                                            },
                                            label = { Text(fmt) },
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Merge, PageNumbers, ExtractPages — no configuration needed
                    else -> {}
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BrewAmber),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Pipeline Bottom Bar ─────────────────────────────────────────────────

@Composable
fun PipelineBottomBar(
    itemCount: Int,
    selectedActionCount: Int,
    isExecuting: Boolean,
    onAddClick: () -> Unit,
    onSortClick: () -> Unit,
    onResetClick: () -> Unit,
    onExecuteClick: () -> Unit,
    onLegacyMergeClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                BarIconButton(icon = Icons.Outlined.Add, onClick = onAddClick, contentDesc = "Add PDF")
                BarIconButton(
                    icon = Icons.AutoMirrored.Outlined.Sort,
                    onClick = onSortClick,
                    enabled = itemCount > 1,
                    contentDesc = "Sort"
                )
                BarIconButton(
                    icon = Icons.Outlined.DeleteOutline,
                    onClick = onResetClick,
                    enabled = itemCount > 0,
                    tint = MaterialTheme.colorScheme.error,
                    contentDesc = "Clear"
                )
            }

            if (selectedActionCount > 0) {
                // Pipeline Execute button
                Button(
                    onClick = onExecuteClick,
                    enabled = itemCount > 0 && !isExecuting,
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .height(44.dp)
                        .background(
                            brush = Brush.linearGradient(listOf(BrewAmber, BrewTerracotta)),
                            shape = RoundedCornerShape(14.dp),
                            alpha = if (itemCount > 0 && !isExecuting) 1f else 0.4f
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 18.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text(
                            "Execute ($selectedActionCount)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            } else {
                // Fallback: classic Merge button
                Button(
                    onClick = onLegacyMergeClick,
                    enabled = itemCount > 1 && !isExecuting,
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .height(44.dp)
                        .background(
                            brush = Brush.linearGradient(listOf(BrewAmber, BrewTerracotta)),
                            shape = RoundedCornerShape(14.dp),
                            alpha = if (itemCount > 1 && !isExecuting) 1f else 0.4f
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 18.dp)
                    ) {
                        Icon(Icons.Rounded.Layers, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text("Execute", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun BarIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    contentDesc: String
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = if (enabled) tint else tint.copy(alpha = 0.25f),
            modifier = Modifier.size(22.dp)
        )
    }
}

// ── Reused Sub-components (StorageGauge, TopBar, EmptyState, SessionSelector) ──

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
                        contentDescription = "Switch Session"
                    )
                }
                if (itemCount > 0) {
                    Text(
                        text = "$itemCount file${if (itemCount != 1) "s" else ""} added",
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
            text = "Studio is Empty",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Add PDFs here, then build a pipeline to process them in bulk.\nShare from any app or tap the + button.",
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

// ── Session Selector Sheet ──────────────────────────────────────────────

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
    var showRenameDialog by remember { mutableStateOf<String?>(null) }
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
                text = "Studio Sessions",
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
                    Checkbox(
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
                item {
                    ListItem(
                        modifier = Modifier.clickable {
                            showNewSessionDialog = true
                        },
                        headlineContent = { Text("New Session") },
                        leadingContent = {
                            Icon(Icons.Outlined.Add, contentDescription = null, tint = BrewAmber)
                        }
                    )
                }
            }
        }
    }

    if (showNewSessionDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewSessionDialog = false },
            title = { Text("New Session") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Session Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.createNewSession(newName.trim(), context)
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
                title = { Text("Rename Session") },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Session Name") },
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
