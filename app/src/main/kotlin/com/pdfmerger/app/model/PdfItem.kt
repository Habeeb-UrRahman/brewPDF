package com.pdfmerger.app.model

import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Represents a single PDF file in the staging list.
 */
data class PdfItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val cachedFile: File? = null,
    val fileName: String,
    val fileSize: Long,
    val pageCount: Int,
    val isLocked: Boolean = false
)
