package org.openfsp.validator.attestations

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.openfsp.validator.attestation.Attestation
import org.openfsp.validator.engine.LevelVerdict
import org.openfsp.validator.suite.Requirement
import org.springframework.stereotype.Component
import java.awt.Color
import java.io.ByteArrayOutputStream

enum class PdfLocale { EN, FR }

private data class PdfCopy(
    val title: String,
    val statement: String,
    val core: (String) -> String,
    val notConformant: String,
    val conformant: String,
    val claim: String,
    val noClaim: String,
    val protocol: String,
    val profiles: String,
    val noProfiles: String,
    val target: String,
    val baseUrl: String,
    val announcedProtocol: String,
    val unknown: String,
    val suite: String,
    val runDate: String,
    val issuedAt: String,
    val issuer: String,
    val attestationId: String,
    val reportId: String,
    val digest: String,
    val keyId: String,
    val configuration: String,
    val verify: String,
    val verifyHint: String,
    val limits: String,
    val validity: String,
    val validityStatement: String,
    val levels: Map<LevelVerdict, String>,
)

private val COPIES =
    mapOf(
        PdfLocale.EN to
            PdfCopy(
                title = "OpenFSP conformance attestation",
                statement = Attestation.STATEMENT,
                core = { verdict -> "Core: $verdict" },
                notConformant = "Core did not pass: this implementation is not conformant (conformance 3.2).",
                conformant = "Core passed for this configuration and date.",
                claim = "Claim",
                noClaim = "No conformance claim: Core did not pass.",
                protocol = "protocol",
                profiles = "Profiles",
                noProfiles = "No profile scenario ran.",
                target = "Target",
                baseUrl = "Base URL",
                announcedProtocol = "Announced protocol",
                unknown = "unknown",
                suite = "Suite",
                runDate = "Run date",
                issuedAt = "Issued at",
                issuer = "Issuer",
                attestationId = "Attestation",
                reportId = "Report",
                digest = "Report digest (SHA-256, RFC 8785)",
                keyId = "Signing key (EdDSA)",
                configuration = "Configuration",
                verify = "Verify",
                verifyHint = "Scan the code or open this address to check the signature and the report digest.",
                limits = "Limits",
                validity = "Validity",
                validityStatement = Attestation.VALIDITY,
                levels = mapOf(LevelVerdict.PASS to "pass", LevelVerdict.FAIL to "fail", LevelVerdict.INCOMPLETE to "incomplete", LevelVerdict.NOT_APPLICABLE to "not applicable"),
            ),
        PdfLocale.FR to
            PdfCopy(
                title = "Attestation de conformité OpenFSP",
                statement =
                    "Cette attestation consigne les résultats d'une exécution de la suite de conformité OpenFSP contre la cible ci-dessous, " +
                        "échecs compris. Elle ne certifie pas une implémentation (conformité 7.6).",
                core = { verdict -> "Core : $verdict" },
                notConformant = "Core n'a pas réussi : cette implémentation n'est pas conforme (conformité 3.2).",
                conformant = "Core a réussi pour cette configuration et à cette date.",
                claim = "Revendication",
                noClaim = "Aucune revendication de conformité : Core n'a pas réussi.",
                protocol = "protocole",
                profiles = "Profils",
                noProfiles = "Aucun scénario de profil n'a été exécuté.",
                target = "Cible",
                baseUrl = "URL de base",
                announcedProtocol = "Protocole annoncé",
                unknown = "inconnu",
                suite = "Suite",
                runDate = "Date de l'exécution",
                issuedAt = "Émise le",
                issuer = "Émetteur",
                attestationId = "Attestation",
                reportId = "Rapport",
                digest = "Empreinte du rapport (SHA-256, RFC 8785)",
                keyId = "Clé de signature (EdDSA)",
                configuration = "Configuration",
                verify = "Vérifier",
                verifyHint = "Scannez le code ou ouvrez cette adresse pour vérifier la signature et l'empreinte du rapport.",
                limits = "Limites",
                validity = "Validité",
                validityStatement = "Vaut pour cette configuration et cette date ; périmée quand l'implémentation ou la version du protocole change.",
                levels = mapOf(LevelVerdict.PASS to "réussi", LevelVerdict.FAIL to "échoué", LevelVerdict.INCOMPLETE to "incomplet", LevelVerdict.NOT_APPLICABLE to "non applicable"),
            ),
    )

