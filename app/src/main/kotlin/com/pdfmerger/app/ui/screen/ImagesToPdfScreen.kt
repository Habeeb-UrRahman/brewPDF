package com.pdfmerger.app.ui.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pdfmerger.app.ui.component.ToolBottomBar

import com.pdfmerger.app.ui.component.BrewPickerPrompt
import com.pdfmerger.app.ui.component.BrewScaffold
import com.pdfmerger.app.ui.component.ToolResultSheet
import com.pdfmerger.app.ui.theme.ToolImagesToPdf
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
fun ImagesToPdfScreen(initialUris: List<Uri> = emptyList(), onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var thumbnails by remember { mutableStateOf<List<Bitmap?>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var isDone by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var mergeResult by remember { mutableStateOf<com.pdfmerger.app.viewmodel.MergeResult?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showRenameDialog by remember { mutableStateOf(false) }
    var suggestedOutputName by remember { mutableStateOf("") }
    
    var showPreviewViewer by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<File?>(null) }

    fun handleUriSelection(uris: List<Uri>) {
        if (uris.isNotEmpty()) {
            selectedUris = uris
            isDone = false
            errorMessage = null
            scope.launch {
                val thumbs = withContext(Dispatchers.IO) {
                    uris.map { uri ->
                        try {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                                BitmapFactory.decodeStream(stream, null, opts)
                            }
                        } catch (e: Exception) { null }
                    }
                }
                thumbnails = thumbs
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris != null) {
            handleUriSelection(uris)
        }
    }

    LaunchedEffect(initialUris) {
        if (initialUris.isNotEmpty()) {
            handleUriSelection(initialUris)
        }
    }

    BrewScaffold(
        title = "Images → PDF",
        subtitle = if (selectedUris.isNotEmpty()) "${selectedUris.size} image${if (selectedUris.size > 1) "s" else ""} selected" else "Turn photos into a document",
        onBack = onBack,
        bottomBar = {
            if (selectedUris.isNotEmpty()) {
                ToolBottomBar(
                    leftIcon = Icons.Outlined.Image,
                    onLeftClick = { imagePicker.launch(arrayOf("image/*")) },
                    leftContentDesc = "Change File",
                    showClearButton = true,
                    onClearClick = {
                        selectedUris = emptyList()
                        thumbnails = emptyList()
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
                                    val imageFiles = selectedUris.mapNotNull { uri ->
                                        val name = "img_${System.currentTimeMillis()}_${selectedUris.indexOf(uri)}.jpg"
                                        FileProviderUtil.copyUriToStaging(context, uri, name)
                                    }
                                    val outputFile = File(context.cacheDir, "preview_images_${System.currentTimeMillis()}.pdf")
                                    PdfUtils.imagesToPdf(imageFiles, outputFile)
                                    imageFiles.forEach { it.delete() }
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
                    actionColor = com.pdfmerger.app.ui.theme.ToolImagesToPdf,
                    onActionClick = {
                        suggestedOutputName = FileProviderUtil.generateSmartName("images_to_pdf", emptyList())
                        showRenameDialog = true
                    }
                )
            }
        }
    ) {
        if (selectedUris.isEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
            BrewPickerPrompt(
                icon = Icons.Outlined.Image,
                title = "Select Images",
                subtitle = "Choose photos to convert",
                accentColor = ToolImagesToPdf,
                onClick = { imagePicker.launch(arrayOf("image/*")) }
            )
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Spacer(modifier = Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(thumbnails) { index, bitmap ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(0.75f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Image ${index + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
            }

            
        }
    }

    mergeResult?.let { result ->
        ToolResultSheet(
            title = "PDF Created!",
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
            fileName = "Preview - Images to PDF",
            onSave = {
                showPreviewViewer = false
                suggestedOutputName = FileProviderUtil.generateSmartName("images_to_pdf", emptyList())
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
                            val imageFiles = selectedUris.mapNotNull { uri ->
                                val name = "img_${System.currentTimeMillis()}_${selectedUris.indexOf(uri)}.jpg"
                                FileProviderUtil.copyUriToStaging(context, uri, name)
                            }
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            val outputFile = File(context.cacheDir, "images_to_pdf_${timestamp}.pdf")
                            PdfUtils.imagesToPdf(imageFiles, outputFile)
                            val resultUri = FileProviderUtil.saveToDownloads(context, outputFile, finalName)
                            if (resultUri != null) {
                                mergeResult = com.pdfmerger.app.viewmodel.MergeResult(
                                    fileName = finalName,
                                    fileSize = outputFile.length(),
                                    outputUri = resultUri,
                                    localFile = outputFile
                                )
                            }
                            imageFiles.forEach { it.delete() }
                            outputFile.delete()
                            previewFile?.delete()
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
}
