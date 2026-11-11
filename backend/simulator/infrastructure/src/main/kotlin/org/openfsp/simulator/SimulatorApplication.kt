package org.openfsp.simulator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class SimulatorApplication

fun main(args: Array<String>) {
    runApplication<SimulatorApplication>(*args)
}
