package com.pdfmerger.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.EncryptionConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.ReaderProperties
import com.itextpdf.kernel.pdf.WriterProperties
import com.itextpdf.kernel.utils.PdfMerger
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.pdfmerger.app.ui.screen.ImageFormat
import java.io.File
import java.io.FileOutputStream

import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.properties.AreaBreakType
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.VerticalAlignment
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState
import com.itextpdf.kernel.pdf.canvas.parser.listener.RegexBasedLocationExtractionStrategy
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor


/**
 * PDF-specific utilities: page count, merge, compress, extract, encrypt, convert.
 */
object PdfUtils {

    /**
     * Gets the page count of a PDF using Android's built-in PdfRenderer.
     * Works with content:// URIs.
     */
    fun getPageCount(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val renderer = PdfRenderer(pfd)
                val count = renderer.pageCount
                renderer.close()
                count
            } ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    /**
     * Gets the page count from a cached File.
     */
    fun getPageCount(file: File): Int {
        return try {
            val reader = PdfReader(file)
            val doc = PdfDocument(reader)
            val count = doc.numberOfPages
            doc.close()
            count
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    /**
     * Checks if a PDF file is encrypted/password protected.
     */
    fun isPdfLocked(file: File): Boolean {
        return try {
            val reader = PdfReader(file)
            val doc = PdfDocument(reader)
            doc.close()
            false
        } catch (e: Exception) {
            e is com.itextpdf.kernel.exceptions.BadPasswordException || 
            e.cause is com.itextpdf.kernel.exceptions.BadPasswordException ||
            e.message?.contains("BadPasswordException") == true ||
            e.message?.contains("Password") == true
        }
    }

    /**
     * Unlocks a PDF by reading it with a password and saving it unencrypted.
     */
    fun unlockPdf(sourceFile: File, destFile: File, password: String) {
        val readerProps = ReaderProperties().setPassword(password.toByteArray())
        val reader = PdfReader(sourceFile, readerProps)
        val writer = PdfWriter(destFile)
        val doc = PdfDocument(reader, writer)
        doc.close()
    }

    /**
     * Merges multiple PDF files into a single output file.
     */
    fun mergePdfs(inputFiles: List<File>, outputFile: File) {
        val pdfWriter = PdfWriter(outputFile)
        val pdfDocument = PdfDocument(pdfWriter)
        val merger = PdfMerger(pdfDocument)

        for (file in inputFiles) {
            val reader = PdfReader(file)
            val sourcePdf = PdfDocument(reader)
            merger.merge(sourcePdf, 1, sourcePdf.numberOfPages)
            sourcePdf.close()
        }

        pdfDocument.close()
    }

    /**
     * Compresses a PDF by re-writing it with a specified compression level.
     * Level 0 = no compression, 9 = max compression.
     */
    fun compressPdf(inputFile: File, outputFile: File, compressionLevel: Int) {
        val reader = PdfReader(inputFile)
        val writerProps = WriterProperties()
            .setCompressionLevel(compressionLevel)
            .setFullCompressionMode(true)
        val writer = PdfWriter(outputFile.absolutePath, writerProps)
        val pdfDoc = PdfDocument(reader, writer)
        pdfDoc.close()
    }

    /**
     * Extracts specific pages (1-indexed) from a PDF into a new file.
     */
    fun extractPages(inputFile: File, outputFile: File, pages: List<Int>) {
        val reader = PdfReader(inputFile)
        val writer = PdfWriter(outputFile)
        val srcDoc = PdfDocument(reader)
        val destDoc = PdfDocument(writer)

        srcDoc.copyPagesTo(pages, destDoc)

        srcDoc.close()
        destDoc.close()
    }

    /**
     * Converts a list of image files into a single PDF document.
     */
    fun imagesToPdf(imageFiles: List<File>, outputFile: File) {
        val writer = PdfWriter(outputFile)
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc)

        for (imageFile in imageFiles) {
            val imageData = ImageDataFactory.create(imageFile.absolutePath)
            val image = Image(imageData)

            // Fit image to A4 page
            val pageSize = PageSize(image.imageWidth, image.imageHeight)
            pdfDoc.addNewPage(pageSize)
            image.setFixedPosition(pdfDoc.numberOfPages, 0f, 0f)
            image.scaleToFit(pageSize.width, pageSize.height)
            document.add(image)
        }

        document.close()
    }

    /**
     * Encrypts a PDF with AES-256 password protection.
     */
    fun encryptPdf(inputFile: File, outputFile: File, password: String) {
        val reader = PdfReader(inputFile)
        val writerProps = WriterProperties()
            .setStandardEncryption(
                password.toByteArray(),
                password.toByteArray(),
                EncryptionConstants.ALLOW_PRINTING,
                EncryptionConstants.ENCRYPTION_AES_256
            )
        val writer = PdfWriter(outputFile.absolutePath, writerProps)
        val srcDoc = PdfDocument(reader)
        val destDoc = PdfDocument(writer)

        srcDoc.copyPagesTo(1, srcDoc.numberOfPages, destDoc)

        srcDoc.close()
        destDoc.close()
    }

    /**
     * Reorders/deletes pages. newOrder is a 1-indexed list of page numbers
     * in the desired output order. Missing pages are effectively deleted.
     */
    fun reorderAndDeletePages(inputFile: File, outputFile: File, newOrder: List<Int>) {
        val reader = PdfReader(inputFile)
        val writer = PdfWriter(outputFile)
        val srcDoc = PdfDocument(reader)
        val destDoc = PdfDocument(writer)

        srcDoc.copyPagesTo(newOrder, destDoc)

        srcDoc.close()
        destDoc.close()
    }

    /**
     * Converts each page of a PDF to an image file (JPEG or PNG).
     * Uses Android's PdfRenderer for native rendering.
     */
    fun pdfToImages(inputFile: File, outputDir: File, format: ImageFormat) {
        val pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)

        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val density = 2.5f
            val bitmap = Bitmap.createBitmap(
                (page.width * density).toInt(),
                (page.height * density).toInt(),
                Bitmap.Config.ARGB_8888
            )
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()

            val outputFile = File(outputDir, "page_${String.format("%03d", i + 1)}.${format.ext}")
            FileOutputStream(outputFile).use { fos ->
                val compressFormat = if (format == ImageFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                bitmap.compress(compressFormat, 95, fos)
            }
            bitmap.recycle()
        }

        renderer.close()
        pfd.close()
    }

