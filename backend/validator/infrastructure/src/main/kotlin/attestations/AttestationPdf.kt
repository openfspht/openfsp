package org.openfsp.validator.attestations

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.openfsp.validator.attestation.Attestation
import org.openfsp.validator.engine.LevelVerdict
import org.openfsp.validator.engine.Verdict
import org.openfsp.validator.suite.Limit
import org.openfsp.validator.suite.Requirement
import org.springframework.stereotype.Component
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.text.MessageFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.ResourceBundle

enum class PdfLocale { EN, FR }

private class PdfCopy(
    locale: PdfLocale,
) {
    private val language = if (locale == PdfLocale.FR) Locale.FRENCH else Locale.ENGLISH
    private val bundle =
        ResourceBundle.getBundle(
            "attestations/pdf",
            if (locale == PdfLocale.FR) Locale.FRENCH else Locale.ROOT,
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES),
        )
    private val dates = DateTimeFormatter.ofPattern(text("dateTime"), language).withZone(ZoneOffset.UTC)

    fun text(key: String): String = bundle.getString(key)

    fun text(
        key: String,
        value: String,
    ): String = text(key).replace("{0}", value)

    fun counted(
        key: String,
        vararg counts: Int,
    ): String = MessageFormat(text(key), language).format(counts.toTypedArray())

    fun level(verdict: LevelVerdict): String = text("level.${verdict.name.lowercase()}")

    fun target(kind: String): String = optional("target.$kind") ?: kind

    fun limit(limit: Limit): String = optional("limit.${limit.reference.replace('#', '-')}") ?: limit.text

    fun reference(reference: String): String {
        val document = reference.substringBefore('#')
        return "${optional("document.$document") ?: document}\u00a0${reference.substringAfter('#')}"
    }

    fun date(value: String): String = runCatching { dates.format(Instant.parse(value)) }.getOrDefault(value)

    private fun optional(key: String): String? = if (bundle.containsKey(key)) bundle.getString(key) else null
}

private data class Fit(
    val scale: Float,
    val valueLines: Int,
)

@Component
class AttestationPdf {
    private val logo: ByteArray = AttestationPdf::class.java.getResourceAsStream(LOGO)!!.use { it.readBytes() }

    fun render(
        attestation: Attestation,
        kid: String,
        verifyUrl: String,
        locale: PdfLocale,
    ): ByteArray {
        val copy = PdfCopy(locale)
        FITS.dropLast(1).forEach { fit -> attempt(attestation, kid, verifyUrl, copy, fit)?.let { return it } }
        return attempt(attestation, kid, verifyUrl, copy, FITS.last(), clip = true)!!
    }

    private fun attempt(
        attestation: Attestation,
        kid: String,
        verifyUrl: String,
        copy: PdfCopy,
        fit: Fit,
        clip: Boolean = false,
    ): ByteArray? =
        PDDocument().use { document ->
            document.documentInformation.title = "${copy.text("title")} ${attestation.id}"
            document.documentInformation.author = attestation.issuer
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            val overflowed = PdfCanvas(document, page).use { canvas -> AttestationSheet(canvas, copy, attestation, kid, verifyUrl, logo, fit).draw() }
            if (overflowed && !clip) null else ByteArrayOutputStream().also(document::save).toByteArray()
        }

    private companion object {
        const val LOGO = "/attestations/openfsp-logo.png"
        val FITS = listOf(Fit(1f, Int.MAX_VALUE), Fit(1f, 8), Fit(0.92f, 6), Fit(0.85f, 4), Fit(0.78f, 3), Fit(0.72f, 2), Fit(0.66f, 1))
    }
}

