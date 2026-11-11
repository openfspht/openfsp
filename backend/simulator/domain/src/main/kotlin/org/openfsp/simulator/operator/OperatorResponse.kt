package org.openfsp.simulator.operator

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