    /**
     * Adds a diagonal text watermark to the PDF.
     * If defaultText is provided, it applies to all pages except those in customTextMap.
     * If defaultText is null, only pages in customTextMap get watermarked.
     */
    fun addTextWatermark(
        inputFile: File, 
        outputFile: File, 
        defaultText: String? = null,
        customTextMap: Map<Int, String> = emptyMap(),
        color: com.itextpdf.kernel.colors.Color = ColorConstants.RED, 
        opacity: Float = 0.3f,
        fontSize: Float = 80f
    ) {
        val pdfDoc = PdfDocument(PdfReader(inputFile), PdfWriter(outputFile))
        val font = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD)
        val transparentState = PdfExtGState().setFillOpacity(opacity)

        for (i in 1..pdfDoc.numberOfPages) {
            val textToDraw = customTextMap[i] ?: defaultText
            if (textToDraw.isNullOrBlank()) continue // Skip if no watermark for this page

            val page = pdfDoc.getPage(i)
            val pageSize = page.pageSize
            
            // The critical fix: use `PdfCanvas(page, true)` to wrap the old content in q/Q operators.
            // This resets the graphics state (including inverted CTMs from other software)
            // so our watermark is drawn in standard coordinates (bottom-left origin).
            val pdfCanvas = PdfCanvas(page, true)
            
            pdfCanvas.saveState()
            pdfCanvas.setExtGState(transparentState)

            val canvas = com.itextpdf.layout.Canvas(pdfCanvas, pageSize)
            canvas.setFont(font).setFontSize(fontSize).setFontColor(color)
            canvas.showTextAligned(
                textToDraw,
                pageSize.width / 2,
                pageSize.height / 2,
                TextAlignment.CENTER,
                VerticalAlignment.MIDDLE,
                0.785398f // 45 degrees in radians
            )
            canvas.close()
            pdfCanvas.restoreState()
        }
        pdfDoc.close()
    }

    /**
     * Adds page numbers (e.g., "Page 1 of N") to the bottom of each page.
     */
    fun addPageNumbers(inputFile: File, outputFile: File) {
        val pdfDoc = PdfDocument(PdfReader(inputFile), PdfWriter(outputFile))
        val document = Document(pdfDoc)
        val totalPages = pdfDoc.numberOfPages

        for (i in 1..totalPages) {
            val page = pdfDoc.getPage(i)
            val pageSize = page.pageSize
            document.showTextAligned(
                Paragraph("Page $i of $totalPages").setFontSize(10f).setFontColor(ColorConstants.BLACK),
                pageSize.width / 2,
                20f,
                i,
                TextAlignment.CENTER,
                VerticalAlignment.BOTTOM,
                0f
            )
        }
        document.close()
    }


    /**
     * Converts a plain text file into a PDF.
     */
    fun textToPdf(inputFile: File, outputFile: File) {
        val writer = PdfWriter(outputFile)
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc)
        
        val text = inputFile.readText()
        val paragraph = Paragraph(text)
        document.add(paragraph)
        
        document.close()
    }

    /**
     * Converts a list of text strings into a PDF. Each string in the list creates a new page.
     */
    fun textPagesToPdf(pages: List<String>, destFile: File) {
        val writer = PdfWriter(FileOutputStream(destFile))
        val pdf = PdfDocument(writer)
        val document = Document(pdf, PageSize.A4)
        document.setMargins(36f, 36f, 36f, 36f)

        try {
            for ((index, pageText) in pages.withIndex()) {
                val paragraph = Paragraph(pageText)
                    .setFontSize(12f)
                
                document.add(paragraph)
                
                if (index < pages.size - 1) {
                    document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
                }
            }
        } finally {
            document.close()
        }
    }

    /**
     * Splits a PDF into two files at the given page number.
     * Part 1: pages 1 to splitAtPage.
     * Part 2: pages splitAtPage + 1 to end.
     */
    fun splitPdf(inputFile: File, splitAtPage: Int, outputFile1: File, outputFile2: File) {
        val reader = PdfReader(inputFile)
        val sourceDoc = PdfDocument(reader)
        val totalPages = sourceDoc.numberOfPages
        
        if (splitAtPage < 1 || splitAtPage >= totalPages) {
            sourceDoc.close()
            throw IllegalArgumentException("Invalid split page number")
        }

        // Part 1
        val pdf1 = PdfDocument(PdfWriter(outputFile1))
        sourceDoc.copyPagesTo(1, splitAtPage, pdf1)
        pdf1.close()

        // Part 2
        val pdf2 = PdfDocument(PdfWriter(outputFile2))
        sourceDoc.copyPagesTo(splitAtPage + 1, totalPages, pdf2)
        pdf2.close()

        sourceDoc.close()
    }

    /**
     * Redacts text from a PDF securely using iText pdfSweep (cleanup plugin).
     */
    fun redactText(inputFile: File, outputFile: File, textToRedact: String) {
        val pdfDoc = PdfDocument(PdfReader(inputFile), PdfWriter(outputFile))
        
        // Escape regex special characters from the user's input so it searches literal text
        val escapedText = Regex.escape(textToRedact)
        
        val strategy = com.itextpdf.pdfcleanup.autosweep.RegexBasedCleanupStrategy(escapedText)
        com.itextpdf.pdfcleanup.PdfCleaner.autoSweepCleanUp(pdfDoc, strategy)
        
        pdfDoc.close()
    }
}
