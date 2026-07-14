package com.pdfmerger.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdfmerger.app.ui.screen.SessionSelectorSheet
import com.pdfmerger.app.ui.screen.*
import com.pdfmerger.app.ui.theme.BrewAmber
import com.pdfmerger.app.ui.theme.PdfMergerTheme
import com.pdfmerger.app.viewmodel.MergeViewModel

/**
 * Root composable for the brewPDF app.
 * Hub-based navigation: HomeScreen <-> individual tool screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfMergerApp(viewModel: MergeViewModel = viewModel()) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("brewPDF_prefs", Context.MODE_PRIVATE)

    LaunchedEffect(Unit) {
        viewModel.loadState(context)
    }

    var activeTool by remember { mutableStateOf<Tool?>(null) }
    var showOnboarding by remember { mutableStateOf(!prefs.getBoolean("has_seen_onboarding_v2_1", false)) }

    // ── Handle shared content via intent ──
    val sharedUris = viewModel.sharedUris.value
    val sharedMimeType = viewModel.sharedMimeType.value
    val sharedAction = viewModel.sharedAction.value

    // If viewing a PDF or sharing images, route directly
    LaunchedEffect(sharedUris, sharedMimeType, sharedAction) {
        if (sharedUris.isNotEmpty()) {
            if (sharedAction == Intent.ACTION_VIEW) {
                viewModel.pendingToolUris.value = sharedUris
                viewModel.sharedUris.value = emptyList()
                viewModel.sharedMimeType.value = ""
                viewModel.sharedAction.value = ""
                activeTool = Tool.PdfViewer
            } else if (sharedMimeType.startsWith("image/")) {
                viewModel.pendingToolUris.value = sharedUris
                viewModel.sharedUris.value = emptyList()
                viewModel.sharedMimeType.value = ""
                viewModel.sharedAction.value = ""
                activeTool = Tool.ImagesToPdf
            }
        }
    }

    var showStageSelectorForShare by remember { mutableStateOf(false) }

    // For PDFs (ACTION_SEND), show the disambiguation bottom sheet
    if (sharedUris.isNotEmpty() && !sharedMimeType.startsWith("image/") && sharedAction != Intent.ACTION_VIEW) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.sharedUris.value = emptyList() },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Open with brewPDF",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Pipeline option
                ShareOptionCard(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "Add to PDF Studio",
                    subtitle = "Chain bulk actions on PDFs",
                    accentColor = BrewAmber,
                    onClick = {
                        val currentDefaultId = prefs.getString("temp_default_session_id", null)
                        val expiry = prefs.getLong("temp_default_session_expiry", 0L)
                        val isValid = currentDefaultId != null && 
                            System.currentTimeMillis() < expiry &&
                            viewModel.sessions.any { it.id == currentDefaultId }
                        
                        if (isValid) {
                            viewModel.switchSession(currentDefaultId!!)
                            viewModel.addPdfs(sharedUris, context)
                            viewModel.sharedUris.value = emptyList()
                            activeTool = Tool.Pipeline
                        } else {
                            showStageSelectorForShare = true
                        }
                    }
                )

                // Tools option
                ShareOptionCard(
                    icon = Icons.Outlined.Build,
                    title = "Use a Tool",
                    subtitle = "Compress, extract, lock & more",
                    accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = {
                        viewModel.pendingToolUris.value = sharedUris
                        viewModel.sharedUris.value = emptyList()
                        activeTool = null
                    }
                )
            }
        }
    }

    if (showStageSelectorForShare) {
        com.pdfmerger.app.ui.screen.SessionSelectorSheet(
            viewModel = viewModel,
            onDismiss = { showStageSelectorForShare = false },
            context = context,
            isForShare = true,
            onSessionSelected = { sessionId, setAsDefault ->
                showStageSelectorForShare = false
                
                if (setAsDefault) {
                    prefs.edit()
                        .putString("temp_default_session_id", sessionId)
                        .putLong("temp_default_session_expiry", System.currentTimeMillis() + 10 * 60 * 1000)
                        .apply()
                }
                
                viewModel.addPdfs(sharedUris, context)
                viewModel.sharedUris.value = emptyList()
                activeTool = Tool.Pipeline
            }
        )
    }

    PdfMergerTheme {
        Crossfade(
            targetState = showOnboarding,
            animationSpec = tween(500),
            label = "onboarding_gate"
        ) { isOnboarding ->
            if (isOnboarding) {
                WelcomeScreen(onFinished = {
                    prefs.edit().putBoolean("has_seen_onboarding_v2_1", true).apply()
                    showOnboarding = false
                })
            } else {
        BackHandler(enabled = activeTool != null) {
            activeTool = null
        }

        AnimatedContent(
            targetState = activeTool,
            transitionSpec = {
                if (targetState != null) {
                    // Home -> Tool: slide up + fade
                    (slideInVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        initialOffsetY = { it / 6 }
                    ) + fadeIn(tween(250)))
                        .togetherWith(
                            fadeOut(tween(150))
                        )
                } else {
                    // Tool -> Home: fade in from top
                    (fadeIn(tween(250)) + slideInVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        initialOffsetY = { -it / 10 }
                    ))
                        .togetherWith(
                            slideOutVertically(
                                animationSpec = spring(stiffness = Spring.StiffnessLow),
                                targetOffsetY = { it / 6 }
                            ) + fadeOut(tween(150))
                        )
                }
            },
            label = "tool_transition"
        ) { currentTool ->
            if (currentTool == null) {
                HomeScreen(onToolSelected = { activeTool = it })
            } else {
                ToolRouter(
                    tool = currentTool,
                    viewModel = viewModel,
                    onBack = { activeTool = null },
                    onNavigateToTool = { activeTool = it },
                    onShowWelcome = { 
                        activeTool = null 
                        showOnboarding = true 
                    }
                )
            }
        }
            } // else
        } // Crossfade
    }
}

@Composable
private fun ShareOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Routes to the correct tool screen based on selection.
 */
