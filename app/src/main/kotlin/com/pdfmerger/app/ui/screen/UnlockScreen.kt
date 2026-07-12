package com.pdfmerger.app.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pdfmerger.app.ui.component.ToolBottomBar
import androidx.compose.material.icons.outlined.FolderOpen
import com.pdfmerger.app.ui.component.BrewFileCard
import com.pdfmerger.app.ui.component.BrewPickerPrompt
import com.pdfmerger.app.ui.component.BrewScaffold
import com.pdfmerger.app.ui.component.ToolResultSheet
import com.pdfmerger.app.ui.theme.ToolUnlock
import com.pdfmerger.app.util.FileProviderUtil
import com.pdfmerger.app.util.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.pdfmerger.app.ui.component.RenameDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockScreen(initialUri: Uri? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var fileSize by remember { mutableStateOf(0L) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
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
            password = ""
        
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
            password = ""
        }
    }

    BrewScaffold(
        title = "Unlock PDF",
        subtitle = "Remove password protection from your document",
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
                        password = ""
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
                                    val inputFile = FileProviderUtil.copyUriToStaging(context, selectedUri!!, "preview_unlock_${System.currentTimeMillis()}.pdf")
                                        ?: throw Exception("Failed to read file")
                                    val outputFile = File(context.cacheDir, "preview_unlocked_${System.currentTimeMillis()}.pdf")
                                    PdfUtils.unlockPdf(inputFile, outputFile, password)
                                    previewFile?.delete()
                                    previewFile = outputFile
                                    inputFile.delete()
                                }
                                showPreviewViewer = true
                            } catch (e: Exception) {
                                errorMessage = e.localizedMessage ?: "Failed to generate preview. Wrong password?"
                            }
                            isProcessing = false
                        }
                    },
                    actionText = if (isDone) "Done ✓" else "Save Unlock",
                    isActionEnabled = password.isNotEmpty() && !isProcessing && !isDone,
                    isProcessing = isProcessing,
                    actionColor = ToolUnlock,
                    onActionClick = {
                        suggestedOutputName = "unlocked_$fileName"
                        showRenameDialog = true
                    }
                )
            }
        }
    ) {
        if (selectedUri == null) {
            Spacer(modifier = Modifier.weight(1f))
            BrewPickerPrompt(
                icon = Icons.Outlined.LockOpen,
                title = "Select a Locked PDF",
                subtitle = "Choose a file to unlock",
                accentColor = ToolUnlock,
                onClick = { filePicker.launch(arrayOf("application/pdf")) }
            )
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Spacer(modifier = Modifier.height(24.dp))
            BrewFileCard(fileName = fileName, fileSize = fileSize)
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Original Password") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = null
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ToolUnlock,
                    focusedLabelColor = ToolUnlock
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
            title = "PDF Unlocked Successfully!",
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

    if (showPreviewViewer && previewFile != null) {
        com.pdfmerger.app.ui.component.PdfViewer(
            file = previewFile!!,
            fileName = "Preview - ${fileName}",
            onSave = {
                showPreviewViewer = false
                suggestedOutputName = "unlocked_$fileName"
                showRenameDialog = true
            },
            onDismiss = { showPreviewViewer = false }
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
                            val inputFile = FileProviderUtil.copyUriToStaging(context, selectedUri!!, "unlock_input_${System.currentTimeMillis()}.pdf")
                                ?: throw Exception("Failed to read file")
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            val outputFile = File(context.cacheDir, "unlocked_${timestamp}.pdf")
                            PdfUtils.unlockPdf(inputFile, outputFile, password)
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
                        errorMessage = e.localizedMessage ?: "Failed to unlock. Wrong password?"
                    }
                    isProcessing = false
                }
            },
            onDismiss = { showRenameDialog = false }
        )
    }
}
