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
