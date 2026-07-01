package com.pdfmerger.app.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pdfmerger.app.ui.component.ToolBottomBar
import androidx.compose.material.icons.outlined.FolderOpen
import com.pdfmerger.app.ui.component.BrewFileCard
import com.pdfmerger.app.ui.component.BrewPickerPrompt
import com.pdfmerger.app.ui.component.BrewScaffold
import com.pdfmerger.app.ui.component.ToolResultSheet
import com.pdfmerger.app.ui.theme.ToolCompress
import com.pdfmerger.app.util.FileProviderUtil
import com.pdfmerger.app.util.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CompressionLevel(val label: String, val level: Int) {
    High("High Quality", 1),
    Medium("Balanced", 5),
    Web("Web Ready", 9)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressScreen(initialUri: Uri? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var originalSize by remember { mutableStateOf(0L) }
    var compressedSize by remember { mutableStateOf<Long?>(null) }
    var selectedLevel by remember { mutableStateOf(CompressionLevel.Medium) }
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
            originalSize = FileProviderUtil.getFileSize(context, uri)
            compressedSize = null
            isDone = false
            errorMessage = null
        
        }
    }
val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedUri = it
            fileName = FileProviderUtil.getFileName(context, it)
            originalSize = FileProviderUtil.getFileSize(context, it)
            compressedSize = null
            isDone = false
            errorMessage = null
        }
    }

    BrewScaffold(
        title = "Compress PDF",
        subtitle = "Shrink file size while keeping quality",
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
                        compressedSize = null
                        isDone = false
                        errorMessage = null
                    },
                    actionText = if (isDone) "Done ✓" else "Compress PDF",
                    isActionEnabled = !isProcessing && !isDone,
                    isProcessing = isProcessing,
                    actionColor = ToolCompress,
                    onActionClick = {
                        isProcessing = true
                        errorMessage = null
                        scope.launch {
                            try {
                            val cachedFile = FileProviderUtil.copyUriToStaging(context, selectedUri!!, "compress_input_${System.currentTimeMillis()}.pdf")
                            withContext(Dispatchers.IO) {
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                val outputFile = File(context.cacheDir, "compressed_${timestamp}.pdf")
                                PdfUtils.compressPdf(cachedFile!!, outputFile, selectedLevel.level)
                                val resultUri = FileProviderUtil.saveToDownloads(context, outputFile, "compressed_$fileName")
                                if (resultUri != null) {
                                    mergeResult = com.pdfmerger.app.viewmodel.MergeResult(
                                        fileName = "compressed_$fileName",
                                        fileSize = outputFile.length(),
                                        outputUri = resultUri,
                                        localFile = outputFile
                                    )
                                    compressedSize = outputFile.length()
                                }
                                cachedFile.delete()
                            }
                            isDone = true
                        } catch (e: Exception) {
                            errorMessage = e.localizedMessage ?: "Compression failed"
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
                icon = Icons.Outlined.Compress,
                title = "Select a PDF",
                subtitle = "Choose a file to compress",
                accentColor = ToolCompress,
                onClick = { filePicker.launch(arrayOf("application/pdf")) }
            )
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Spacer(modifier = Modifier.height(24.dp))
            BrewFileCard(fileName = fileName, fileSize = originalSize)
            Spacer(modifier = Modifier.height(24.dp))

            // Quality selector
            Text(
                text = "Compression Level",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CompressionLevel.entries.forEach { level ->
                    val isSelected = selectedLevel == level
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedLevel = level },
                        label = { Text(level.label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ToolCompress.copy(alpha = 0.2f),
                            selectedLabelColor = ToolCompress
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (compressedSize != null) {
                SizeComparisonCard(originalSize = originalSize, compressedSize = compressedSize!!)
            }

            Spacer(modifier = Modifier.weight(1f))

            errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
            }

            
        }
    }

    mergeResult?.let { result ->
        ToolResultSheet(
            title = "PDF Compressed!",
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

@Composable
fun SizeComparisonCard(originalSize: Long, compressedSize: Long) {
    val savings = ((1.0 - compressedSize.toDouble() / originalSize.toDouble()) * 100).coerceAtLeast(0.0)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Original", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(FileProviderUtil.formatFileSize(originalSize), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Compressed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(FileProviderUtil.formatFileSize(compressedSize), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = ToolCompress)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Saved", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = String.format("%.1f%%", savings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = ToolCompress
                )
            }
        }
    }
}
