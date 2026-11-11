package org.openfsp.validator.web

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class ValidatorWebApplication

fun main(args: Array<String>) {
    runApplication<ValidatorWebApplication>(*args)
}
