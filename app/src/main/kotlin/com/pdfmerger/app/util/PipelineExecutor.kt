package com.pdfmerger.app.util

import android.content.Context
import com.pdfmerger.app.model.PipelineAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.pdfmerger.app.ui.screen.ImageFormat
import com.itextpdf.kernel.colors.ColorConstants

data class PipelineProgress(
    val currentStep: String,
    val overallPercent: Float,
    val currentFile: String?
)

data class PipelineResult(
    val outputFiles: List<File>,
    val isImages: Boolean = false,
    val outputDir: File? = null
)

/**
 * Orchestrates a chain of PDF operations on a list of staged files.
 *
 * Execution order:
 *   1. Pre-file actions (Watermark, ExtractPages) — run on each file individually
 *   2. Core action (Merge) — combines all files into one
 *   3. Post-merge actions (PageNumbers, Compress, Lock) — run on the merged output (or each file if no merge)
 *   4. Terminal action (PdfToImages) — converts final output; always last
 *
 * Progress is reported via the [onProgress] callback on the main thread.
 */
object PipelineExecutor {

    suspend fun execute(
        context: Context,
        inputFiles: List<File>,
        actions: List<PipelineAction>,
        onProgress: suspend (PipelineProgress) -> Unit
    ): PipelineResult = withContext(Dispatchers.IO) {
        var currentFiles = inputFiles.toList()
        val sortedActions = actions.sortedBy { it.order }

        val totalSteps = calculateTotalSteps(inputFiles.size, sortedActions)
        var stepsCompleted = 0f

        suspend fun report(step: String, file: String? = null) {
            stepsCompleted++
            val pct = if (totalSteps > 0f) (stepsCompleted / totalSteps).coerceIn(0f, 1f) else 0f
            withContext(Dispatchers.Main) {
                onProgress(PipelineProgress(step, pct, file))
            }
        }

        // ── 1. Pre-file actions ──────────────────────────────────────────
        val preActions = sortedActions.filter { it.order < 200 }
        if (preActions.isNotEmpty()) {
            val newFiles = mutableListOf<File>()
            for ((index, file) in currentFiles.withIndex()) {
                var tempFile = file
                for (action in preActions) {
                    report("${action.displayName} (${index + 1}/${currentFiles.size})", file.name)
                    val outFile = File(context.cacheDir, "pre_${action.order}_${System.currentTimeMillis()}_${file.name}")
                    when (action) {
                        is PipelineAction.Watermark -> {
                            try {
                                if (action.isImageMode && action.stampPath != null) {
                                    PdfUtils.addImageWatermark(
                                        inputFile = tempFile,
                                        outputFile = outFile,
                                        imageFile = File(action.stampPath),
                                        opacity = action.opacity,
                                        scale = action.stampScale,
                                        removeBackground = action.removeBackground
                                    )
                                } else if (action.text.isNotBlank()) {
                                    val customMap = if (action.applyToAll) emptyMap() else parseWatermarkRules(action.customRules)
                                    val defaultTxt = if (action.applyToAll) action.text else null
                                    PdfUtils.addTextWatermark(
                                        inputFile = tempFile,
                                        outputFile = outFile,
                                        defaultText = defaultTxt,
                                        customTextMap = customMap,
                                        color = resolveITextColor(action.color),
                                        opacity = action.opacity,
                                        fontSize = action.fontSize
                                    )
                                } else {
                                    tempFile.copyTo(outFile, overwrite = true)
                                }
                            } catch (e: Exception) {
                                // If watermarking fails for this specific file, copy it as-is
                                e.printStackTrace()
                                if (!outFile.exists() || outFile.length() == 0L) {
                                    tempFile.copyTo(outFile, overwrite = true)
                                }
                            }
                        }
                        is PipelineAction.ExtractPages -> {
                            val pageNumbers = parsePageRanges(action.ranges.keys.joinToString(","))
                            if (pageNumbers.isNotEmpty()) {
                                PdfUtils.extractPages(tempFile, outFile, pageNumbers)
                            } else {
                                tempFile.copyTo(outFile, overwrite = true)
                            }
                        }
                        else -> tempFile.copyTo(outFile, overwrite = true)
                    }
                    if (tempFile != file) tempFile.delete()
                    tempFile = outFile
                }
                newFiles.add(tempFile)
            }
            currentFiles = newFiles
        }

        // ── 2. Core action — Merge ──────────────────────────────────────
        val hasMerge = sortedActions.any { it is PipelineAction.Merge }
        if (hasMerge && currentFiles.size >= 2) {
            report("Merging ${currentFiles.size} files…")
            val mergeOut = File(context.cacheDir, "merged_${System.currentTimeMillis()}.pdf")
            PdfUtils.mergePdfs(currentFiles, mergeOut)
            currentFiles.forEach { if (!inputFiles.contains(it)) it.delete() }
            currentFiles = listOf(mergeOut)
        }

        // ── 3. Post-merge actions ────────────────────────────────────────
        val postActions = sortedActions.filter { it.order in 300..399 }
        if (postActions.isNotEmpty()) {
            val newFiles = mutableListOf<File>()
            for ((index, file) in currentFiles.withIndex()) {
                var tempFile = file
                for (action in postActions) {
                    report("${action.displayName} (${index + 1}/${currentFiles.size})", file.name)
                    val outFile = File(context.cacheDir, "post_${action.order}_${System.currentTimeMillis()}_${file.name}")
                    when (action) {
                        is PipelineAction.PageNumbers -> {
                            PdfUtils.addPageNumbers(tempFile, outFile)
                        }
                        is PipelineAction.Compress -> {
                            PdfUtils.compressPdf(tempFile, outFile, action.quality.toInt())
                        }
                        is PipelineAction.Lock -> {
                            PdfUtils.encryptPdf(tempFile, outFile, action.password)
                        }
                        else -> tempFile.copyTo(outFile, overwrite = true)
                    }
                    if (tempFile != file) tempFile.delete()
                    tempFile = outFile
                }
                newFiles.add(tempFile)
            }
            currentFiles = newFiles
        }

        // ── 4. Terminal action ───────────────────────────────────────────
        val terminalAction = sortedActions.find { it.order >= 400 }
        if (terminalAction != null) {
            report("${terminalAction.displayName}…")
            when (terminalAction) {
                is PipelineAction.PdfToImages -> {
                    val outDir = File(context.cacheDir, "pipeline_images_${System.currentTimeMillis()}")
                    outDir.mkdirs()
                    val format = if (terminalAction.format.equals("PNG", ignoreCase = true)) ImageFormat.PNG else ImageFormat.JPEG
                    for ((index, file) in currentFiles.withIndex()) {
                        PdfUtils.pdfToImages(file, outDir, format, prefix = "file_${index + 1}")
                    }
                    return@withContext PipelineResult(
                        outputFiles = outDir.listFiles()?.toList() ?: emptyList(),
                        isImages = true,
                        outputDir = outDir
                    )
                }
                else -> { /* unknown terminal */ }
            }
        }

        report("Done")
        PipelineResult(outputFiles = currentFiles)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun calculateTotalSteps(numFiles: Int, actions: List<PipelineAction>): Float {
        var steps = 0f
        val hasMerge = actions.any { it is PipelineAction.Merge }
        val preActions = actions.count { it.order < 200 }
        val postActions = actions.count { it.order in 300..399 }
        val terminalActions = actions.count { it.order >= 400 }

        steps += preActions * numFiles
        if (hasMerge) steps += 1
        val numPostFiles = if (hasMerge) 1 else numFiles
        steps += postActions * numPostFiles
        steps += terminalActions * numPostFiles
        steps += 1 // "Done" step
        return steps
    }

    private fun resolveITextColor(androidColor: Int): com.itextpdf.kernel.colors.Color {
        return when (androidColor) {
            android.graphics.Color.RED -> ColorConstants.RED
            android.graphics.Color.BLUE -> ColorConstants.BLUE
            android.graphics.Color.BLACK -> ColorConstants.BLACK
            android.graphics.Color.GRAY -> ColorConstants.GRAY
            else -> ColorConstants.RED
        }
    }

    private fun parseWatermarkRules(rules: List<Pair<String, String>>): Map<Int, String> {
        val map = mutableMapOf<Int, String>()
        for (rule in rules) {
            val text = rule.second
            if (text.isBlank()) continue
            for (part in rule.first.split(",")) {
                val p = part.trim()
                if (p.contains("-")) {
                    val range = p.split("-")
                    if (range.size == 2) {
                        val start = range[0].toIntOrNull() ?: continue
                        val end = range[1].toIntOrNull() ?: continue
                        for (i in start..end) map[i] = text
                    }
                } else {
                    p.toIntOrNull()?.let { map[it] = text }
                }
            }
        }
        return map
    }

    private fun parsePageRanges(pages: String): List<Int> {
        val list = mutableListOf<Int>()
        for (part in pages.split(",")) {
            val p = part.trim()
            if (p.contains("-")) {
                val range = p.split("-")
                if (range.size == 2) {
                    val start = range[0].toIntOrNull() ?: continue
                    val end = range[1].toIntOrNull() ?: continue
                    for (i in start..end) list.add(i)
                }
            } else {
                p.toIntOrNull()?.let { list.add(it) }
            }
        }
        return list.distinct().sorted()
    }
}
