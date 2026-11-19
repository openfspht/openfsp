plugins {
    id("openfsp.jpa")
}

dependencies {
    implementation(project(":gateway:domain"))
    implementation(project(":rules"))
    implementation(project(":sdk"))
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.restclient)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.sentry.spring.boot.starter)
    implementation(libs.sentry.logback)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.spring.security.crypto)
    implementation(libs.bouncycastle.provider)
    implementation(project(":http"))
    runtimeOnly(libs.postgresql)

    testImplementation(testFixtures(project(":gateway:domain")))
    testImplementation(libs.spring.boot.starter.actuator.test)
    testImplementation(libs.spring.boot.starter.data.jpa.test)
    testImplementation(libs.spring.boot.starter.flyway.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}

val simulatorJar = rootProject.layout.projectDirectory.file("simulator/infrastructure/build/libs/app.jar")

tasks.named<Test>("test") {
    dependsOn(":simulator:infrastructure:bootJar")
    inputs.file(simulatorJar).withPropertyName("simulatorJar").withPathSensitivity(PathSensitivity.NONE)
    systemProperty("openfsp.simulator-jar", simulatorJar.asFile.relativeTo(projectDir).path)
}


tasks.named<JavaExec>("bootRun") {
    environment("SPRING_PROFILES_ACTIVE", providers.environmentVariable("SPRING_PROFILES_ACTIVE").getOrElse("development"))
}
