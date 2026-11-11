package org.openfsp.validator.web.runs

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.validator.report.Report
import org.openfsp.validator.report.Reports
import org.openfsp.validator.web.problems.ProblemKind
import org.openfsp.validator.web.problems.ValidatorException
import org.openfsp.validator.web.reports.ReportStore
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
import tools.jackson.module.kotlin.readValue
import java.net.URI
import java.time.Clock
import java.util.UUID

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
@RequestMapping("/api/runs")
class RunsController(
    private val runs: RunService,
    private val sessions: RunSessions,
    private val prepared: PreparedRuns,
    private val configs: SimulatorConfigs,
    private val reports: ReportStore,
    private val clock: Clock,
) {
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun prepare(
        @RequestBody request: PrepareRequest,
        http: HttpServletRequest,
    ): ResponseEntity<RunPreparedResource> {
        val run = runs.prepare(request, http.remoteAddr)
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
        runs.start(id, request)
        val location = "$BASE/$id"
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .location(URI.create(location))
            .body(RunCreatedResource(id.toString(), "$location/events", "$location/report"))
    }

    @GetMapping("/{id}/simulator-config")
    fun simulatorConfig(
        @PathVariable id: UUID,
    ): SimulatorConfigResource = prepared.find(id)?.let(configs::of) ?: throw ValidatorException(ProblemKind.NOT_FOUND, "No prepared run $id.")

    @GetMapping("/{id}/events")
    fun events(
        @PathVariable id: UUID,
        @RequestHeader(name = LAST_EVENT_ID, required = false) lastEventId: String?,
    ): SseEmitter {
        val after = lastEventId?.toLongOrNull()
        sessions.find(id)?.let { return it.subscribe(after) }
        val report = stored(id) ?: throw ValidatorException(ProblemKind.NOT_FOUND, "No run $id.")
        val replay = sessions.transient(id)
        RunEvents.replay(id, report).forEach { (name, payload) -> replay.publish(name, payload) }
        replay.finish(clock.instant())
        return replay.subscribe(after)
    }

    @GetMapping("/{id}", "/{id}/report")
    fun report(
        @PathVariable id: UUID,
    ): ResponseEntity<String> {
        if (prepared.isWaiting(id)) throw ValidatorException(ProblemKind.RUN_NOT_STARTED, "Start it with POST $BASE/$id/start.")
        val session = sessions.find(id)
        if (session != null && !session.finished) throw ValidatorException(ProblemKind.RUN_IN_PROGRESS, "Follow $BASE/$id/events until the summary.")
        reports.find(id)?.let { return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(it.body + "\n") }
        session?.failure?.let { failure -> throw ValidatorException(ProblemKind.entries.first { it.type == failure.type }, failure.detail) }
        throw ValidatorException(ProblemKind.NOT_FOUND, "No report for run $id.")
    }

    private fun stored(id: UUID): Report? = reports.find(id)?.let { Reports.json.readValue<Report>(it.body) }

    private companion object {
        const val BASE = "/api/runs"
        const val LAST_EVENT_ID = "Last-Event-ID"
    }
}