private class AttestationSheet(
    private val canvas: PdfCanvas,
    private val copy: PdfCopy,
    private val attestation: Attestation,
    private val kid: String,
    private val verifyUrl: String,
    private val logo: ByteArray,
    private val fit: Fit,
) {
    private val scale = fit.scale

    fun draw(): Boolean {
        canvas.floor = footer() + GAP * scale
        var top = header(TOP)
        top = verdict(top - GAP * scale)
        top = facts(top - SECTION_GAP * scale)
        top = identifiers(top - SECTION_GAP * scale)
        configuration(top - SECTION_GAP * scale)
        return canvas.overflowed
    }

    private fun header(top: Float): Float {
        canvas.image(logo, LEFT, top, LOGO_HEIGHT)
        val size = META * scale
        canvas.writeRight(attestation.id, RIGHT, top - LOGO_HEIGHT / 2 + size * 0.2f, canvas.mono, size, TEXT)
        canvas.writeRight(copy.text("issuedOn", copy.date(attestation.issuedAt)), RIGHT, top - LOGO_HEIGHT / 2 - size * 1.4f, canvas.regular, size, MUTED)
        val rule = top - LOGO_HEIGHT - HEADER_GAP * scale
        canvas.rule(LEFT, rule, WIDTH, HAIRLINE, PdfCanvas.HAIRLINE)
        canvas.rule(LEFT, rule, ACCENT_LENGTH, ACCENT, ACCENT_THICKNESS)
        val titleSize = TITLE * scale
        var y = canvas.block(listOf(copy.text("title")), LEFT, rule - HEADER_GAP * scale * 1.3f, canvas.bold, titleSize, INK)
        val statementSize = SMALL * scale
        y = canvas.block(canvas.lines(copy.text("statement"), canvas.regular, statementSize, WIDTH), LEFT, y - 2 * scale, canvas.regular, statementSize, MUTED)
        return y
    }

    private fun verdict(top: Float): Float {
        val core = attestation.verdicts.core
        val color = verdictColor(core)
        val padding = PANEL_PADDING * scale
        val inner = WIDTH - 2 * padding - PANEL_BORDER
        val textLeft = LEFT + PANEL_BORDER + padding
        val coreSize = CORE * scale
        val bodySize = BODY * scale
        val smallSize = SMALL * scale
        val meaning =
            copy.text(
                when (core) {
                    LevelVerdict.PASS -> "conformant"
                    LevelVerdict.INCOMPLETE -> "incomplete"
                    else -> "notConformant"
                },
            )
        val tally = tally()
        val claimLabel = copy.text("claim")
        val claimLeft = canvas.widthOf(claimLabel, canvas.bold, smallSize) + 6 * scale
        val claim = canvas.lines(claimText(), canvas.regular, smallSize, inner - claimLeft)
        val tallyHeight = if (tally == null) 0f else PdfCanvas.heightOf(1, smallSize) + (BAR + BAR_GAP) * scale
        val height = padding * 2 + PdfCanvas.heightOf(1, coreSize) + PdfCanvas.heightOf(1, bodySize) + tallyHeight + 4 * scale + PdfCanvas.heightOf(claim.size, smallSize)
        canvas.frame(LEFT, top - height, WIDTH, height, PANEL, HAIRLINE)
        canvas.rectangle(LEFT, top - height, PANEL_BORDER, height, color)
        var y = top - padding
        val dotRadius = coreSize * 0.28f
        canvas.dot(textLeft + dotRadius, y - coreSize * 0.64f, dotRadius, color)
        y = canvas.block(listOf(copy.text("core", copy.level(core))), textLeft + dotRadius * 2 + 7 * scale, y, canvas.bold, coreSize, TEXT)
        y = canvas.block(listOf(meaning), textLeft, y, canvas.regular, bodySize, TEXT)
        if (tally != null) {
            y = canvas.block(listOf(tally.first), textLeft, y, canvas.regular, smallSize, MUTED)
            val bar = BAR * scale
            canvas.rectangle(textLeft, y - bar, inner, bar, TRACK)
            if (tally.second > 0f) canvas.rectangle(textLeft, y - bar, inner * tally.second, bar, color)
            y -= bar + BAR_GAP * scale
        }
        y -= 4 * scale
        canvas.block(listOf(claimLabel), textLeft, y, canvas.bold, smallSize, MUTED)
        canvas.block(claim, textLeft + claimLeft, y, canvas.regular, smallSize, TEXT)
        return top - height
    }

    private fun tally(): Pair<String, Float>? {
        val results = attestation.verdicts.coreResults
        val total = results.filterKeys { it != Verdict.NOT_APPLICABLE }.values.sum()
        if (total == 0) return null
        val passed = results[Verdict.PASS] ?: 0
        val shortfalls = SHORTFALLS.mapNotNull { (verdict, key) -> results[verdict]?.takeIf { it > 0 }?.let { copy.counted(key, it) } }
        val line = (listOf(copy.counted("tally.passed", passed, total)) + shortfalls).joinToString(" · ")
        return line to passed.toFloat() / total
    }

    private fun claimText(): String {
        val claim = attestation.claim
        if (claim.level != Requirement.CORE) return copy.text("noClaim")
        return "${(listOf(copy.target(claim.target), claim.level) + claim.profiles).joinToString(" + ")}, ${copy.text("protocol")} ${claim.protocolVersion}"
    }

    private fun facts(top: Float): Float {
        val facts =
            listOf(
                copy.text("target") to copy.target(attestation.target.kind),
                copy.text("baseUrl") to attestation.target.baseUrl,
                copy.text("announcedProtocol") to (attestation.target.protocolVersion ?: copy.text("unknown")),
                copy.text("suite") to "${attestation.suite.version} / ${copy.text("protocol")} ${attestation.suite.protocolVersion}",
                copy.text("runDate") to copy.date(attestation.run.date),
                copy.text("issuedAt") to copy.date(attestation.issuedAt),
                copy.text("issuer") to attestation.issuer,
            )
        var left = heading(copy.text("run"), LEFT, top, COLUMN)
        facts.forEach { (label, value) -> left = field(label, value, LEFT, left, COLUMN, FACT_LABEL, mono = false) }
        var right = heading(copy.text("profiles"), SECOND, top, COLUMN)
        val size = BODY * scale * 0.86f
        if (attestation.verdicts.profiles.isEmpty()) right = canvas.block(listOf(copy.text("noProfiles")), SECOND, right, canvas.regular, size, MUTED)
        attestation.verdicts.profiles.forEach { profile ->
            val radius = size * 0.26f
            canvas.dot(SECOND + radius, right - size * 0.66f, radius, verdictColor(profile.verdict))
            canvas.writeRight(copy.level(profile.verdict), RIGHT, right - size, canvas.regular, size, MUTED)
            right = canvas.block(listOf(profile.name), SECOND + radius * 2 + 6 * scale, right, canvas.regular, size, TEXT) - ROW_GAP * scale
        }
        return minOf(left, right)
    }

    private fun identifiers(top: Float): Float {
        var y = heading(copy.text("identifiers"), LEFT, top, WIDTH)
        listOf(
            copy.text("attestationId") to attestation.id,
            copy.text("reportId") to attestation.report.id,
            copy.text("digest") to attestation.report.digest,
            copy.text("keyId") to kid,
        ).forEach { (label, value) -> y = field(label, value, LEFT, y, WIDTH, IDENTIFIER_LABEL, mono = true) }
        return y
    }

    private fun configuration(top: Float) {
        val start = heading(copy.text("configuration"), LEFT, top, WIDTH)
        val size = MONO * scale
        val valueWidth = COLUMN - CONFIG_LABEL - 8 * scale
        val entries =
            attestation.configuration.toSortedMap().map { (name, value) ->
                val lines =
                    if (value is Map<*, *>) {
                        value.entries.flatMap { canvas.lines("${it.key}: ${it.value}", canvas.mono, size, valueWidth) }
                    } else {
                        canvas.lines(value.toString(), canvas.mono, size, valueWidth)
                    }
                name to clamp(lines, size, valueWidth)
            }
        val heights = entries.map { PdfCanvas.heightOf(it.second.size, size) + ROW_GAP * scale }
        val split = (0..entries.size).minBy { maxOf(heights.take(it).sum(), heights.drop(it).sum()) }
        listOf(entries.take(split) to LEFT, entries.drop(split) to SECOND).forEach { (column, x) ->
            var y = start
            column.forEach { (name, lines) ->
                canvas.block(listOf(name), x, y, canvas.regular, size, MUTED)
                y = canvas.block(lines, x + CONFIG_LABEL, y, canvas.mono, size, TEXT) - ROW_GAP * scale
            }
        }
    }

    private fun clamp(
        lines: List<String>,
        size: Float,
        width: Float,
    ): List<String> {
        if (lines.size <= fit.valueLines) return lines
        return lines.take(fit.valueLines - 1) + canvas.ellipsized(lines[fit.valueLines - 1], canvas.mono, size, width)
    }

    private fun footer(): Float {
        val smallSize = FINE * scale
        val headSize = SMALL * scale
        val qr = QR * scale
        val verifyLeft = LEFT + qr + 10 * scale
        val verifyWidth = COLUMN - qr - 10 * scale
        val url = canvas.lines(verifyUrl, canvas.mono, smallSize, verifyWidth)
        val hint = canvas.lines(copy.text("verifyHint"), canvas.regular, smallSize, verifyWidth)
        val leftHeight = maxOf(qr, PdfCanvas.heightOf(1, headSize) + PdfCanvas.heightOf(url.size + hint.size, smallSize) + 2 * scale)
        val limits = attestation.limits.map { canvas.lines("${copy.limit(it)} (${copy.reference(it.reference)})", canvas.regular, smallSize, COLUMN) }
        val validity = canvas.lines("${copy.text("validityStatement")} (${copy.reference(attestation.validity.reference)})", canvas.regular, smallSize, COLUMN)
        val rightHeight = PdfCanvas.heightOf(2, headSize) + PdfCanvas.heightOf(limits.sumOf { it.size } + validity.size, smallSize) + 6 * scale
        val padding = FOOTER_PADDING * scale
        val top = BOTTOM + maxOf(leftHeight, rightHeight) + padding
        canvas.rule(LEFT, top, WIDTH, HAIRLINE, PdfCanvas.HAIRLINE)
        val inner = top - padding
        canvas.qr(verifyUrl, LEFT, inner, qr, INK)
        var y = canvas.block(listOf(copy.text("verify")), verifyLeft, inner, canvas.bold, headSize, INK)
        y = canvas.block(url, verifyLeft, y - 2 * scale, canvas.mono, smallSize, TEXT)
        canvas.block(hint, verifyLeft, y, canvas.regular, smallSize, MUTED)
        y = canvas.block(listOf(copy.text("limits")), SECOND, inner, canvas.bold, headSize, INK)
        limits.forEach { y = canvas.block(it, SECOND, y, canvas.regular, smallSize, MUTED) }
        y = canvas.block(listOf(copy.text("validity")), SECOND, y - 6 * scale, canvas.bold, headSize, INK)
        canvas.block(validity, SECOND, y, canvas.regular, smallSize, MUTED)
        return top
    }

    private fun heading(
        value: String,
        x: Float,
        top: Float,
        width: Float,
    ): Float {
        val size = HEADING * scale
        val y = canvas.block(listOf(value), x, top, canvas.bold, size, INK)
        canvas.rule(x, y + size * 0.1f, width, HAIRLINE, PdfCanvas.HAIRLINE)
        return y - HEADING_GAP * scale
    }

    private fun field(
        label: String,
        value: String,
        x: Float,
        top: Float,
        width: Float,
        labelWidth: Float,
        mono: Boolean,
    ): Float {
        val labelSize = SMALL * scale
        val size = if (mono) MONO * scale else SMALL * scale
        val font = if (mono) canvas.mono else canvas.regular
        val labels = canvas.lines(label, canvas.regular, labelSize, labelWidth - 6 * scale)
        val values = canvas.lines(value, font, size, width - labelWidth)
        val after = canvas.block(labels, x, top, canvas.regular, labelSize, MUTED)
        val below = canvas.block(values, x + labelWidth, top - (labelSize - size), font, size, TEXT)
        return minOf(after, below) - ROW_GAP * scale
    }

    private fun verdictColor(verdict: LevelVerdict): Color =
        when (verdict) {
            LevelVerdict.PASS -> PASS
            LevelVerdict.FAIL -> FAIL
            LevelVerdict.INCOMPLETE -> WARN
            LevelVerdict.NOT_APPLICABLE -> NEUTRAL
        }

    private companion object {
        val INK = Color(0x0b, 0x3b, 0x36)
        val ACCENT = Color(0xe8, 0xa3, 0x3d)
        val TEXT = Color(0x17, 0x17, 0x17)
        val MUTED = Color(0x52, 0x52, 0x52)
        val HAIRLINE = Color(0xe5, 0xe5, 0xe5)
        val PANEL = Color(0xfa, 0xfa, 0xfa)
        val TRACK = Color(0xe5, 0xe5, 0xe5)
        val PASS = Color(0x1f, 0x7a, 0x4d)
        val FAIL = Color(0xb4, 0x23, 0x18)
        val WARN = Color(0xc0, 0x7e, 0x1b)
        val NEUTRAL = Color(0xa3, 0xa3, 0xa3)
        val SHORTFALLS = listOf(Verdict.FAIL to "tally.fail", Verdict.INCONCLUSIVE to "tally.inconclusive", Verdict.ASSISTED to "tally.assisted")

        const val MARGIN = 48f
        const val LEFT = MARGIN
        val RIGHT = PDRectangle.A4.width - MARGIN
        val WIDTH = RIGHT - LEFT
        const val GUTTER = 28f
        val COLUMN = (WIDTH - GUTTER) / 2
        val SECOND = LEFT + COLUMN + GUTTER
        val TOP = PDRectangle.A4.height - 44f
        const val BOTTOM = 40f

        const val LOGO_HEIGHT = 24f
        const val TITLE = 17f
        const val CORE = 17f
        const val BODY = 10f
        const val HEADING = 9.5f
        const val SMALL = 8.5f
        const val MONO = 7.6f
        const val META = 7.6f
        const val FINE = 7.4f

        const val GAP = 16f
        const val SECTION_GAP = 20f
        const val HEADER_GAP = 12f
        const val HEADING_GAP = 7f
        const val ROW_GAP = 2.2f
        const val ACCENT_LENGTH = 56f
        const val ACCENT_THICKNESS = 2f
        const val PANEL_PADDING = 14f
        const val PANEL_BORDER = 3f
        const val BAR = 3f
        const val BAR_GAP = 6f
        const val FACT_LABEL = 98f
        const val IDENTIFIER_LABEL = 190f
        const val CONFIG_LABEL = 72f
        const val QR = 66f
        const val FOOTER_PADDING = 14f
    }
}
