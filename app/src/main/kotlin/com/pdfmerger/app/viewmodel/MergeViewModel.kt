package com.pdfmerger.app.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfmerger.app.model.PdfItem
import com.pdfmerger.app.util.FileProviderUtil
import com.pdfmerger.app.util.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Result data after a successful merge.
 */
data class MergeResult(
    val fileName: String,
    val fileSize: Long,
    val outputUri: Uri,
    val localFile: File
)

/**
 * Represents a persistent staging session.
 */
data class StagingSession(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val items: List<PdfItem>,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * ViewModel managing the staging list of PDFs and the merge operation.
 * State survives configuration changes and backgrounding (retained ViewModel).
 */
class MergeViewModel : ViewModel() {

    /** Mutable staging list — observed by Compose UI. Represents the active session's items. */
    val pdfItems = mutableStateListOf<PdfItem>()

    /** The list of all saved staging sessions. */
    val sessions = mutableStateListOf<StagingSession>()

    /** The currently active session ID. */
    val activeSessionId = mutableStateOf<String?>(null)

    /** True while a merge is in progress. */
    val isMerging = mutableStateOf(false)

    /** Non-null when a merge has completed successfully. */
    val mergeResult = mutableStateOf<MergeResult?>(null)

    /** One-shot snackbar message. */
    val snackbarMessage = mutableStateOf<String?>(null)

    /** True while PDFs are being loaded / imported. */
    val isLoading = mutableStateOf(false)

    /** Current sort direction: true for Ascending, false for Descending. */
    val sortAscending = mutableStateOf(true)

    /** URIs shared from external apps, waiting for disambiguation. */
    val sharedUris = mutableStateOf<List<Uri>>(emptyList())

    /** URIs passed to a specific tool after disambiguation. */
    val pendingToolUris = mutableStateOf<List<Uri>>(emptyList())

    /** MIME type of the shared content (e.g. "application/pdf" or "image/jpeg"). */
    val sharedMimeType = mutableStateOf("")

    /** Action of the intent (e.g. ACTION_SEND or ACTION_VIEW). */
    val sharedAction = mutableStateOf("")

    companion object {
        private const val MAX_STAGING_SIZE_BYTES = 100L * 1024 * 1024 // 100MB
        private const val STATE_FILE_NAME = "staging_state.json"
        private const val SESSIONS_FILE_NAME = "sessions_state.json"
    }

    /** Tracks if state has been loaded to prevent redundant disk reads. */
    var isStateLoaded = false
        private set

    /**
     * Loads the persistent staging state from a JSON file in the app's filesDir.
     */
    fun loadState(context: Context) {
        if (isStateLoaded) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val loadedSessions = mutableListOf<StagingSession>()
                val sessionsFile = File(context.filesDir, SESSIONS_FILE_NAME)
                
                if (sessionsFile.exists()) {
                    val jsonStr = sessionsFile.readText()
                    val jsonArray = JSONArray(jsonStr)
                    for (i in 0 until jsonArray.length()) {
                        val sessionObj = jsonArray.getJSONObject(i)
                        val id = sessionObj.getString("id")
                        val name = sessionObj.getString("name")
                        val createdAt = sessionObj.optLong("createdAt", System.currentTimeMillis())
                        val itemsArray = sessionObj.getJSONArray("items")
                        
                        val sessionItems = mutableListOf<PdfItem>()
                        for (j in 0 until itemsArray.length()) {
                            val itemObj = itemsArray.getJSONObject(j)
                            val itemId = itemObj.optString("id", UUID.randomUUID().toString())
                            val uriStr = itemObj.getString("uri")
                            val cachedFilePath = itemObj.optString("cachedFile", "")
                            val fileName = itemObj.getString("fileName")
                            val fileSize = itemObj.getLong("fileSize")
                            val pageCount = itemObj.getInt("pageCount")
                            val isLocked = itemObj.optBoolean("isLocked", false)

                            val cachedFile = if (cachedFilePath.isNotEmpty()) File(cachedFilePath) else null
                            if (cachedFile != null && cachedFile.exists()) {
                                sessionItems.add(
                                    PdfItem(
                                        id = itemId,
                                        uri = Uri.parse(uriStr),
                                        cachedFile = cachedFile,
                                        fileName = fileName,
                                        fileSize = fileSize,
                                        pageCount = pageCount,
                                        isLocked = isLocked
                                    )
                                )
                            }
                        }
                        loadedSessions.add(StagingSession(id, name, sessionItems, createdAt))
                    }
                } else {
                    // Backwards compatibility migration
                    val oldStateFile = File(context.filesDir, STATE_FILE_NAME)
                    val oldItems = mutableListOf<PdfItem>()
                    if (oldStateFile.exists()) {
                        val jsonStr = oldStateFile.readText()
                        val jsonArray = JSONArray(jsonStr)
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val id = obj.optString("id", UUID.randomUUID().toString())
                            val uriStr = obj.getString("uri")
                            val cachedFilePath = obj.optString("cachedFile", "")
                            val fileName = obj.getString("fileName")
                            val fileSize = obj.getLong("fileSize")
                            val pageCount = obj.getInt("pageCount")
                            val isLocked = obj.optBoolean("isLocked", false)

                            val cachedFile = if (cachedFilePath.isNotEmpty()) File(cachedFilePath) else null
                            if (cachedFile != null && cachedFile.exists()) {
                                oldItems.add(
                                    PdfItem(
                                        id = id,
                                        uri = Uri.parse(uriStr),
                                        cachedFile = cachedFile,
                                        fileName = fileName,
                                        fileSize = fileSize,
                                        pageCount = pageCount,
                                        isLocked = isLocked
                                    )
                                )
                            }
                        }
                    }
                    val defaultSession = StagingSession(name = "Default Stage", items = oldItems)
                    loadedSessions.add(defaultSession)
                }

                withContext(Dispatchers.Main) {
                    sessions.clear()
                    sessions.addAll(loadedSessions)
                    if (sessions.isNotEmpty()) {
                        switchSession(sessions.first().id)
                    }
                    isStateLoaded = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Syncs current UI state into the active session and saves to disk.
     */
    fun saveState(context: Context) {
        val currentSessionId = activeSessionId.value ?: return
        
        // Update active session in memory
        val sessionIndex = sessions.indexOfFirst { it.id == currentSessionId }
        if (sessionIndex != -1) {
            val updatedSession = sessions[sessionIndex].copy(items = pdfItems.toList())
            sessions[sessionIndex] = updatedSession
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonArray = JSONArray()
                // Use a snapshot of current sessions
                val sessionsSnapshot = sessions.toList()
                for (session in sessionsSnapshot) {
                    val sessionObj = JSONObject().apply {
                        put("id", session.id)
                        put("name", session.name)
                        put("createdAt", session.createdAt)
                        
                        val itemsArray = JSONArray()
                        for (item in session.items) {
                            val itemObj = JSONObject().apply {
                                put("id", item.id)
                                put("uri", item.uri.toString())
                                put("cachedFile", item.cachedFile?.absolutePath ?: "")
                                put("fileName", item.fileName)
                                put("fileSize", item.fileSize)
                                put("pageCount", item.pageCount)
                                put("isLocked", item.isLocked)
                            }
                            itemsArray.put(itemObj)
                        }
                        put("items", itemsArray)
                    }
                    jsonArray.put(sessionObj)
                }
                val stateFile = File(context.filesDir, SESSIONS_FILE_NAME)
                stateFile.writeText(jsonArray.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Switches the active session. */
    fun switchSession(sessionId: String) {
        val session = sessions.find { it.id == sessionId }
        if (session != null) {
            activeSessionId.value = session.id
            pdfItems.clear()
            pdfItems.addAll(session.items)
            applySort()
        }
    }

    /** Creates a new session. */
    fun createNewSession(name: String, context: Context) {
        val newSession = StagingSession(name = name, items = emptyList())
        sessions.add(newSession)
        switchSession(newSession.id)
        saveState(context)
    }

    /** Renames a session. */
    fun renameSession(sessionId: String, newName: String, context: Context) {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index != -1) {
            sessions[index] = sessions[index].copy(name = newName)
            saveState(context)
        }
    }

    /** Deletes a session and its cached files. */
    fun deleteSession(sessionId: String, context: Context) {
        if (sessions.size <= 1) {
            snackbarMessage.value = "Cannot delete the last remaining stage."
            return
        }
        val sessionToDelete = sessions.find { it.id == sessionId } ?: return
        
        // Delete cached files
        sessionToDelete.items.forEach { it.cachedFile?.delete() }
        
        sessions.remove(sessionToDelete)
        
        // If we deleted the active session, switch to the first available one
        if (activeSessionId.value == sessionId) {
            switchSession(sessions.first().id)
        }
        saveState(context)
    }

    /**
     * Adds PDFs from a list of content:// URIs.
     * Enforces a 100MB max combined size.
     */
    fun addPdfs(uris: List<Uri>, context: Context) {
        if (uris.isEmpty()) return
        isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val newItems = mutableListOf<PdfItem>()
            var currentTotalSize = pdfItems.sumOf { it.fileSize }
            var rejectedDueToSize = false

            // Calculate sizes first
            for (uri in uris) {
                val fileSize = FileProviderUtil.getFileSize(context, uri)
                if (currentTotalSize + fileSize > MAX_STAGING_SIZE_BYTES) {
                    rejectedDueToSize = true
                    continue
                }

                val fileName = FileProviderUtil.getFileName(context, uri)
                val alreadyStaged = pdfItems.any { it.fileName == fileName && it.fileSize == fileSize }
                if (alreadyStaged) continue

                // Copy to staging directory (persistent)
                val cacheFileName = "${System.currentTimeMillis()}_$fileName"
                val cachedFile = FileProviderUtil.copyUriToStaging(context, uri, cacheFileName)

                if (cachedFile != null) {
                    val actualSize = cachedFile.length()
                    currentTotalSize += actualSize

                    val isLocked = PdfUtils.isPdfLocked(cachedFile)
                    val pageCount = if (isLocked) 0 else PdfUtils.getPageCount(cachedFile)

                    newItems.add(
                        PdfItem(
                            uri = uri,
                            cachedFile = cachedFile,
                            fileName = fileName,
                            fileSize = actualSize,
                            pageCount = pageCount,
                            isLocked = isLocked
                        )
                    )
                }
            }

            withContext(Dispatchers.Main) {
                pdfItems.addAll(newItems)
                applySort()
                saveState(context)
                isLoading.value = false
                
                if (rejectedDueToSize) {
                    snackbarMessage.value = "Some files were rejected to stay under the 100MB limit."
                }
            }
        }
    }

    fun removePdf(item: PdfItem, context: Context) {
        pdfItems.remove(item)
        item.cachedFile?.delete()
        saveState(context)
    }

    fun movePdf(fromIndex: Int, toIndex: Int, context: Context) {
        if (fromIndex in pdfItems.indices && toIndex in pdfItems.indices) {
            val movedItem = pdfItems.removeAt(fromIndex)
            pdfItems.add(toIndex, movedItem)
            saveState(context)
        }
    }

    fun attemptUnlock(item: PdfItem, password: String, context: Context) {
        val file = item.cachedFile ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Create a temporary file for the unlocked version
                val tempFile = File(file.parent, "unlocked_${file.name}")
                PdfUtils.unlockPdf(file, tempFile, password)
                
                // If successful, replace the old file with the new unlocked one
                if (tempFile.exists()) {
                    file.delete()
                    tempFile.renameTo(file)
                    
                    val newPageCount = PdfUtils.getPageCount(file)
                    val index = pdfItems.indexOf(item)
                    if (index != -1) {
                        val unlockedItem = item.copy(isLocked = false, pageCount = newPageCount)
                        withContext(Dispatchers.Main) {
                            pdfItems[index] = unlockedItem
                            saveState(context)
                            snackbarMessage.value = "PDF Unlocked Successfully"
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    snackbarMessage.value = "Incorrect password or unlock failed."
                }
            }
        }
    }

    fun mergePdfs(context: Context) {
        when {
            pdfItems.isEmpty() -> {
                snackbarMessage.value = "Add at least 2 PDFs to merge."
                return
            }
            pdfItems.any { it.isLocked } -> {
                snackbarMessage.value = "Please unlock all PDFs before merging."
                return
            }
            pdfItems.size == 1 -> {
                snackbarMessage.value = "Add at least one more PDF."
                return
            }
        }

        isMerging.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val outputFileName = "merged_${timestamp}.pdf"

                val tempOutput = File(context.cacheDir, outputFileName)
                val inputFiles = pdfItems.mapNotNull { it.cachedFile }

                if (inputFiles.size < 2) {
                    withContext(Dispatchers.Main) {
                        snackbarMessage.value = "Error reading PDF files. Please try again."
                        isMerging.value = false
                    }
                    return@launch
                }

                PdfUtils.mergePdfs(inputFiles, tempOutput)

                val resultUri = FileProviderUtil.saveToDownloads(context, tempOutput, outputFileName)

                withContext(Dispatchers.Main) {
                    isMerging.value = false
                    if (resultUri != null) {
                        mergeResult.value = MergeResult(
                            fileName = outputFileName,
                            fileSize = tempOutput.length(),
                            outputUri = resultUri,
                            localFile = tempOutput
                        )
                        // Stage is NOT cleared automatically — user decides via "Clear Stage?" dialog
                    } else {
                        snackbarMessage.value = "Failed to save merged PDF."
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isMerging.value = false
                    snackbarMessage.value = "Merge failed: ${e.localizedMessage ?: "Unknown error"}"
                }
            }
        }
    }

    fun saveStagedToDownloads(context: Context) {
        if (pdfItems.isEmpty()) return
        isLoading.value = true
        
        viewModelScope.launch(Dispatchers.IO) {
            var savedCount = 0
            for (item in pdfItems) {
                val file = item.cachedFile
                if (file != null && file.exists()) {
                    val resultUri = FileProviderUtil.saveToDownloads(context, file, item.fileName)
                    if (resultUri != null) {
                        savedCount++
                    }
                }
            }
            withContext(Dispatchers.Main) {
                isLoading.value = false
                if (savedCount > 0) {
                    snackbarMessage.value = "Saved $savedCount files to Downloads."
                    // Removed reset(context) as requested
                } else {
                    snackbarMessage.value = "Failed to save files."
                }
            }
        }
    }

    fun clearMergeResult() {
        mergeResult.value = null
    }

    fun clearSnackbar() {
        snackbarMessage.value = null
    }

    fun reset(context: Context) {
        pdfItems.forEach { it.cachedFile?.delete() }
        pdfItems.clear()
        saveState(context)
    }

    fun toggleSort(context: Context) {
        sortAscending.value = !sortAscending.value
        applySort()
        saveState(context)
    }

    private fun applySort() {
        val comparator = if (sortAscending.value) naturalComparator else naturalComparator.reversed()
        pdfItems.sortWith(comparator)
    }

    /**
     * Natural/alphanumeric comparator that splits filenames into text and
     * numeric chunks so "INV_100" sorts after "INV_99" instead of before it.
     */
    private val naturalComparator = Comparator<PdfItem> { a, b ->
        naturalCompare(a.fileName.lowercase(Locale.getDefault()), b.fileName.lowercase(Locale.getDefault()))
    }

    private fun naturalCompare(a: String, b: String): Int {
        val chunksA = splitIntoChunks(a)
        val chunksB = splitIntoChunks(b)
        for (i in 0 until minOf(chunksA.size, chunksB.size)) {
            val chunkA = chunksA[i]
            val chunkB = chunksB[i]
            val numA = chunkA.toBigIntegerOrNull()
            val numB = chunkB.toBigIntegerOrNull()
            val cmp = if (numA != null && numB != null) {
                numA.compareTo(numB)
            } else {
                chunkA.compareTo(chunkB)
            }
            if (cmp != 0) return cmp
        }
        return chunksA.size - chunksB.size
    }

    private fun splitIntoChunks(s: String): List<String> {
        val chunks = mutableListOf<String>()
        val sb = StringBuilder()
        var wasDigit = false
        for (c in s) {
            val isDigit = c.isDigit()
            if (sb.isNotEmpty() && isDigit != wasDigit) {
                chunks.add(sb.toString())
                sb.clear()
            }
            sb.append(c)
            wasDigit = isDigit
        }
        if (sb.isNotEmpty()) chunks.add(sb.toString())
        return chunks
    }

    fun deleteSourceFiles(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            var deletedCount = 0
            var failedCount = 0
            
            for (item in pdfItems) {
                var success = false
                try {
                    success = DocumentsContract.deleteDocument(context.contentResolver, item.uri)
                } catch (e: Exception) {
                    try {
                        val deletedRows = context.contentResolver.delete(item.uri, null, null)
                        success = deletedRows > 0
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                }
                
                item.cachedFile?.delete()
                
                if (success) {
                    deletedCount++
                } else {
                    failedCount++
                }
            }
            
            withContext(Dispatchers.Main) {
                pdfItems.clear()
                saveState(context)
                if (failedCount == 0 && deletedCount > 0) {
                    snackbarMessage.value = "Successfully deleted $deletedCount original files."
                } else if (deletedCount > 0) {
                    snackbarMessage.value = "Deleted $deletedCount files. $failedCount files could not be deleted due to permissions."
                } else if (failedCount > 0) {
                    snackbarMessage.value = "Could not delete original files due to system permissions."
                }
            }
        }
    }
}