@Component
class AttestationPdf {
    fun render(
        attestation: Attestation,
        kid: String,
        verifyUrl: String,
        locale: PdfLocale,
    ): ByteArray {
        val copy = COPIES.getValue(locale)
        return PDDocument().use { document ->
            document.documentInformation.title = "${copy.title} ${attestation.id}"
            document.documentInformation.author = attestation.issuer
            PdfWriter(document).use { writer ->
                writer.text(copy.title, writer.bold, TITLE_SIZE)
                writer.space(SMALL_GAP)
                writer.paragraph(copy.statement, size = SMALL_SIZE, color = MUTED)
                writer.space(GAP)
                coreBand(writer, attestation.verdicts.core, copy)
                writer.space(GAP)
                writer.heading(copy.claim)
                val claim = attestation.claim
                writer.paragraph(
                    if (claim.level == Requirement.CORE) "${(listOf(claim.target, claim.level) + claim.profiles).joinToString(" + ")}, ${copy.protocol} ${claim.protocolVersion}" else copy.noClaim,
                )
                writer.space(GAP)
                writer.heading(copy.profiles)
                if (attestation.verdicts.profiles.isEmpty()) writer.paragraph(copy.noProfiles, color = MUTED)
                attestation.verdicts.profiles.forEach { writer.field(it.name, copy.levels.getValue(it.verdict), valueColor = verdictColor(it.verdict)) }
                writer.space(GAP)
                writer.field(copy.target, attestation.target.kind)
                writer.field(copy.baseUrl, attestation.target.baseUrl)
                writer.field(copy.announcedProtocol, attestation.target.protocolVersion ?: copy.unknown)
                writer.field(copy.suite, "${attestation.suite.version} / ${copy.protocol} ${attestation.suite.protocolVersion}")
                writer.field(copy.runDate, attestation.run.date)
                writer.field(copy.issuedAt, attestation.issuedAt)
                writer.field(copy.issuer, attestation.issuer)
                writer.field(copy.attestationId, attestation.id, writer.mono)
                writer.field(copy.reportId, attestation.report.id, writer.mono)
                writer.field(copy.digest, attestation.report.digest, writer.mono)
                writer.field(copy.keyId, kid, writer.mono)
                writer.space(GAP)
                writer.heading(copy.configuration)
                attestation.configuration.toSortedMap().forEach { (name, value) -> writer.field(name, display(value), writer.mono) }
                writer.space(GAP)
                writer.heading(copy.verify)
                writer.qr(verifyUrl, copy.verifyHint)
                writer.space(GAP)
                writer.heading(copy.limits)
                attestation.limits.forEach { writer.field(it.reference, it.text) }
                writer.space(GAP)
                writer.heading(copy.validity)
                writer.paragraph("${copy.validityStatement} (${attestation.validity.reference})")
            }
            ByteArrayOutputStream().also(document::save).toByteArray()
        }
    }

    private fun coreBand(
        writer: PdfWriter,
        core: LevelVerdict,
        copy: PdfCopy,
    ) {
        val color = verdictColor(core)
        writer.band(color, copy.core(copy.levels.getValue(core).uppercase()), if (core == LevelVerdict.PASS) copy.conformant else copy.notConformant)
    }

    private fun display(value: Any): String = if (value is Map<*, *>) value.entries.joinToString { "${it.key}: ${it.value}" } else value.toString()

    private fun verdictColor(verdict: LevelVerdict): Color =
        when (verdict) {
            LevelVerdict.PASS -> PASS
            LevelVerdict.FAIL -> FAIL
            else -> WARN
        }

    private companion object {
        const val TITLE_SIZE = 20f
        const val SMALL_SIZE = 9f
        const val GAP = 10f
        const val SMALL_GAP = 6f
        val MUTED = Color(0x55, 0x5f, 0x6b)
        val PASS = Color(0x2e, 0x7d, 0x32)
        val FAIL = Color(0xc6, 0x28, 0x28)
        val WARN = Color(0xb2, 0x6a, 0x00)
    }
}

