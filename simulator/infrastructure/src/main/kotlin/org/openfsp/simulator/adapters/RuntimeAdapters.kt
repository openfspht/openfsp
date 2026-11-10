package org.openfsp.simulator.adapters

import org.openfsp.simulator.operator.OperatorCallback
import org.openfsp.simulator.operator.spi.CallbackSender
import org.openfsp.simulator.operator.spi.Delays
import org.openfsp.simulator.operator.spi.Timers
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.HexFormat
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class ScheduledTimers(
    private val scheduler: ScheduledExecutorService,
) : Timers {
    override fun after(
        delay: Duration,
        task: () -> Unit,
    ) {
        scheduler.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS)
    }
}

@Component
class SleepingDelays : Delays {
    override fun pause(duration: Duration) {
        if (!duration.isZero) Thread.sleep(duration)
    }
}

@Component
class HttpCallbackSender(
    private val client: RestClient,
    private val json: JsonMapper,
) : CallbackSender {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(
        url: String,
        callback: OperatorCallback,
        secret: String?,
    ): Boolean {
        val body = json.writeValueAsBytes(callback)
        return runCatching {
            client
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .headers { headers -> secret?.let { headers.set(SIGNATURE_HEADER, "sha256=${sign(it, body)}") } }
                .body(body)
                .retrieve()
                .toBodilessEntity()
        }.onFailure { log.warn("Callback for {} to {} failed: {}", callback.paymentId, url, it.message) }
            .isSuccess
    }

    private fun sign(
        secret: String,
        body: ByteArray,
    ): String {
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(secret.toByteArray(), HMAC))
        return HexFormat.of().formatHex(mac.doFinal(body))
    }

    companion object {
        const val SIGNATURE_HEADER = "X-Mock-Signature"
        private const val HMAC = "HmacSHA256"
    }
}
