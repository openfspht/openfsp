package org.openfsp.validator.web.suite

import org.openfsp.validator.suite.CatalogueCheck
import org.openfsp.validator.suite.Suite
import org.openfsp.validator.suite.SuiteLoader
import org.openfsp.validator.suite.spi.SuiteSource
import org.openfsp.validator.web.configuration.ValidatorProperties
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories

@Component
class ConformanceSuite(
    properties: ValidatorProperties,
) : SuiteSource {
    val suite: Suite = SuiteLoader.load(properties.conformance ?: extractBundled())

    init {
        val problems = CatalogueCheck.problems(suite)
        check(problems.isEmpty()) { "the conformance catalogue is inconsistent: ${problems.joinToString("; ")}" }
    }

    override fun suite(): Suite = suite

    private fun extractBundled(): Path {
        val directory = Files.createTempDirectory("openfsp-conformance")
        PathMatchingResourcePatternResolver().getResources("classpath*:$BUNDLED/**/*.*").forEach { resource ->
            val relative = resource.url.toString().substringAfterLast("/$BUNDLED/")
            val target = directory.resolve(relative).normalize()
            check(target.startsWith(directory)) { "bundled conformance file $relative escapes its directory" }
            target.parent.createDirectories()
            resource.inputStream.use { Files.copy(it, target) }
        }
        return directory
    }

    private companion object {
        const val BUNDLED = "conformance"
    }
}
