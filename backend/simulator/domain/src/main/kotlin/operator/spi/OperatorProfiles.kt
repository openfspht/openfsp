package org.openfsp.simulator.operator.spi

import org.openfsp.simulator.operator.OperatorProfile

fun interface OperatorProfiles {
    operator fun get(id: String): OperatorProfile?
}
