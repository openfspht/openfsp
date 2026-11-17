package org.openfsp.validator.runs.web

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.validator.problem.ProblemKind
import org.openfsp.validator.problem.ValidatorException
import org.openfsp.validator.run.RunOrder
import org.openfsp.validator.run.api.FollowRuns
import org.openfsp.validator.run.api.PrepareRuns
import org.openfsp.validator.run.api.StartRuns
import org.openfsp.validator.shared.configuration.ValidatorProperties
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.net.URI
import java.util.UUID

data class PrepareRequest(
    val target: String? = null,
)

data class RunRequest(
    val target: String? = null,
    val baseUrl: String? = null,
    val apiKey: String? = null,
    val restrictedKey: String? = null,
    val inputs: Map<String, String> = emptyMap(),
) {
    fun toOrder() = RunOrder(target, baseUrl, apiKey, restrictedKey, inputs)
}

data class RunPreparedResource(
    val id: String,
    val target: String,
    val start: String,
    val events: String,
    val report: String,
    val simulator: SimulatorConfigResource,
)

data class RunCreatedResource(
    val id: String,
    val events: String,
    val report: String,
)

@RestController
@RequestMapping(RunsController.BASE)
class RunsController(
    private val preparing: PrepareRuns,
    private val starting: StartRuns,
    private val following: FollowRuns,
    private val configs: SimulatorConfigs,
    private val properties: ValidatorProperties,
) {
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun prepare(
        @RequestBody request: PrepareRequest,
        http: HttpServletRequest,
    ): ResponseEntity<RunPreparedResource> {
        val run = preparing.prepare(request.target, http.remoteAddr)
        val location = "$BASE/${run.id}"
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .location(URI.create(location))
            .body(RunPreparedResource(run.id.toString(), run.target.wire, "$location/start", "$location/events", "$location/report", configs.of(run)))
    }

    @PostMapping("/{id}/start", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun start(
        @PathVariable id: UUID,
        @RequestBody request: RunRequest,
    ): ResponseEntity<RunCreatedResource> {
        starting.start(id, request.toOrder())
        val location = "$BASE/$id"
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .location(URI.create(location))
            .body(RunCreatedResource(id.toString(), "$location/events", "$location/report"))
    }

    @GetMapping("/{id}/simulator-config")
    fun simulatorConfig(
        @PathVariable id: UUID,
    ): SimulatorConfigResource = preparing.find(id)?.let(configs::of) ?: throw ValidatorException(ProblemKind.NOT_FOUND, "No prepared run $id.")

    @GetMapping("/{id}/events")
    fun events(
        @PathVariable id: UUID,
        @RequestHeader(name = LAST_EVENT_ID, required = false) lastEventId: String?,
    ): SseEmitter {
        val stream = SseRunStream(SseEmitter(properties.eventTimeout.toMillis()))
        following.follow(id, lastEventId?.toLongOrNull(), stream)
        return stream.emitter
    }

    @GetMapping("/{id}", "/{id}/report")
    fun report(
        @PathVariable id: UUID,
    ): ResponseEntity<String> = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(following.report(id) + "\n")

    companion object {
        const val BASE = "/api/runs"
        private const val LAST_EVENT_ID = "Last-Event-ID"
    }
}
