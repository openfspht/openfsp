plugins {
    id("openfsp.kotlin")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.springframework.boot")
}

val libs = versionCatalogs.named("libs")

dependencies {
    developmentOnly(platform(libs.findLibrary("spring-boot-dependencies").get()))
    implementation(libs.findLibrary("kotlin-reflect").get())
    implementation(libs.findLibrary("jackson-module-kotlin").get())
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName = "app.jar"
}

tasks.named<Jar>("jar") {
    enabled = false
}

val uiBundle = providers.gradleProperty("uiBundle")

if (uiBundle.isPresent) {
    tasks.named<ProcessResources>("processResources") {
        from(uiBundle.get()) {
            into("openfsp-ui")
        }
    }
}
