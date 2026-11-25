package com.example.data

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

data class StoredPdfInfo(
    val fileName: String,
    val localPath: String,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val bytes: ByteArray
)

object PdfStorageManager {

    private const val MAX_INLINE_PDF_BYTES = 18L * 1024L * 1024L // Stay safely under Gemini 20MB inline limit

    fun copyPdfToInternalStorage(context: Context, uri: Uri): StoredPdfInfo {
        val resolver = context.contentResolver
        val displayName = resolveDisplayName(context, uri)
            ?.takeIf { it.isNotBlank() }
            ?: "Document_${System.currentTimeMillis()}.pdf"
        val sanitizedName = if (displayName.endsWith(".pdf", ignoreCase = true)) {
            displayName
        } else {
            "$displayName.pdf"
        }

        val pdfDir = File(context.filesDir, "pdfs").apply { mkdirs() }
        val targetFile = File(pdfDir, "${System.currentTimeMillis()}_${sanitizedName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}")

        val bytes = resolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: throw IllegalStateException("Unable to read the selected PDF file.")

        if (bytes.isEmpty()) {
            throw IllegalStateException("Selected PDF file is empty.")
        }
        if (bytes.size.toLong() > MAX_INLINE_PDF_BYTES) {
            throw IllegalStateException("PDF exceeds 18 MB inline processing limit. Please choose a smaller document.")
        }

        FileOutputStream(targetFile).use { it.write(bytes) }
        val pageCount = countPdfPages(targetFile)

        return StoredPdfInfo(
            fileName = sanitizedName,
            localPath = targetFile.absolutePath,
            pageCount = pageCount,
            fileSizeBytes = bytes.size.toLong(),
            bytes = bytes
        )
    }

    /**
     * Generates a real, valid multi-page PDF document on device using Android's PdfDocument API
     * so users can immediately test real PDF RAG ingestion, chunk retrieval, and WhatsApp simulation.
     */
    fun createSampleSupportGuidePdf(context: Context): StoredPdfInfo {
        val pdfDir = File(context.filesDir, "pdfs").apply { mkdirs() }
        val fileName = "Customer_Support_Policy_Guide.pdf"
        val targetFile = File(pdfDir, "${System.currentTimeMillis()}_$fileName")

        val document = PdfDocument()
        val pages = listOf(
            "Page 1: Shipping, Delivery & Tracking" to listOf(
                "Standard Shipping: Free on orders over $50. Delivery takes 3 to 5 business days across the continental US.",
                "Express Delivery: Flat rate $14.99 for 1 to 2 business day delivery when ordered before 2:00 PM EST.",
                "International Orders: Delivered in 7 to 12 business days via DHL Express. Customs duties are calculated at checkout.",
                "Order Tracking: Customers receive an SMS and WhatsApp tracking link within 4 hours of warehouse dispatch.",
                "Address Changes: Shipping addresses can be modified within 2 hours of placing an order before fulfillment begins."
            ),
            "Page 2: Returns, Refunds & Warranty" to listOf(
                "30-Day Return Policy: Unused items in original packaging can be returned within 30 calendar days of delivery for a full refund.",
                "Return Shipping: Prepaid return labels are free for defective items or wrong shipments. Standard returns deduct a $6.95 label fee.",
                "Refund Timeline: Approved refunds are processed back to the original payment method within 3 to 5 business days after warehouse inspection.",
                "1-Year Hardware Warranty: Covers manufacturing defects, battery failures, and charging faults. Physical drops and water damage are excluded.",
                "Instant Replacements: VIP members receive advance replacement shipments before returning the defective unit."
            ),
            "Page 3: Subscriptions, Billing & Live Support" to listOf(
                "Pro Plan Pricing: $29 per month or $290 per year (saves 2 months). Includes priority 24/7 WhatsApp support and free express shipping.",
                "Cancellation Policy: Subscriptions can be paused or canceled anytime from Account > Billing with zero cancellation fees.",
                "Billing Disputes: Duplicate charges or unauthorized renewals are prioritized for immediate review by our human billing specialists.",
                "Live Human Hours: Live agents are available Monday through Saturday, 8:00 AM to 9:00 PM EST. Urgent escalations receive callback within 15 minutes.",
                "Escalation Contact: For damaged deliveries, missing packages, or unresolved billing disputes, ask for a live representative anytime."
            )
        )

        val titlePaint = Paint().apply {
            color = Color.rgb(13, 36, 30)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.rgb(10, 127, 88)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(30, 41, 37)
            textSize = 11.5f
            isAntiAlias = true
        }

        pages.forEachIndexed { index, (heading, bullets) ->
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawText("Acme Commerce — Official Support Knowledge Base", 42f, 58f, titlePaint)
            canvas.drawText(heading, 42f, 90f, subtitlePaint)

            var y = 130f
            bullets.forEach { line ->
                val wrapped = wrapText(line, 74)
                wrapped.forEachIndexed { lineIdx, segment ->
                    val prefix = if (lineIdx == 0) "• " else "  "
                    canvas.drawText("$prefix$segment", 46f, y, bodyPaint)
                    y += 18f
                }
                y += 12f
            }

            document.finishPage(page)
        }

        FileOutputStream(targetFile).use { output ->
            document.writeTo(output)
        }
        document.close()

        val bytes = targetFile.readBytes()
        val pageCount = countPdfPages(targetFile)

        return StoredPdfInfo(
            fileName = fileName,
            localPath = targetFile.absolutePath,
            pageCount = pageCount,
            fileSizeBytes = bytes.size.toLong(),
            bytes = bytes
        )
    }

    fun deleteLocalPdf(path: String) {
        runCatching {
            val file = File(path)
            if (file.exists()) file.delete()
        }
    }

    private fun countPdfPages(file: File): Int {
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount.coerceAtLeast(1)
                }
            }
        }.getOrDefault(1)
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull()
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        for (word in words) {
            if (current.length + word.length + 1 > maxChars && current.isNotEmpty()) {
                lines.add(current.toString())
                current.clear()
            }
            if (current.isNotEmpty()) current.append(" ")
            current.append(word)
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }
}
