package org.openfsp.simulator.dialects

import org.openfsp.simulator.operator.Caller

fun caller(
    expected: String,
    presented: String?,
): Caller = Caller.ofPublishedAccount(authenticated = constantTimeEquals(expected, presented), credential = presented.orEmpty())
