package com.pdfmerger.app.ui.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pdfmerger.app.ui.component.BrewScaffold
import com.pdfmerger.app.ui.component.RenameDialog
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

@Composable
fun PdfMakerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // State for the pages
    val pages = remember { mutableStateListOf("") }
    
    // Ensure there is at least one page
    LaunchedEffect(Unit) {
        if (pages.isEmpty()) {
            pages.add("")
        }
    }
    
    var isProcessing by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    
    var showRenameDialog by remember { mutableStateOf(false) }
    val defaultFileName = "MadeDocument_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
    
    var showPreviewViewer by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<File?>(null) }


    BrewScaffold(
        title = "PDF Maker",
        subtitle = "Type text to create a PDF",
        onBack = onBack,
        bottomBar = {
            if (pages.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                isProcessing = true
                                coroutineScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            val outputDir = File(context.cacheDir, "made_pdfs").apply { mkdirs() }
                                            val outputFile = File(outputDir, "preview_made_${System.currentTimeMillis()}.pdf")
                                            PdfUtils.textPagesToPdf(pages.toList(), outputFile)
                                            previewFile?.delete()
                                            previewFile = outputFile
                                        }
                                        showPreviewViewer = true
                                    } catch (e: Exception) {
                                        snackbarMessage = "Failed to generate preview"
                                    }
                                    isProcessing = false
                                }
                            },
                            enabled = !isProcessing,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Visibility,
                                contentDescription = "Preview",
                                tint = ToolMerge
                            )
                        }

                        Button(
                            onClick = { showRenameDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(containerColor = ToolMerge)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White
                                )
                            } else {
                                Text("Save PDF", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(pages) { index, text ->
                    PageItem(
                        pageNumber = index + 1,
                        text = text,
                        onTextChange = { pages[index] = it },
                        onDelete = if (pages.size > 1) { { pages.removeAt(index) } } else null
                    )
                }
                
                item {
                    OutlinedButton(
                        onClick = { pages.add("") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ToolMerge)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add Page")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Page")
                    }
                }
            }
            
            snackbarMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp) // Above bottom bar
                ) {
                    Text(msg)
                }
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(3000)
                    snackbarMessage = null
                }
            }
        }
    }

    if (showPreviewViewer && previewFile != null) {
        com.pdfmerger.app.ui.component.PdfViewer(
            file = previewFile!!,
            fileName = "Preview - PDF Maker",
            onSave = {
                showPreviewViewer = false
                showRenameDialog = true
            },
            onDismiss = { showPreviewViewer = false }
        )
    }
    
    if (showRenameDialog) {
        RenameDialog(
            suggestedName = defaultFileName,
            onDismiss = { showRenameDialog = false },
            onConfirm = { customName ->
                showRenameDialog = false
                isProcessing = true
                
                coroutineScope.launch {
                    val resultFile = withContext(Dispatchers.IO) {
                        try {
                            val finalName = if (customName.endsWith(".pdf", true)) customName else "$customName.pdf"
                            val outputDir = File(context.cacheDir, "made_pdfs").apply { mkdirs() }
                            val outputFile = File(outputDir, finalName)
                            
                            PdfUtils.textPagesToPdf(pages.toList(), outputFile)
                            
                            // Move to Downloads
                            FileProviderUtil.saveToDownloads(context, outputFile, finalName)
                            previewFile?.delete()
                            outputFile
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    }
                    
                    isProcessing = false
                    if (resultFile != null) {
                        snackbarMessage = "Saved to Downloads!"
                    } else {
                        snackbarMessage = "Failed to create PDF"
                    }
                }
            }
        )
    }
}

@Composable
private fun PageItem(
    pageNumber: Int,
    text: String,
    onTextChange: (String) -> Unit,
    onDelete: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                    text = "Page $pageNumber",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete Page",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 150.dp),
                placeholder = { Text("Enter text for this page...") },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = ToolMerge
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}
