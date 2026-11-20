plugins {
    id("openfsp.kotlin")
}

description = "Serves the bundled web interface of a Spring Boot application"

dependencies {
    implementation(libs.spring.boot.starter.webmvc)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testRuntimeOnly(libs.kotlin.reflect)
}
