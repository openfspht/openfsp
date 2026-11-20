package org.openfsp.gateway

import org.openfsp.gateway.shared.HashCommand
import org.openfsp.gateway.shared.security.AdminTokenHashing
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import kotlin.system.exitProcess

@SpringBootApplication
@ConfigurationPropertiesScan
class GatewayApplication

fun main(args: Array<String>) {
    if (args.firstOrNull() == HashCommand.NAME) exitProcess(HashCommand(AdminTokenHashing()).run())
    runApplication<GatewayApplication>(*args)
}
