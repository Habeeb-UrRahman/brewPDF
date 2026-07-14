package com.pdfmerger.app.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Create
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
import com.pdfmerger.app.ui.theme.ToolWatermark
import com.pdfmerger.app.util.FileProviderUtil
import com.pdfmerger.app.util.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.itextpdf.kernel.colors.ColorConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.pdfmerger.app.ui.component.RenameDialog
import com.pdfmerger.app.ui.component.SignatureDialog
import androidx.compose.foundation.border
import coil.compose.AsyncImage

data class WatermarkColor(val name: String, val composeColor: Color, val iTextColor: com.itextpdf.kernel.colors.Color)

val availableColors = listOf(
    WatermarkColor("Red", Color(0xFFE53935), ColorConstants.RED),
    WatermarkColor("Black", Color(0xFF212121), ColorConstants.BLACK),
    WatermarkColor("Blue", Color(0xFF1E88E5), ColorConstants.BLUE),
    WatermarkColor("Gray", Color(0xFF757575), ColorConstants.GRAY)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkScreen(initialUri: Uri? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var fileSize by remember { mutableStateOf(0L) }
    
    var isImageMode by remember { mutableStateOf(false) }
    var applyToAll by remember { mutableStateOf(true) }
    var watermarkText by remember { mutableStateOf("") }
    var customRules by remember { mutableStateOf(listOf(Pair("", ""))) }
    
    var removeBackground by remember { mutableStateOf(false) }
    var watermarkOpacity by remember { mutableFloatStateOf(0.3f) }
    var watermarkFontSize by remember { mutableFloatStateOf(80f) }
    var selectedColor by remember { mutableStateOf(availableColors[0]) }
    
    val stampsDir = remember { File(context.filesDir, "stamps").apply { mkdirs() } }
    var savedStamps by remember { mutableStateOf(stampsDir.listFiles()?.toList() ?: emptyList()) }
    var selectedStamp by remember { mutableStateOf<File?>(null) }
    var pendingStampToSave by remember { mutableStateOf<File?>(null) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var stampScale by remember { mutableFloatStateOf(0.5f) }
    
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
            watermarkText = ""
            customRules = listOf(Pair("", ""))
            applyToAll = true
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
            watermarkText = ""
            customRules = listOf(Pair("", ""))
            applyToAll = true
        }
    }
    
    val stampPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch {
                withContext(Dispatchers.IO) {
                    val file = FileProviderUtil.copyUriToStaging(context, it, "temp_stamp_${System.currentTimeMillis()}.png")
                    if (file != null) {
                        pendingStampToSave = file
                    }
                }
            }
        }
    }

    fun parseWatermarkRules(rules: List<Pair<String, String>>): Map<Int, String> {
        val map = mutableMapOf<Int, String>()
        for (rule in rules) {
            val text = rule.second
            if (text.isBlank()) continue
            val parts = rule.first.split(",")
            for (part in parts) {
                val p = part.trim()
                if (p.contains("-")) {
                    val range = p.split("-")
                    if (range.size == 2) {
                        val start = range[0].toIntOrNull() ?: continue
                        val end = range[1].toIntOrNull() ?: continue
                        for (i in start..end) {
                            map[i] = text
                        }
                    }
                } else {
                    val pInt = p.toIntOrNull()
                    if (pInt != null) {
                        map[pInt] = text
                    }
                }
            }
        }
        return map
    }

    if (showSignatureDialog) {
        SignatureDialog(
            onDismiss = { showSignatureDialog = false },
            onSave = { file ->
                showSignatureDialog = false
                pendingStampToSave = file
            },
            cacheDir = context.cacheDir
        )
    }

    if (pendingStampToSave != null) {
        AlertDialog(
            onDismissRequest = { pendingStampToSave = null },
            title = { Text("Save Stamp") },
            text = { Text("Do you want to save this stamp/signature to your library for future use?") },
            confirmButton = {
                TextButton(onClick = {
                    val dest = File(stampsDir, "stamp_${System.currentTimeMillis()}.png")
                    pendingStampToSave?.copyTo(dest)
                    savedStamps = stampsDir.listFiles()?.toList() ?: emptyList()
                    selectedStamp = dest
                    pendingStampToSave = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    selectedStamp = pendingStampToSave
                    pendingStampToSave = null 
                }) { Text("Just Use Once") }
            }
        )
    }

    BrewScaffold(
        title = "Watermark PDF",
        subtitle = "Add a text overlay or stamp",
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
                        watermarkText = ""
                        customRules = listOf(Pair("", ""))
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
                                    val inputFile = FileProviderUtil.copyUriToStaging(context, selectedUri!!, "preview_input_${System.currentTimeMillis()}.pdf")
                                        ?: throw Exception("Failed to read file")
                                    val outputFile = File(context.cacheDir, "preview_watermarked_${System.currentTimeMillis()}.pdf")
                                    
                                    if (isImageMode && selectedStamp != null) {
                                        PdfUtils.addImageWatermark(
                                            inputFile = inputFile,
                                            outputFile = outputFile,
                                            imageFile = selectedStamp!!,
                                            opacity = watermarkOpacity,
                                            scale = stampScale,
                                            removeBackground = removeBackground
                                        )
                                    } else {
                                        val customMap = if (applyToAll) emptyMap() else parseWatermarkRules(customRules)
                                        val defaultTxt = if (applyToAll) watermarkText else null
                                        
                                        PdfUtils.addTextWatermark(
                                            inputFile = inputFile,
                                            outputFile = outputFile,
                                            defaultText = defaultTxt,
                                            customTextMap = customMap,
                                            color = selectedColor.iTextColor,
                                            opacity = watermarkOpacity,
                                            fontSize = watermarkFontSize
                                        )
                                    }
                                    
                                    previewFile?.delete()
                                    previewFile = outputFile
                                    inputFile.delete()
                                }
                                showPreviewViewer = true
                            } catch (e: Exception) {
                                errorMessage = e.localizedMessage ?: "Failed to generate preview"
                            }
                            isProcessing = false
                        }
                    },
                    actionText = if (isDone) "Done ✓" else "Save Watermark",
                    isActionEnabled = if (isImageMode) selectedStamp != null else ((applyToAll && watermarkText.isNotEmpty()) || (!applyToAll && customRules.any { it.first.isNotEmpty() && it.second.isNotEmpty() })),
                    isProcessing = isProcessing,
                    actionColor = ToolWatermark,
                    onActionClick = {
                        suggestedOutputName = FileProviderUtil.generateSmartName("watermark", listOf(fileName))
                        showRenameDialog = true
                    }
                )
            }
        }
    ) {
        if (selectedUri == null) {
            Spacer(modifier = Modifier.weight(1f))
            BrewPickerPrompt(
                icon = Icons.Outlined.FontDownload,
                title = "Select a PDF",
                subtitle = "Choose a file to watermark",
                accentColor = ToolWatermark,
                onClick = { filePicker.launch(arrayOf("application/pdf")) }
            )
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Spacer(modifier = Modifier.height(24.dp))
            BrewFileCard(fileName = fileName, fileSize = fileSize)
            Spacer(modifier = Modifier.height(16.dp))
            
            TabRow(selectedTabIndex = if (isImageMode) 1 else 0, containerColor = Color.Transparent, contentColor = ToolWatermark) {
                Tab(selected = !isImageMode, onClick = { isImageMode = false }, text = { Text("Text") })
                Tab(selected = isImageMode, onClick = { isImageMode = true }, text = { Text("Stamp / Signature") })
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isImageMode) {
                Text("Stamp Library", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    items(savedStamps) { stampFile ->
                        val isSelected = selectedStamp == stampFile
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) ToolWatermark else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .background(Color.White)
                                .clickable { selectedStamp = stampFile },
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = stampFile,
                                contentDescription = "Saved Stamp",
                                modifier = Modifier.fillMaxSize().padding(4.dp)
                            )
                            if (isSelected) {
                                IconButton(
                                    onClick = { 
                                        stampFile.delete()
                                        savedStamps = stampsDir.listFiles()?.toList() ?: emptyList()
                                        selectedStamp = null
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(Color(0x88000000), CircleShape)
                                ) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { stampPicker.launch(arrayOf("image/*")) }
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Image, contentDescription = "Pick Image", tint = ToolWatermark)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Image", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showSignatureDialog = true }
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Create, contentDescription = "Draw Signature", tint = ToolWatermark)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Draw", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Text("Scale: ${(stampScale * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = stampScale,
                    onValueChange = { stampScale = it },
                    valueRange = 0.1f..3.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = ToolWatermark,
                        activeTrackColor = ToolWatermark
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { removeBackground = !removeBackground }) {
                    Checkbox(checked = removeBackground, onCheckedChange = { removeBackground = it }, colors = CheckboxDefaults.colors(checkedColor = ToolWatermark))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Make white background transparent", style = MaterialTheme.typography.bodyLarge)
                }
                
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { applyToAll = !applyToAll }) {
                    Checkbox(checked = applyToAll, onCheckedChange = { applyToAll = it }, colors = CheckboxDefaults.colors(checkedColor = ToolWatermark))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Apply same watermark to all pages", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (applyToAll) {
                    OutlinedTextField(
                        value = watermarkText,
                        onValueChange = { watermarkText = it },
                        label = { Text("Watermark Text (e.g., CONFIDENTIAL)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ToolWatermark,
                            focusedLabelColor = ToolWatermark
                        )
                    )
                } else {
                    Text("Custom Pages", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    customRules.forEachIndexed { index, rule ->
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = rule.first,
                                onValueChange = { newVal -> customRules = customRules.toMutableList().apply { set(index, newVal to rule.second) } },
                                label = { Text("Pages (e.g. 1, 3-5)") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = rule.second,
                                onValueChange = { newVal -> customRules = customRules.toMutableList().apply { set(index, rule.first to newVal) } },
                                label = { Text("Text") },
                                modifier = Modifier.weight(1.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            IconButton(onClick = { customRules = customRules.toMutableList().apply { removeAt(index) } }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete Rule", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    TextButton(onClick = { customRules = customRules + Pair("", "") }) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Rule", color = ToolWatermark)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                Text("Color", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    availableColors.forEach { colorOption ->
                        val isSelected = selectedColor == colorOption
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colorOption.composeColor)
                                .clickable { selectedColor = colorOption },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Font Size: ${watermarkFontSize.toInt()}", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = watermarkFontSize,
                    onValueChange = { watermarkFontSize = it },
                    valueRange = 12f..150f,
                    colors = SliderDefaults.colors(
                        thumbColor = ToolWatermark,
                        activeTrackColor = ToolWatermark
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Opacity: ${(watermarkOpacity * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = watermarkOpacity,
                onValueChange = { watermarkOpacity = it },
                valueRange = 0.1f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = ToolWatermark,
                    activeTrackColor = ToolWatermark
                )
            )

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
            fileName = "Preview - ${fileName}",
            onSave = {
                showPreviewViewer = false
                suggestedOutputName = FileProviderUtil.generateSmartName("watermark", listOf(fileName))
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
                            val inputFile = FileProviderUtil.copyUriToStaging(context, selectedUri!!, "watermark_input_${System.currentTimeMillis()}.pdf")
                                ?: throw Exception("Failed to read file")
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            val outputFile = File(context.cacheDir, "watermarked_${timestamp}.pdf")
                            
                            if (isImageMode && selectedStamp != null) {
                                PdfUtils.addImageWatermark(
                                    inputFile = inputFile,
                                    outputFile = outputFile,
                                    imageFile = selectedStamp!!,
                                    opacity = watermarkOpacity,
                                    scale = stampScale,
                                    removeBackground = removeBackground
                                )
                            } else {
                                val customMap = if (applyToAll) emptyMap() else parseWatermarkRules(customRules)
                                val defaultTxt = if (applyToAll) watermarkText else null
                                
                                PdfUtils.addTextWatermark(
                                    inputFile = inputFile,
                                    outputFile = outputFile,
                                    defaultText = defaultTxt,
                                    customTextMap = customMap,
                                    color = selectedColor.iTextColor,
                                    opacity = watermarkOpacity,
                                    fontSize = watermarkFontSize
                                )
                            }
                            
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
                        errorMessage = e.localizedMessage ?: "Failed to apply watermark"
                    }
                    isProcessing = false
                }
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    mergeResult?.let { result ->
        ToolResultSheet(
            title = "Watermark Added!",
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
