package com.pdfmerger.app.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pdfmerger.app.ui.component.ToolBottomBar
import com.pdfmerger.app.ui.component.BrewFileCard
import com.pdfmerger.app.ui.component.BrewPickerPrompt
import com.pdfmerger.app.ui.component.BrewScaffold
import com.pdfmerger.app.ui.theme.ToolExtract
import com.pdfmerger.app.util.FileProviderUtil
import com.pdfmerger.app.util.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.content.Intent
import androidx.core.content.FileProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitScreen(initialUri: Uri? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var cachedFile by remember { mutableStateOf<File?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var splitAtPage by remember { mutableStateOf(1) }
    
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resultFiles by remember { mutableStateOf<Pair<File, File>?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun getPageCount(file: File): Int {
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            renderer.close()
            pfd.close()
            count
        } catch (e: Exception) {
            0
        }
    }

    fun handleUriSelection(uri: Uri) {
        selectedUri = uri
        fileName = FileProviderUtil.getFileName(context, uri)
        errorMessage = null
        resultFiles = null
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                FileProviderUtil.copyUriToStaging(context, uri, "split_input_${System.currentTimeMillis()}.pdf")
            }
            cachedFile = file
            if (file != null) {
                val count = withContext(Dispatchers.IO) { getPageCount(file) }
                pageCount = count
                splitAtPage = (count / 2).coerceAtLeast(1)
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            handleUriSelection(uri)
        }
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null && selectedUri == null) {
            handleUriSelection(initialUri)
        }
    }

    val baseName = fileName.substringBeforeLast(".")

    BrewScaffold(
        title = "Split PDF",
        subtitle = "Break a document into two parts",
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
                        resultFiles = null
                        pageCount = 0
                        cachedFile = null
                    },
                    actionText = "Split PDF",
                    isActionEnabled = pageCount > 1,
                    isProcessing = isProcessing,
                    actionColor = ToolExtract,
                    onActionClick = {
                        isProcessing = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val stagingDir = File(context.filesDir, "staging")
                                if (!stagingDir.exists()) stagingDir.mkdirs()
                                
                                val out1 = File(stagingDir, "${baseName}_part1.pdf")
                                val out2 = File(stagingDir, "${baseName}_part2.pdf")
                                
                                withContext(Dispatchers.IO) {
                                    PdfUtils.splitPdf(cachedFile!!, splitAtPage, out1, out2)
                                }
                                
                                resultFiles = Pair(out1, out2)
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Failed to split PDF"
                            } finally {
                                isProcessing = false
                            }
                        }
                    }
                )
            }
        }
    ) {
        if (selectedUri == null) {
            Spacer(modifier = Modifier.weight(1f))
            BrewPickerPrompt(
                icon = Icons.AutoMirrored.Outlined.CallSplit,
                title = "Select PDF to Split",
                subtitle = "Choose a file to split in half",
                accentColor = ToolExtract,
                onClick = { filePicker.launch(arrayOf("application/pdf")) }
            )
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Spacer(modifier = Modifier.height(16.dp))
            BrewFileCard(
                fileName = fileName,
                fileSize = 0L
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            if (pageCount > 1) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Split at Page: $splitAtPage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Part 1: Pages 1 to $splitAtPage",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Part 2: Pages ${splitAtPage + 1} to $pageCount",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Slider(
                            value = splitAtPage.toFloat(),
                            onValueChange = { splitAtPage = it.roundToInt() },
                            valueRange = 1f..(pageCount - 1).toFloat(),
                            steps = (pageCount - 3).coerceAtLeast(0)
                        )
                    }
                }
            } else if (pageCount == 1) {
                Text(
                    text = "This PDF only has 1 page and cannot be split.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }

    if (resultFiles != null) {
        ModalBottomSheet(
            onDismissRequest = { resultFiles = null },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.CallSplit,
                    contentDescription = null,
                    tint = ToolExtract,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "PDF Split Successfully!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        val uri = FileProviderUtil.getShareUri(context, resultFiles!!.first)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Part 1"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Share Part 1 (Pages 1-$splitAtPage)")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val uri = FileProviderUtil.getShareUri(context, resultFiles!!.second)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Part 2"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Share Part 2 (Pages ${splitAtPage + 1}-$pageCount)")
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
