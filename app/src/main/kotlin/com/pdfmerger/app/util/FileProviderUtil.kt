package com.pdfmerger.app.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Utility for file operations: copying URIs to cache, querying metadata,
 * saving to external cache, and generating FileProvider URIs.
 */
object FileProviderUtil {

    /**
     * Copies a content:// URI into the app's persistent staging directory.
     */
    fun copyUriToStaging(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val sanitized = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val stagingDir = File(context.filesDir, "staging")
            if (!stagingDir.exists()) {
                stagingDir.mkdirs()
            }
            val stagingFile = File(stagingDir, sanitized)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(stagingFile).use { output ->
                    input.copyTo(output)
                }
            }
            stagingFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Reads the display name from a content:// URI.
     */
    fun getFileName(context: Context, uri: Uri): String {
        var name = "unknown.pdf"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    name = cursor.getString(nameIndex) ?: "unknown.pdf"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return name
    }

    /**
     * Reads the file size from a content:// URI.
     */
    fun getFileSize(context: Context, uri: Uri): Long {
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex >= 0) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return size
    }

    /**
     * Generates a content:// URI via FileProvider for sharing a file.
     */
    fun getShareUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Saves a merged PDF to the app's external cache directory.
     */
    fun saveToExternalCache(context: Context, sourceFile: File, fileName: String): Uri? {
        return try {
            val outputDir = context.externalCacheDir ?: context.cacheDir
            val outputFile = File(outputDir, fileName)
            sourceFile.copyTo(outputFile, overwrite = true)
            getShareUri(context, outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves a merged PDF to the device's Downloads directory using MediaStore,
     * or falls back to external cache for older Android versions.
     */
    fun saveToDownloads(context: Context, sourceFile: File, fileName: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        sourceFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    uri
                } else {
                    saveToExternalCache(context, sourceFile, fileName)
                }
            } else {
                saveToExternalCache(context, sourceFile, fileName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            saveToExternalCache(context, sourceFile, fileName)
        }
    }

    /**
     * Formats a byte size into a human-readable string (KB, MB, etc.)
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Saves an image file to the Downloads directory using MediaStore.
     */
    fun saveImageToDownloads(context: Context, sourceFile: File, fileName: String, format: com.pdfmerger.app.ui.screen.ImageFormat): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val mimeType = if (format == com.pdfmerger.app.ui.screen.ImageFormat.PNG) "image/png" else "image/jpeg"
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        sourceFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    uri
                } else null
            } else {
                saveToExternalCache(context, sourceFile, fileName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Generates a smart context-aware filename.
     */
    fun generateSmartName(tool: String, sourceFiles: List<String>, extras: Map<String, String> = emptyMap()): String {
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val firstFile = sourceFiles.firstOrNull()?.let { 
            val name = it.substringBeforeLast(".")
            if (name.length > 20) name.substring(0, 20) else name
        } ?: "document"

        return when (tool) {
            "merge" -> {
                val count = sourceFiles.size
                if (count > 1) {
                    "merged_${firstFile}_+${count - 1}_${timestamp}.pdf"
                } else {
                    "merged_${firstFile}_${timestamp}.pdf"
                }
            }
            "compress" -> "compressed_${firstFile}_${timestamp}.pdf"
            "extract" -> {
                val range = extras["range"] ?: ""
                "extracted_${range}_${firstFile}_${timestamp}.pdf"
            }
            "watermark" -> "watermarked_${firstFile}_${timestamp}.pdf"
            "encrypt", "lock" -> "locked_${firstFile}_${timestamp}.pdf"
            "unlock" -> "unlocked_${firstFile}_${timestamp}.pdf"
            "images_to_pdf" -> "images_${timestamp}.pdf"
            "pdf_maker", "text_to_pdf" -> "document_${timestamp}.pdf"
            "page_numbers" -> "numbered_${firstFile}_${timestamp}.pdf"
            "redact" -> "redacted_${firstFile}_${timestamp}.pdf"
            "split" -> {
                val part = extras["part"]
                if (part != null) "split_${firstFile}_part${part}_${timestamp}.pdf"
                else "split_${firstFile}_${timestamp}.pdf"
            }
            "scan" -> "scanned_${timestamp}.pdf"
            "pipeline" -> {
                val actionsCount = extras["actionsCount"] ?: "multi"
                "pipeline_${firstFile}_${actionsCount}actions_${timestamp}.pdf"
            }
            else -> "${tool}_${firstFile}_${timestamp}.pdf"
        }
    }
}