private class PdfWriter(
    private val document: PDDocument,
) : AutoCloseable {
    val regular: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    val bold: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
    val mono: PDFont = PDType1Font(Standard14Fonts.FontName.COURIER)

    private var stream: PDPageContentStream = newPage()
    private var y = TOP

    fun text(
        value: String,
        font: PDFont,
        size: Float,
        color: Color = INK,
    ) {
        lines(value, font, size, WIDTH).forEach { line ->
            room(size * LEADING)
            y -= size
            write(line, font, size, MARGIN, color)
            y -= size * (LEADING - 1)
        }
    }

    fun paragraph(
        value: String,
        size: Float = BODY,
        color: Color = INK,
    ) = text(value, regular, size, color)

    fun heading(value: String) {
        room(HEADING * LEADING * 2)
        text(value, bold, HEADING)
        space(2f)
    }

    fun field(
        label: String,
        value: String,
        font: PDFont = regular,
        valueColor: Color = INK,
    ) {
        val labels = lines(label, regular, SMALL, LABEL_WIDTH)
        val values = lines(value, font, SMALL, WIDTH - LABEL_WIDTH - GUTTER)
        repeat(maxOf(labels.size, values.size)) { index ->
            room(SMALL * LEADING)
            y -= SMALL
            labels.getOrNull(index)?.let { write(it, regular, SMALL, MARGIN, MUTED) }
            values.getOrNull(index)?.let { write(it, font, SMALL, MARGIN + LABEL_WIDTH + GUTTER, valueColor) }
            y -= SMALL * (LEADING - 1)
        }
    }

    fun band(
        color: Color,
        title: String,
        detail: String,
    ) {
        val height = BAND_HEIGHT
        room(height)
        stream.setNonStrokingColor(color)
        stream.addRect(MARGIN, y - height, WIDTH, height)
        stream.fill()
        write(title, bold, BAND_TITLE, MARGIN + BAND_PADDING, Color.WHITE, y - BAND_PADDING - BAND_TITLE)
        write(fit(detail, regular, SMALL, WIDTH - 2 * BAND_PADDING), regular, SMALL, MARGIN + BAND_PADDING, Color.WHITE, y - height + BAND_PADDING)
        y -= height
    }

    fun qr(
        url: String,
        hint: String,
    ) {
        room(QR_SIZE)
        val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0))
        val module = QR_SIZE / matrix.width
        val top = y
        stream.setNonStrokingColor(INK)
        for (row in 0 until matrix.height) {
            for (column in 0 until matrix.width) {
                if (matrix.get(column, row)) stream.addRect(MARGIN + column * module, top - (row + 1) * module, module, module)
            }
        }
        stream.fill()
        val left = MARGIN + QR_SIZE + GUTTER
        val width = WIDTH - QR_SIZE - GUTTER
        var line = top
        lines(url, mono, SMALL, width).forEach { text ->
            line -= SMALL * LEADING
            write(text, mono, SMALL, left, INK, line)
        }
        lines(hint, regular, SMALL, width).forEach { text ->
            line -= SMALL * LEADING
            write(text, regular, SMALL, left, MUTED, line)
        }
        y = minOf(top - QR_SIZE, line) - SMALL
    }

    fun space(points: Float) {
        y -= points
    }

    override fun close() = stream.close()

    private fun write(
        value: String,
        font: PDFont,
        size: Float,
        x: Float,
        color: Color,
        baseline: Float = y,
    ) {
        stream.beginText()
        stream.setFont(font, size)
        stream.setNonStrokingColor(color)
        stream.newLineAtOffset(x, baseline)
        stream.showText(encodable(value, font))
        stream.endText()
    }

    private fun room(height: Float) {
        if (y - height >= MARGIN) return
        stream.close()
        stream = newPage()
        y = TOP
    }

    private fun newPage(): PDPageContentStream {
        val page = PDPage(PDRectangle.A4)
        document.addPage(page)
        return PDPageContentStream(document, page)
    }

    private fun lines(
        value: String,
        font: PDFont,
        size: Float,
        width: Float,
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
                    val cut = (1..current.length).last { widthOf(current.take(it), font, size) <= width }
                    result += current.take(cut)
                    current = current.drop(cut)
                }
            }
        }
        if (current.isNotEmpty() || result.isEmpty()) result += current
        return result
    }

    private fun fit(
        value: String,
        font: PDFont,
        size: Float,
        width: Float,
    ): String = lines(value, font, size, width).let { if (it.size == 1) it.single() else it.first().dropLast(1) + "..." }

    private fun widthOf(
        value: String,
        font: PDFont,
        size: Float,
    ) = font.getStringWidth(encodable(value, font)) / 1000 * size

    private fun encodable(
        value: String,
        font: PDFont,
    ): String =
        value
            .map { char ->
                if (runCatching { font.encode(char.toString()) }.isSuccess) char else '?'
            }.joinToString("")

    private companion object {
        val INK = Color(0x11, 0x18, 0x27)
        val MUTED = Color(0x55, 0x5f, 0x6b)
        const val MARGIN = 40f
        val WIDTH = PDRectangle.A4.width - 2 * MARGIN
        val TOP = PDRectangle.A4.height - MARGIN
        const val LEADING = 1.35f
        const val BODY = 10.5f
        const val SMALL = 9f
        const val HEADING = 12f
        const val LABEL_WIDTH = 150f
        const val GUTTER = 12f
        const val BAND_HEIGHT = 52f
        const val BAND_PADDING = 12f
        const val BAND_TITLE = 16f
        const val QR_SIZE = 88f
    }
}
