package org.openfsp.validator.attestations.web

import org.openfsp.validator.engine.LevelVerdict
import org.springframework.stereotype.Component
import kotlin.math.roundToInt

@Component
class AttestationBadge {
    fun verdictText(core: LevelVerdict): String = if (core == LevelVerdict.PASS) "pass" else "fail"

    fun svg(
        core: LevelVerdict,
        protocolVersion: String,
        pageUrl: String,
    ): String {
        val value = "${verdictText(core)}, protocol $protocolVersion"
        val left = width(LABEL)
        val right = width(value)
        val total = left + right
        val color = if (core == LevelVerdict.PASS) PASS_COLOR else FAIL_COLOR
        val title = escape("$LABEL: $value")
        return """
            <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="$total" height="$HEIGHT" role="img" aria-label="$title">
              <title>$title</title>
              <a xlink:href="${escape(pageUrl)}" href="${escape(pageUrl)}" target="_blank">
                <clipPath id="r"><rect width="$total" height="$HEIGHT" rx="3"/></clipPath>
                <g clip-path="url(#r)">
                  <rect width="$left" height="$HEIGHT" fill="$LABEL_COLOR"/>
                  <rect x="$left" width="$right" height="$HEIGHT" fill="$color"/>
                </g>
                <g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" font-size="11">
                  <text x="${left / 2}" y="14">${escape(LABEL)}</text>
                  <text x="${left + right / 2}" y="14">${escape(value)}</text>
                </g>
              </a>
            </svg>
            """.trimIndent() + "\n"
    }

    private fun width(text: String) = (text.length * CHARACTER_WIDTH).roundToInt() + PADDING

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    companion object {
        const val LABEL = "OpenFSP core"
        private const val CHARACTER_WIDTH = 6.8
        private const val PADDING = 12
        private const val HEIGHT = 20
        private const val LABEL_COLOR = "#555"
        private const val PASS_COLOR = "#2e7d32"
        private const val FAIL_COLOR = "#c62828"
    }
}
