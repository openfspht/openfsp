package org.openfsp.ui

import org.springframework.core.io.ResourceLoader

enum class ContentCoding(
    val token: String,
    val suffix: String,
) {
    BROTLI("br", ".br"),
    GZIP("gzip", ".gz"),
}

class IndexPage(
    resources: ResourceLoader,
) {
    private val plain: ByteArray? = read(resources, INDEX)
    private val encoded: Map<ContentCoding, ByteArray> =
        ContentCoding.entries.mapNotNull { coding -> read(resources, INDEX + coding.suffix)?.let { coding to it } }.toMap()

    val present: Boolean get() = plain != null

    fun negotiate(acceptEncoding: String?): Variant? {
        val body = plain ?: return null
        val accepted = accepted(acceptEncoding)
        return ContentCoding.entries
            .firstOrNull { it.token in accepted && it in encoded }
            ?.let { Variant(it, encoded.getValue(it)) } ?: Variant(null, body)
    }

    class Variant(
        val coding: ContentCoding?,
        val body: ByteArray,
    )

    private fun accepted(header: String?): Set<String> =
        header
            .orEmpty()
            .split(',')
            .map { it.split(';').map(String::trim) }
            .filter { parts -> parts.first().isNotEmpty() && quality(parts.drop(1)) > 0.0 }
            .map { it.first().lowercase() }
            .toSet()

    private fun quality(parameters: List<String>): Double =
        parameters.firstOrNull { it.startsWith("q=", ignoreCase = true) }?.substring(2)?.toDoubleOrNull() ?: 1.0

    private fun read(
        resources: ResourceLoader,
        path: String,
    ): ByteArray? = resources.getResource(path).takeIf { it.exists() && it.isReadable }?.inputStream?.use { it.readBytes() }

    companion object {
        const val LOCATION = "classpath:/openfsp-ui/"
        private const val INDEX = "${LOCATION}index.html"
    }
}
