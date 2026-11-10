package org.openfsp.simulator.operator

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

enum class Scenario {
    SUCCESS,
    DECLINE,
    INSUFFICIENT,
    TIMEOUT,
    TIMEOUT_THEN_SUCCESS,
    TIMEOUT_THEN_NOTHING,
    PENDING_FOREVER,
    EXPIRE,
    NO_CALLBACK,
    DUPLICATE_CALLBACK,
    LATE_CALLBACK,
    UNKNOWN_STATUS,
    PROVIDER_ERROR,
    CREDENTIAL_ECHO,
    CONTRADICT,
    AMOUNT_MISMATCH,
    ;

    val hangs: Boolean get() = this in setOf(TIMEOUT, TIMEOUT_THEN_SUCCESS, TIMEOUT_THEN_NOTHING)

    val settlesBy: Pair<Outcome, String?>?
        get() =
            when (this) {
                DECLINE -> Outcome.FAILED to "DECLINED"
                INSUFFICIENT -> Outcome.FAILED to "INSUFFICIENT_FUNDS"
                PENDING_FOREVER, EXPIRE, PROVIDER_ERROR, TIMEOUT_THEN_NOTHING, CREDENTIAL_ECHO -> null
                else -> Outcome.SUCCESSFUL to null
            }
}

enum class Outcome(
    val wire: String,
) {
    SUCCESSFUL("SUCCESSFUL"),
    FAILED("FAILED"),
    EXPIRED("EXPIRED"),
}

enum class Dialect(
    val scenarios: Set<Scenario>,
) {
    OPENFSP(Scenario.entries.toSet()),
    MONCASH(NETWORK_FAILURES + setOf(Scenario.SUCCESS, Scenario.PENDING_FOREVER, Scenario.EXPIRE)),
    NATCASH(
        NETWORK_FAILURES +
            setOf(
                Scenario.SUCCESS,
                Scenario.DECLINE,
                Scenario.PENDING_FOREVER,
                Scenario.EXPIRE,
                Scenario.NO_CALLBACK,
                Scenario.DUPLICATE_CALLBACK,
                Scenario.LATE_CALLBACK,
            ),
    ),
    ;

    val rotation: List<Scenario> get() = Scenario.entries.filter { it in scenarios && it !in TESTS_ONLY }
}

private val TESTS_ONLY: Set<Scenario>
    get() = setOf(Scenario.CREDENTIAL_ECHO)

private val NETWORK_FAILURES: Set<Scenario>
    get() = setOf(Scenario.TIMEOUT, Scenario.TIMEOUT_THEN_SUCCESS, Scenario.TIMEOUT_THEN_NOTHING, Scenario.PROVIDER_ERROR)

data class Caller(
    val authenticated: Boolean,
    val credential: String,
)

data class OperatorProfile(
    val id: String,
    val dialect: Dialect,
    val referencePrefix: String,
    val lookup: Boolean,
    val callbacks: Boolean,
    val idempotentByOrder: Boolean,
    val statements: Boolean,
    val returnUrl: String?,
) {
    init {
        require(id.startsWith("mock_")) { "simulated operators use the reserved mock_ prefix" }
    }

    fun hostsCheckoutFor(payment: SimulatedPayment): Boolean = dialect != Dialect.OPENFSP || payment.msisdn == null
}

data class SimulationSettings(
    val responseDelay: Duration,
    val settleAfter: Duration,
    val hangDuration: Duration,
    val lateCallbackDelay: Duration,
    val publicBaseUrl: String,
)

data class SimulatedPayment(
    val operator: String,
    val paymentId: String,
    val orderId: String,
    val amount: String,
    val msisdn: String?,
    val callbackUrl: String?,
    val scenario: Scenario,
    val createdAt: Instant,
    val expiresAt: Instant,
    val outcome: Outcome? = null,
    val reason: String? = null,
    val reportedAmount: String? = null,
    val callbacksSent: Int = 0,
    val lookups: Int = 0,
) {
    val isSettled: Boolean get() = outcome != null

    fun settle(
        outcome: Outcome,
        reason: String?,
    ): SimulatedPayment =
        if (isSettled) {
            this
        } else {
            copy(outcome = outcome, reason = reason, reportedAmount = if (scenario == Scenario.AMOUNT_MISMATCH) amount.toBigDecimal().minus(java.math.BigDecimal.ONE).toPlainString() else null)
        }

    fun asReported(): OperatorPayment = OperatorPayment(paymentId, orderId, outcome?.wire ?: PENDING, reportedAmount ?: amount, reason, expiresIn())

    fun asLookedUp(): OperatorPayment =
        when {
            scenario == Scenario.UNKNOWN_STATUS && isSettled -> asReported().copy(status = "IN_REVIEW")
            scenario == Scenario.CONTRADICT && outcome == Outcome.SUCCESSFUL && lookups > 1 -> asReported().copy(status = "FAILED", reason = "DECLINED")
            else -> asReported()
        }

    private fun expiresIn(): Long? = if (isSettled) null else Duration.between(createdAt, expiresAt).seconds

    private companion object {
        const val PENDING = "PENDING"
    }
}

data class OperatorPayment(
    val paymentId: String,
    val orderId: String,
    val status: String,
    val amount: String,
    val reason: String?,
    val expiresIn: Long?,
    val checkoutUrl: String? = null,
)

data class OperatorCallback(
    val paymentId: String,
    val orderId: String,
    val status: String,
    val amount: String,
    val reason: String?,
)

data class CreatePaymentAtOperator(
    val orderId: String,
    val amount: String,
    val msisdn: String?,
    val description: String?,
    val callbackUrl: String?,
)

data class StatementLine(
    val reference: String,
    val orderId: String?,
    val type: String,
    val amount: String,
    val bookedAt: String,
)

data class OperatorStatement(
    val businessDate: LocalDate,
    val closed: Boolean,
    val entries: List<StatementLine>,
)

data class StatementDays(
    val today: LocalDate,
    val closed: List<LocalDate>,
)

data class JournalEntry(
    val operator: String,
    val receivedAt: Instant,
    val method: String,
    val path: String,
    val body: String,
    val remoteAddress: String = "",
    val headers: Map<String, String> = emptyMap(),
    val status: Int = 0,
    val durationMs: Long = 0,
    val responseHeaders: Map<String, String> = emptyMap(),
    val responseBody: String = "",
)

sealed interface OperatorResponse<out T> {
    data class Answered<T>(
        val status: Int,
        val body: T,
    ) : OperatorResponse<T>

    data class Refused(
        val status: Int,
        val code: String,
        val message: String,
    ) : OperatorResponse<Nothing>
}
