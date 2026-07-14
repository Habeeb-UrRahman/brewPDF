package com.pdfmerger.app.model

sealed class PipelineAction(val displayName: String, val order: Int) {
    // Pre-file actions (order 1xx)
    data class Watermark(
        val text: String = "",
        val color: Int,
        val fontSize: Float = 80f,
        val isImageMode: Boolean = false,
        val stampPath: String? = null,
        val opacity: Float = 0.3f,
        val stampScale: Float = 0.5f,
        val customRules: List<Pair<String, String>> = emptyList(),
        val applyToAll: Boolean = true,
        val removeBackground: Boolean = false
    ) : PipelineAction("Watermark", 100)
    
    data class ExtractPages(val ranges: Map<String, IntRange>) : PipelineAction("Extract Pages", 110)
    
    // Core action (order 200)
    object Merge : PipelineAction("Merge", 200)
    
    // Post-merge actions (order 3xx)
    data class PageNumbers(val format: String, val position: String) : PipelineAction("Page Numbers", 300)
    
    data class Compress(val quality: Float) : PipelineAction("Compress", 310)
    
    data class Lock(val password: String) : PipelineAction("Lock", 320)
    
    // Terminal action (order 400)
    data class PdfToImages(val format: String, val dpi: Int) : PipelineAction("PDF to Images", 400)
}
