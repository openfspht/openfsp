plugins {
    id("openfsp.jpa")
}

dependencies {
    implementation(project(":validator:domain"))
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.sentry.spring.boot.starter)
    implementation(libs.sentry.logback)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.json.schema.validator)
    implementation(project(":http"))
    implementation(libs.pdfbox)
    implementation(libs.zxing.core)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.actuator.test)
    testImplementation(libs.spring.boot.starter.data.jpa.test)
    testImplementation(libs.spring.boot.starter.flyway.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}

val conformance = rootDir.resolve("../conformance")

tasks.processResources {
    from(conformance) {
        into("conformance")
    }
}

val specification = file(providers.environmentVariable("OPENFSP_SPECIFICATION").getOrElse(rootDir.resolve("../../openfsp.org/content/docs/spec").absolutePath))

tasks.test {
    inputs.dir(conformance).withPropertyName("conformance").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(fileTree(specification) { include("*.mdx", "*.json") }).withPropertyName("specification").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("ci", providers.environmentVariable("CI").orElse(""))
    systemProperty("openfsp.conformance", conformance.absolutePath)
    systemProperty("openfsp.specification", specification.absolutePath)
    providers.environmentVariable("CI").orNull?.let { environment("CI", it) }
}
