plugins {
    id("openfsp.kotlin")
}

dependencies {
    api(project(":ddd"))
    api(project(":protocol"))
    api(libs.jackson.module.kotlin)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.json.schema.validator)
}

val conformance = rootDir.resolve("../conformance")

tasks.test {
    inputs.dir(conformance).withPropertyName("conformance").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("openfsp.conformance", conformance.absolutePath)
}
