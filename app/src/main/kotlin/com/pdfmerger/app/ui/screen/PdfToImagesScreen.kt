package com.pdfmerger.app.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pdfmerger.app.ui.component.ToolBottomBar
import androidx.compose.material.icons.outlined.FolderOpen
import com.pdfmerger.app.ui.component.BrewFileCard
import com.pdfmerger.app.ui.component.BrewPickerPrompt
import com.pdfmerger.app.ui.component.BrewScaffold
import com.pdfmerger.app.ui.theme.ToolPdfToImages
import com.pdfmerger.app.util.FileProviderUtil
import com.pdfmerger.app.util.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ImageFormat(val label: String, val ext: String) {
    JPEG("JPEG", "jpg"),
    PNG("PNG", "png")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToImagesScreen(initialUri: Uri? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var fileSize by remember { mutableStateOf(0L) }
    var selectedFormat by remember { mutableStateOf(ImageFormat.JPEG) }
    var isProcessing by remember { mutableStateOf(false) }
    var isDone by remember { mutableStateOf(false) }
    var exportedCount by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun handleUriSelection(uri: Uri) {
        selectedUri = uri
        fileName = FileProviderUtil.getFileName(context, uri)
        fileSize = FileProviderUtil.getFileSize(context, uri)
        isDone = false
        errorMessage = null
        exportedCount = 0
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
        title = "PDF → Images",
        subtitle = "Convert each page into an image file",
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
                    },
                    actionText = if (isDone) "Done ✓" else "Extract Images",
                    isActionEnabled = !isProcessing && !isDone,
                    isProcessing = isProcessing,
                    actionColor = com.pdfmerger.app.ui.theme.ToolPdfToImages,
                    onActionClick = {
                        isProcessing = true
                        errorMessage = null
                        scope.launch {
                            try {
                            val count = withContext(Dispatchers.IO) {
                                val inputFile = FileProviderUtil.copyUriToStaging(context, selectedUri!!, "export_input_${System.currentTimeMillis()}.pdf")
                                    ?: throw Exception("Failed to read file")
                                val outputDir = File(context.cacheDir, "pdf_export_${System.currentTimeMillis()}")
                                outputDir.mkdirs()
                                PdfUtils.pdfToImages(inputFile, outputDir, selectedFormat)

                                var saved = 0
                                val baseName = fileName.removeSuffix(".pdf").removeSuffix(".PDF")
                                outputDir.listFiles()?.sortedBy { it.name }?.forEachIndexed { index, file ->
                                    val imgName = "${baseName}_page${index + 1}.${selectedFormat.ext}"
                                    FileProviderUtil.saveImageToDownloads(context, file, imgName, selectedFormat)
                                    saved++
                                }

                                inputFile.delete()
                                outputDir.deleteRecursively()
                                saved
                            }
                            exportedCount = count
                            isDone = true
                        } catch (e: Exception) {
                            errorMessage = e.localizedMessage ?: "Export failed"
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
                icon = Icons.Outlined.PhotoLibrary,
                title = "Select a PDF",
                subtitle = "Choose a file to export",
                accentColor = ToolPdfToImages,
                onClick = { filePicker.launch(arrayOf("application/pdf")) }
            )
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Spacer(modifier = Modifier.height(24.dp))
            BrewFileCard(fileName = fileName, fileSize = fileSize)
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Output Format",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ImageFormat.entries.forEach { format ->
                    val isSelected = selectedFormat == format
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFormat = format },
                        label = { Text(format.label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ToolPdfToImages.copy(alpha = 0.2f),
                            selectedLabelColor = ToolPdfToImages
                        )
                    )
                }
            }

            if (isDone) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Exported $exportedCount images to Downloads ✓",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ToolPdfToImages
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
            }

            
        }
    }
}
