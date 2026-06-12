package com.pdfmerger.app.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Description
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StagingScreen(
    viewModel: MergeViewModel,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val showDeleteConfirmation = remember { mutableStateOf(false) }

    var pdfToUnlock by remember { mutableStateOf<PdfItem?>(null) }
    var pdfToView by remember { mutableStateOf<PdfItem?>(null) }

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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                StagingTopBar(
                    scrollBehavior = scrollBehavior,
                    itemCount = viewModel.pdfItems.size,
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
                onResetClick = { viewModel.reset(context) },
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

    viewModel.mergeResult.value?.let { result ->
        ToolResultSheet(
            title = "PDFs Merged Successfully!",
            result = result,
            sheetState = sheetState,
            onDismiss = { viewModel.clearMergeResult() },
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
                Text(
                    text = "Merge Stage",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
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
