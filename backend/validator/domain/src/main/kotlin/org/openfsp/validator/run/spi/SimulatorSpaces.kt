package org.openfsp.validator.run.spi

data class SimulatorSpace(
    val id: String,
    val controlUrl: String,
    val publicUrl: String,
)

interface SimulatorSpaces {
    fun open(): SimulatorSpace

    fun close(space: SimulatorSpace)
}
