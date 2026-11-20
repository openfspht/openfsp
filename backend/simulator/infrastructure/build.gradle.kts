plugins {
    id("openfsp.spring-boot")
}

dependencies {
    implementation(project(":shared:protocol"))
    implementation(project(":simulator:domain"))
    implementation(project(":simulator:memory"))
    implementation(project(":shared:http"))
    implementation(project(":shared:ui"))
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.restclient)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.sentry.spring.boot.starter)
    implementation(libs.sentry.logback)

    testImplementation(libs.spring.boot.starter.actuator.test)
    testImplementation(libs.spring.boot.starter.validation.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
}
