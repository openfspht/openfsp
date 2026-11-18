plugins {
    id("openfsp.library")
}

description = "OpenFSP client for Kotlin and Java: payments, capabilities, configuration and webhook verification"

dependencies {
    api("org.openfsp:openfsp-protocol:$version")
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    testImplementation(project(":sdk-testing"))
}

val integrationTest = "integration"

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags(integrationTest)
    }
}

tasks.register<Test>("integrationTest") {
    description = "Runs the tests against a running gateway."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags(integrationTest)
    }
    providers.gradleProperty("gatewayUrl").orNull?.let { systemProperty("openfsp.gateway.url", it) }
}
