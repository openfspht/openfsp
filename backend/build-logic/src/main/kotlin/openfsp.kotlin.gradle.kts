plugins {
    id("org.jetbrains.kotlin.jvm")
}

val libs = versionCatalogs.named("libs")

kotlin {
    jvmToolchain(25)
    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    implementation(platform(libs.findLibrary("spring-boot-dependencies").get()))
    implementation(platform(libs.findLibrary("kotlin-bom").get()))
    testImplementation(libs.findLibrary("kotlin-test-junit5").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

dependencyLocking {
    lockAllConfigurations()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
