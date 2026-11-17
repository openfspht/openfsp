package org.openfsp.validator.suites

import org.openfsp.validator.suite.Suite
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories

object BundledConformance {
    private const val BUNDLED = "conformance"

    fun load(workspace: Path = Path.of(System.getProperty("java.io.tmpdir"))): Suite {
        val directory = Files.createTempDirectory(workspace, "openfsp-conformance")
        try {
            extract(directory)
            return DirectorySuiteSource(directory).suite()
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun extract(directory: Path) {
        PathMatchingResourcePatternResolver().getResources("classpath*:$BUNDLED/**/*.*").forEach { resource ->
            val relative = resource.url.toString().substringAfterLast("/$BUNDLED/")
            val target = directory.resolve(relative).normalize()
            check(target.startsWith(directory)) { "bundled conformance file $relative escapes its directory" }
            target.parent.createDirectories()
            resource.inputStream.use { Files.copy(it, target) }
        }
    }
}
