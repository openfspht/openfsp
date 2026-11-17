plugins {
    id("openfsp.spring-boot")
}

dependencies {
    implementation(project(":protocol"))
    implementation(project(":rules"))
    implementation(project(":simulator:domain"))
    implementation(libs.httpclient5)
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
