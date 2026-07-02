package com.pdfmerger.app.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pdfmerger.app.ui.component.BrewScaffold
import com.pdfmerger.app.ui.component.RenameDialog
import com.pdfmerger.app.ui.component.ToolBottomBar
import com.pdfmerger.app.ui.component.ToolResultSheet
import com.pdfmerger.app.ui.theme.ToolMerge
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
fun TextToPdfScreen(onBack: () -> Unit, initialUri: Uri? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(initialUri) }
    var fileName by remember { mutableStateOf("") }
    
    var isProcessing by remember { mutableStateOf(false) }
    var isDone by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var mergeResult by remember { mutableStateOf<com.pdfmerger.app.viewmodel.MergeResult?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    var showRenameDialog by remember { mutableStateOf(false) }
    var suggestedOutputName by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            fileName = FileProviderUtil.getFileName(context, uri)
            isDone = false
            errorMessage = null
        }
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null) {
            fileName = FileProviderUtil.getFileName(context, initialUri)
        }
    }

    BrewScaffold(
        title = "Text to PDF",
        subtitle = "Convert plain text to a PDF",
        onBack = onBack,
        bottomBar = {
            if (selectedUri != null) {
                ToolBottomBar(
                    showClearButton = true,
                    onClearClick = { 
                        selectedUri = null 
                        isDone = false
                        errorMessage = null
                    },
                    leftIcon = Icons.Outlined.FolderOpen,
                    leftContentDesc = "Change File",
                    onLeftClick = { filePickerLauncher.launch("text/plain") },
                    actionText = "Convert to PDF",
                    isActionEnabled = selectedUri != null,
                    isProcessing = isProcessing,
                    actionColor = ToolMerge,
                    onActionClick = {
                        val baseName = if (fileName.contains(".")) fileName.substringBeforeLast(".") else fileName
                        suggestedOutputName = "${baseName}.pdf"
                        showRenameDialog = true
                    }
                )
            }
        }
    ) {
        Spacer(modifier = Modifier.weight(1f))

        if (selectedUri == null) {
            // Empty State
            Column(
                modifier = Modifier.fillMaxWidth(),
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
                    "No Text File Selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Button(
                    onClick = { filePickerLauncher.launch("text/plain") },
                    colors = ButtonDefaults.buttonColors(containerColor = ToolMerge)
                ) {
                    Text("Select .txt file")
                }
            }
        } else {
            // Selected State
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Outlined.FileCopy,
                    contentDescription = null,
                    tint = ToolMerge,
                    modifier = Modifier.size(64.dp)
                )
                
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
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
                            val inputFile = FileProviderUtil.copyUriToStaging(context, selectedUri!!, "text_input_${System.currentTimeMillis()}.txt")
                                ?: throw Exception("Failed to read file")
                            
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            val outputFile = File(context.cacheDir, "text_to_pdf_${timestamp}.pdf")
                            
                            PdfUtils.textToPdf(inputFile, outputFile)
                            
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
                        }
                        isDone = true
                    } catch (e: Exception) {
                        errorMessage = e.localizedMessage ?: "Conversion failed"
                    }
                    isProcessing = false
                }
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    // Result sheet
    mergeResult?.let { result ->
        ToolResultSheet(
            title = "PDF Generated!",
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
