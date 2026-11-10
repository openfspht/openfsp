package org.openfsp.gateway

import org.openfsp.gateway.cli.HashCommand
import org.openfsp.gateway.security.admin.AdminTokenHashing
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
