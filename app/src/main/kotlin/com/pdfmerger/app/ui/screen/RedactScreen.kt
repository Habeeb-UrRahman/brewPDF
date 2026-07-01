package com.pdfmerger.app.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatStrikethrough
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
import com.pdfmerger.app.ui.theme.ToolRedact
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
fun RedactScreen(initialUri: Uri? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var fileSize by remember { mutableStateOf(0L) }
    var textToRedact by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var isDone by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var mergeResult by remember { mutableStateOf<com.pdfmerger.app.viewmodel.MergeResult?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun handleUriSelection(uri: Uri) {
        selectedUri = uri
        fileName = FileProviderUtil.getFileName(context, uri)
        fileSize = FileProviderUtil.getFileSize(context, uri)
        isDone = false
        errorMessage = null
        textToRedact = ""
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            handleUriSelection(uri)
        }
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null) {
            handleUriSelection(initialUri)
        }
    }

    BrewScaffold(
        title = "Redact PDF",
        subtitle = "Permanently black out sensitive text",
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
                        textToRedact = ""
                        isDone = false
                        errorMessage = null
                    },
                    actionText = if (isDone) "Done ✓" else "Redact Words",
                    isActionEnabled = textToRedact.isNotEmpty() && !isProcessing && !isDone,
                    isProcessing = isProcessing,
                    actionColor = com.pdfmerger.app.ui.theme.ToolRedact,
                    onActionClick = {
                        isProcessing = true
                        errorMessage = null
                        scope.launch {
                            try {
                            withContext(Dispatchers.IO) {
                                val inputFile = FileProviderUtil.copyUriToStaging(context, selectedUri!!, "redact_input_${System.currentTimeMillis()}.pdf")
                                    ?: throw Exception("Failed to read file")
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                val outputFile = File(context.cacheDir, "redacted_${timestamp}.pdf")
                                PdfUtils.redactText(inputFile, outputFile, textToRedact)
                                val resultUri = FileProviderUtil.saveToDownloads(context, outputFile, "redacted_$fileName")
                                if (resultUri != null) {
                                    mergeResult = com.pdfmerger.app.viewmodel.MergeResult(
                                        fileName = "redacted_$fileName",
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
                            errorMessage = e.localizedMessage ?: "Failed to redact text"
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
                icon = Icons.Outlined.FormatStrikethrough,
                title = "Select a PDF",
                subtitle = "Choose a file to redact",
                accentColor = ToolRedact,
                onClick = { filePicker.launch(arrayOf("application/pdf")) }
            )
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Spacer(modifier = Modifier.height(24.dp))
            BrewFileCard(fileName = fileName, fileSize = fileSize)
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = textToRedact,
                onValueChange = { textToRedact = it },
                label = { Text("Text to Redact (e.g., SSN, Name)") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ToolRedact,
                    focusedLabelColor = ToolRedact
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
            }

            
        }
    }

    mergeResult?.let { result ->
        ToolResultSheet(
            title = "Text Redacted!",
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
