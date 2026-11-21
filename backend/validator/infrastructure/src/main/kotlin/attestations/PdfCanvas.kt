package org.openfsp.validator.attestations

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.awt.Color

internal class PdfCanvas(
    private val document: PDDocument,
    page: PDPage,
) : AutoCloseable {
    val regular: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    val bold: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
    val mono: PDFont = PDType1Font(Standard14Fonts.FontName.COURIER)

    private val stream = PDPageContentStream(document, page)

    var floor = 0f
    var overflowed = false
        private set

    fun write(
        value: String,
        x: Float,
        baseline: Float,
        font: PDFont,
        size: Float,
        color: Color,
    ) {
        if (baseline < floor) {
            overflowed = true
            return
        }
        stream.beginText()
        stream.setFont(font, size)
        stream.setNonStrokingColor(color)
        stream.newLineAtOffset(x, baseline)
        stream.showText(encodable(value, font))
        stream.endText()
    }

    fun writeRight(
        value: String,
        right: Float,
        baseline: Float,
        font: PDFont,
        size: Float,
        color: Color,
    ) = write(value, right - widthOf(value, font, size), baseline, font, size, color)

    fun block(
        lines: List<String>,
        x: Float,
        top: Float,
        font: PDFont,
        size: Float,
        color: Color,
    ): Float {
        lines.forEachIndexed { index, line -> write(line, x, top - size - index * size * LEADING, font, size, color) }
        return top - heightOf(lines.size, size)
    }

    fun rule(
        x: Float,
        y: Float,
        width: Float,
        color: Color,
        thickness: Float,
    ) {
        if (y < floor) return
        rectangle(x, y - thickness / 2, width, thickness, color)
    }

    fun rectangle(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Color,
    ) {
        stream.setNonStrokingColor(color)
        stream.addRect(x, y, width, height)
        stream.fill()
    }

    fun frame(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        fill: Color,
        stroke: Color,
    ) {
        stream.setNonStrokingColor(fill)
        stream.setStrokingColor(stroke)
        stream.setLineWidth(HAIRLINE)
        stream.addRect(x, y, width, height)
        stream.fillAndStroke()
    }

    fun dot(
        centerX: Float,
        centerY: Float,
        radius: Float,
        color: Color,
    ) {
        if (centerY - radius < floor) return
        val handle = radius * CIRCLE_HANDLE
        stream.setNonStrokingColor(color)
        stream.moveTo(centerX + radius, centerY)
        stream.curveTo(centerX + radius, centerY + handle, centerX + handle, centerY + radius, centerX, centerY + radius)
        stream.curveTo(centerX - handle, centerY + radius, centerX - radius, centerY + handle, centerX - radius, centerY)
        stream.curveTo(centerX - radius, centerY - handle, centerX - handle, centerY - radius, centerX, centerY - radius)
        stream.curveTo(centerX + handle, centerY - radius, centerX + radius, centerY - handle, centerX + radius, centerY)
        stream.fill()
    }

    fun image(
        bytes: ByteArray,
        x: Float,
        top: Float,
        height: Float,
    ): Float {
        val image = PDImageXObject.createFromByteArray(document, bytes, "logo")
        val width = height * image.width / image.height
        stream.drawImage(image, x, top - height, width, height)
        return width
    }

    fun qr(
        content: String,
        x: Float,
        top: Float,
        size: Float,
        color: Color,
    ) {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0))
        val module = size / matrix.width
        stream.setNonStrokingColor(color)
        for (row in 0 until matrix.height) {
            for (column in 0 until matrix.width) {
                if (matrix.get(column, row)) stream.addRect(x + column * module, top - (row + 1) * module, module, module)
            }
        }
        stream.fill()
    }

    fun lines(
        value: String,
        font: PDFont,
        size: Float,
        width: Float,
        limit: Int = Int.MAX_VALUE,
    ): List<String> {
        val result = mutableListOf<String>()
        var current = ""
        value.split(' ').forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (widthOf(candidate, font, size) <= width) {
                current = candidate
            } else {
                if (current.isNotEmpty()) result += current
                current = word
                while (widthOf(current, font, size) > width) {
                    val fitting = (1..current.length).last { widthOf(current.take(it), font, size) <= width }
                    val boundary = current.take(fitting).indexOfLast { it in BREAKS } + 1
                    val cut = if (boundary > fitting / 2) boundary else fitting
                    result += current.take(cut)
                    current = current.drop(cut)
                }
            }
        }
        if (current.isNotEmpty() || result.isEmpty()) result += current
        return if (result.size <= limit) result else result.take(limit - 1) + ellipsized(result[limit - 1], font, size, width)
    }

    fun widthOf(
        value: String,
        font: PDFont,
        size: Float,
    ) = font.getStringWidth(encodable(value, font)) / THOUSAND * size

    override fun close() = stream.close()

    fun ellipsized(
        value: String,
        font: PDFont,
        size: Float,
        width: Float,
    ): String {
        var kept = value.trimEnd()
        while (kept.isNotEmpty() && widthOf("$kept$ELLIPSIS", font, size) > width) kept = kept.dropLast(1)
        return "$kept$ELLIPSIS"
    }

    private fun encodable(
        value: String,
        font: PDFont,
    ): String =
        value
            .map { char -> if (char in NARROW_SPACES) ' ' else char }
            .map { char -> if (runCatching { font.encode(char.toString()) }.isSuccess) char else '?' }
            .joinToString("")

    companion object {
        const val LEADING = 1.4f
        const val HAIRLINE = 0.6f
        const val ELLIPSIS = "…"
        private const val THOUSAND = 1000f
        private const val CIRCLE_HANDLE = 0.5523f
        private val BREAKS = setOf('/', '-', '_', '.', ',', '&', '?', '=')
        private val NARROW_SPACES = setOf(' ', ' ')

        fun heightOf(
            lines: Int,
            size: Float,
        ) = lines * size * LEADING
    }
}
