package org.openfsp.validator.web

import org.openfsp.validator.cli.validate
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import kotlin.system.exitProcess

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class ValidatorWebApplication

const val VALIDATE = "validate"

fun main(args: Array<String>) {
    if (args.firstOrNull() == VALIDATE) {
        // The command line stays silent without Spring's logging setup, as the former slf4j-nop jar kept it.
        System.setProperty("slf4j.provider", "org.slf4j.helpers.NOP_FallbackServiceProvider")
        System.setProperty("slf4j.internal.verbosity", "WARN")
        exitProcess(validate(args.drop(1), System.getenv()))
    }
    runApplication<ValidatorWebApplication>(*args)
}
