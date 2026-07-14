package com.pdfmerger.app.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pdfmerger.app.ui.component.ToolBottomBar
import androidx.compose.material.icons.outlined.FolderOpen
import com.pdfmerger.app.ui.component.BrewFileCard
import com.pdfmerger.app.ui.component.BrewPickerPrompt
import com.pdfmerger.app.ui.component.BrewScaffold
import com.pdfmerger.app.ui.component.ToolResultSheet
import com.pdfmerger.app.ui.theme.ToolPageNumbers
import com.pdfmerger.app.ui.component.RenameDialog
import com.pdfmerger.app.util.FileProviderUtil
import com.pdfmerger.app.util.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageNumbersScreen(initialUri: Uri? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var fileSize by remember { mutableStateOf(0L) }
    var isProcessing by remember { mutableStateOf(false) }
    var isDone by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var mergeResult by remember { mutableStateOf<com.pdfmerger.app.viewmodel.MergeResult?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showRenameDialog by remember { mutableStateOf(false) }
    var suggestedOutputName by remember { mutableStateOf("") }
    
    var showPreviewViewer by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<File?>(null) }

    
    LaunchedEffect(initialUri) {
        if (initialUri != null && selectedUri == null) {
            val uri = initialUri

            selectedUri = uri
            fileName = FileProviderUtil.getFileName(context, uri)
            fileSize = FileProviderUtil.getFileSize(context, uri)
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
            fileSize = FileProviderUtil.getFileSize(context, it)
            isDone = false
            errorMessage = null
        }
    }

    BrewScaffold(
        title = "Page Numbers",
        subtitle = "Add page numbers to the bottom of all pages",
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
                        isDone = false
                        errorMessage = null
                        previewFile?.delete()
                        previewFile = null
                    },
                    showPreviewButton = true,
                    onPreviewClick = {
                        isProcessing = true
                        errorMessage = null
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    val inputFile = FileProviderUtil.copyUriToStaging(context, selectedUri!!, "preview_numbers_input_${System.currentTimeMillis()}.pdf")
                                        ?: throw Exception("Failed to read file")
                                    val outputFile = File(context.cacheDir, "preview_numbered_${System.currentTimeMillis()}.pdf")
                                    PdfUtils.addPageNumbers(inputFile, outputFile)
                                    inputFile.delete()
                                    previewFile?.delete()
                                    previewFile = outputFile
                                }
                                showPreviewViewer = true
                            } catch (e: Exception) {
                                errorMessage = e.localizedMessage ?: "Failed to generate preview"
                            }
                            isProcessing = false
                        }
                    },
                    actionText = if (isDone) "Done ✓" else "Save PDF",
                    isActionEnabled = !isProcessing && !isDone,
                    isProcessing = isProcessing,
                    actionColor = com.pdfmerger.app.ui.theme.ToolPageNumbers,
                    onActionClick = {
                        suggestedOutputName = FileProviderUtil.generateSmartName("page_numbers", listOf(fileName))
                        showRenameDialog = true
                    }
                )
            }
        }
    ) {
        if (selectedUri == null) {
            Spacer(modifier = Modifier.weight(1f))
            BrewPickerPrompt(
                icon = Icons.Outlined.FormatListNumbered,
                title = "Select a PDF",
                subtitle = "Choose a file to number",
                accentColor = ToolPageNumbers,
                onClick = { filePicker.launch(arrayOf("application/pdf")) }
            )
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Spacer(modifier = Modifier.height(24.dp))
            BrewFileCard(fileName = fileName, fileSize = fileSize)
            Spacer(modifier = Modifier.height(24.dp))

            Spacer(modifier = Modifier.weight(1f))

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
            }

        }
    }

    if (showPreviewViewer && previewFile != null) {
        com.pdfmerger.app.ui.component.PdfViewer(
            file = previewFile!!,
            fileName = "Preview - Page Numbers",
            onSave = {
                showPreviewViewer = false
                suggestedOutputName = FileProviderUtil.generateSmartName("page_numbers", listOf(fileName))
                showRenameDialog = true
            },
            onDismiss = { showPreviewViewer = false }
        )
    }

    mergeResult?.let { result ->
        ToolResultSheet(
            title = "Page Numbers Added!",
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

    if (showRenameDialog) {
        RenameDialog(
            suggestedName = suggestedOutputName,
            onConfirm = { finalName ->
                showRenameDialog = false
                isProcessing = true
                errorMessage = null
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val inputFile = FileProviderUtil.copyUriToStaging(context, selectedUri!!, "numbers_input_${System.currentTimeMillis()}.pdf")
                                ?: throw Exception("Failed to read file")
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            val outputFile = File(context.cacheDir, "numbered_${timestamp}.pdf")
                            PdfUtils.addPageNumbers(inputFile, outputFile)
                            val resultUri = FileProviderUtil.saveToDownloads(context, outputFile, finalName)
                            if (resultUri != null) {
                                mergeResult = com.pdfmerger.app.viewmodel.MergeResult(
                                    fileName = finalName,
                                    fileSize = outputFile.length(),
                                    outputUri = resultUri,
                                    localFile = outputFile
                                )
                            }
                            inputFile.delete()
                            outputFile.delete()
                            previewFile?.delete()
                        }
                        isDone = true
                    } catch (e: Exception) {
                        errorMessage = e.localizedMessage ?: "Failed to add page numbers"
                    }
                    isProcessing = false
                }
            },
            onDismiss = { showRenameDialog = false }
        )
    }
}
