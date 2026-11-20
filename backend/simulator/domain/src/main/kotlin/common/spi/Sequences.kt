package org.openfsp.simulator.common.spi

fun interface Sequences {
    fun next(counter: String): Long
}
