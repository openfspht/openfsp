package org.openfsp.validator.web.runs

import org.openfsp.validator.web.configuration.ValidatorProperties
import org.openfsp.validator.web.problems.ProblemKind
import org.openfsp.validator.web.problems.ValidatorException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class SimulatorSpace(
    val id: String,
    val controlUrl: String,
    val publicUrl: String,
)

interface SimulatorSpaces {
    fun open(): SimulatorSpace

    fun close(space: SimulatorSpace)
}

@Component
class HttpSimulatorSpaces(
    private val properties: ValidatorProperties,
    private val json: JsonMapper,
) : SimulatorSpaces {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build()

    override fun open(): SimulatorSpace {
        val control = properties.simulator.controlUrl.trimEnd('/')
        val request = HttpRequest.newBuilder(URI.create("$control$SPACES")).timeout(TIMEOUT).POST(HttpRequest.BodyPublishers.noBody()).build()
        val response =
            try {
                client.send(request, HttpResponse.BodyHandlers.ofByteArray())
            } catch (exception: IOException) {
                log.warn("the simulator at {} is unreachable: {}", control, exception.message)
                throw unavailable()
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                throw unavailable()
            }
        if (response.statusCode() != CREATED) {
            log.warn("the simulator refused a space with {}", response.statusCode())
            throw unavailable()
        }
        val id = runCatching { json.readTree(response.body()).path("space").asString() }.getOrNull()?.takeIf(SHAPE::matches) ?: throw unavailable()
        return SimulatorSpace(id, "$control$SPACES/$id", "${properties.simulator.publicUrl.trimEnd('/')}$SPACES/$id")
    }

    override fun close(space: SimulatorSpace) {
        val request = HttpRequest.newBuilder(URI.create(space.controlUrl)).timeout(TIMEOUT).DELETE().build()
        try {
            val status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
            if (status != NO_CONTENT && status != NOT_FOUND) log.warn("the simulator answered {} when closing space {}", status, space.id)
        } catch (exception: IOException) {
            log.warn("the simulator space {} stays open until it expires: {}", space.id, exception.message)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun unavailable() = ValidatorException(ProblemKind.SIMULATOR_UNAVAILABLE, "The simulator cannot open a space for this run now. Try again in a few minutes.")

    private companion object {
        const val SPACES = "/runs"
        const val CREATED = 201
        const val NO_CONTENT = 204
        const val NOT_FOUND = 404
        val TIMEOUT: Duration = Duration.ofSeconds(10)
        val SHAPE = Regex("[0-9a-f]{32}")
    }
}