@Composable
private fun ToolRouter(
    tool: Tool, 
    viewModel: MergeViewModel, 
    onBack: () -> Unit,
    onNavigateToTool: (Tool) -> Unit,
    onShowWelcome: () -> Unit
) {
    val context = LocalContext.current
    val pendingUris = viewModel.pendingToolUris.value
    val initialUri = pendingUris.firstOrNull()

    // Clear pending URIs once injected to prevent stale data
    LaunchedEffect(tool) {
        if (pendingUris.isNotEmpty() && tool != Tool.PdfViewer) {
            viewModel.pendingToolUris.value = emptyList()
        }
    }

    when (tool) {
        Tool.Pipeline -> StagingScreen(
            viewModel = viewModel,
            onShare = {
                viewModel.mergeResult.value?.let { result ->
                    shareFile(context, result.outputUri, result.fileName)
                }
            },
            onOpen = {
                viewModel.mergeResult.value?.let { result ->
                    openFile(context, result.outputUri)
                }
            },
            onBack = onBack
        )
        Tool.Merge -> MergeScreen(initialUris = pendingUris, onBack = onBack)
        Tool.Compress -> CompressScreen(initialUri = initialUri, onBack = onBack)
        Tool.Extract -> ExtractScreen(initialUri = initialUri, onBack = onBack)
        Tool.Split -> SplitScreen(initialUri = initialUri, onBack = onBack)
        Tool.ImagesToPdf -> ImagesToPdfScreen(initialUris = pendingUris, onBack = onBack)
        Tool.Encrypt -> EncryptScreen(initialUri = initialUri, onBack = onBack)
        Tool.PageEditor -> PageEditorScreen(initialUri = initialUri, onBack = onBack)
        Tool.PdfToImages -> PdfToImagesScreen(initialUri = initialUri, onBack = onBack)
        Tool.Unlock -> UnlockScreen(initialUri = initialUri, onBack = onBack)
        Tool.Watermark -> WatermarkScreen(initialUri = initialUri, onBack = onBack)
        Tool.PageNumbers -> PageNumbersScreen(initialUri = initialUri, onBack = onBack)
        Tool.Redact -> RedactScreen(initialUri = initialUri, onBack = onBack)
        Tool.ScanDocument -> ScanDocumentScreen(onBack = onBack)
        Tool.TextToPdf -> TextToPdfScreen(initialUri = initialUri, onBack = onBack)
        Tool.PdfViewer -> PdfViewerScreen(
            onBack = onBack,
            viewModel = viewModel,
            initialUri = initialUri,
            onNavigateToTool = { targetTool ->
                // The current viewer URI is already in pendingUris, so we don't clear it.
                onNavigateToTool(targetTool)
            }
        )
        Tool.PdfMaker -> PdfMakerScreen(onBack = onBack)
        Tool.Ocr -> { /* Coming soon, handled by HomeScreen toast */ onBack() }

        Tool.Settings -> SettingsScreen(onBack = onBack, onShowWelcome = onShowWelcome)
    }
}

private fun shareFile(context: Context, uri: Uri, fileName: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, fileName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
}

private fun openFile(context: Context, uri: Uri) {
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(viewIntent)
    } catch (e: Exception) {
        context.startActivity(Intent.createChooser(viewIntent, "Open with"))
    }
}
