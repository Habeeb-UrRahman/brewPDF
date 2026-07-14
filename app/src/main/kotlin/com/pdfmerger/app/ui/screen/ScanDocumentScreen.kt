package com.pdfmerger.app.ui.screen

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.pdfmerger.app.ui.component.BrewScaffold
import com.pdfmerger.app.ui.component.ToolResultSheet
import com.pdfmerger.app.ui.theme.ToolScanDocument
import com.pdfmerger.app.util.FileProviderUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanDocumentScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? Activity

    var scannedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var suggestedFileName by remember { mutableStateOf("") }
    var mergeResult by remember { mutableStateOf<com.pdfmerger.app.viewmodel.MergeResult?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var scannerLaunched by remember { mutableStateOf(false) }

    // Scanner options
    val scannerOptions = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(100)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF, GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }

    val scanner = remember { GmsDocumentScanning.getClient(scannerOptions) }

    // Result launcher
    var scannedPages by remember { mutableStateOf<List<GmsDocumentScanningResult.Page>?>(null) }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            if (scanResult != null) {
                scannedPdfUri = scanResult.pdf?.uri
                scannedPages = scanResult.pages
                pageCount = scanResult.pages?.size ?: 0
                suggestedFileName = FileProviderUtil.generateSmartName("scan", emptyList())
                showRenameDialog = true
            }
        }
    }

    // Auto-launch scanner when the screen opens
    LaunchedEffect(Unit) {
        if (!scannerLaunched && activity != null) {
            scannerLaunched = true
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    scannerLauncher.launch(
                        IntentSenderRequest.Builder(intentSender).build()
                    )
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        context,
                        "Scanner unavailable: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    // Rename dialog
    if (showRenameDialog) {
        ScanRenameDialog(
            suggestedName = suggestedFileName,
            onConfirm = { finalName, isPdf ->
                showRenameDialog = false
                isProcessing = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            if (isPdf && scannedPdfUri != null) {
                                val tempFile = File(context.cacheDir, "scan_temp_${System.currentTimeMillis()}.pdf")
                                context.contentResolver.openInputStream(scannedPdfUri!!)?.use { input ->
                                    tempFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                val resultUri = FileProviderUtil.saveToDownloads(context, tempFile, finalName)
                                if (resultUri != null) {
                                    mergeResult = com.pdfmerger.app.viewmodel.MergeResult(
                                        fileName = finalName,
                                        fileSize = tempFile.length(),
                                        outputUri = resultUri,
                                        localFile = tempFile
                                    )
                                }
                                tempFile.delete()
                            } else if (!isPdf && scannedPages != null) {
                                val savedFilesCount = scannedPages!!.size
                                scannedPages!!.forEachIndexed { index, page ->
                                    val name = "${finalName.removeSuffix(".pdf")}_${index + 1}.jpg"
                                    val tempFile = File(context.cacheDir, name)
                                    context.contentResolver.openInputStream(page.imageUri)?.use { input ->
                                        tempFile.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    FileProviderUtil.saveToDownloads(context, tempFile, name)
                                    tempFile.delete()
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Saved $savedFilesCount images to Downloads", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Save failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    isProcessing = false
                }
            },
            onDismiss = {
                showRenameDialog = false
            }
        )
    }

    BrewScaffold(
        title = "Scan Document",
        subtitle = "Camera to PDF, just like that",
        onBack = onBack
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Animated icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(ToolScanDocument.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        color = ToolScanDocument,
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        Icons.Outlined.DocumentScanner,
                        contentDescription = null,
                        tint = ToolScanDocument,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Text(
                text = if (isProcessing) "Saving your scan..." else "Tap below to scan pages",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Use your camera to scan documents.\nPages are auto-cropped and enhanced.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (activity != null) {
                        scanner.getStartScanIntent(activity)
                            .addOnSuccessListener { intentSender ->
                                scannerLauncher.launch(
                                    IntentSenderRequest.Builder(intentSender).build()
                                )
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(
                                    context,
                                    "Scanner unavailable: ${e.localizedMessage}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ToolScanDocument),
                enabled = !isProcessing
            ) {
                Icon(
                    Icons.Outlined.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Open Scanner",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }

    // Result sheet
    mergeResult?.let { result ->
        ToolResultSheet(
            title = "Scan Saved!",
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
fun ScanRenameDialog(
    suggestedName: String,
    onConfirm: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(suggestedName.removeSuffix(".pdf")) }
    var isPdf by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Scan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("File Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = isPdf, onClick = { isPdf = true })
                    Text("PDF Document", modifier = Modifier.padding(end = 16.dp))
                    
                    RadioButton(selected = !isPdf, onClick = { isPdf = false })
                    Text("Images (JPEG)")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    val suffix = if (isPdf) ".pdf" else ""
                    val finalName = if (name.endsWith(suffix, ignoreCase = true)) name else "$name$suffix"
                    onConfirm(finalName, isPdf) 
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
