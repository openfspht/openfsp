plugins {
    id("openfsp.jpa")
}

dependencies {
    implementation(project(":validator:domain"))
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.json.schema.validator)
    implementation(libs.httpclient5)
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

tasks.test {
    inputs.dir(conformance).withPropertyName("conformance").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("openfsp.conformance", conformance.absolutePath)
    systemProperty("openfsp.specification", providers.environmentVariable("OPENFSP_SPECIFICATION").getOrElse(rootDir.resolve("../../openfsp.org/content/docs/spec").absolutePath))
}
