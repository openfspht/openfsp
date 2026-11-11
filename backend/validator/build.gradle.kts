plugins {
    id("openfsp.kotlin")
}

dependencies {
    implementation(project(":protocol"))
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.json.schema.validator)
    runtimeOnly(libs.slf4j.nop)
}

val conformance = rootDir.resolve("../conformance")

tasks.test {
    inputs.dir(conformance).withPropertyName("conformance").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("openfsp.conformance", conformance.absolutePath)
}

tasks.jar {
    archiveFileName = "openfsp-validate.jar"
    manifest {
        attributes("Main-Class" to "org.openfsp.validator.MainKt")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.map { classpath -> classpath.map { if (it.isDirectory) it else zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/*/module-info.class", "module-info.class")
}
