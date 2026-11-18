plugins {
    id("openfsp.library")
}

description = "Test doubles for the OpenFSP SDK: a fake transport, wire fixtures and a webhook signer"

dependencies {
    api(project(":sdk"))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
}
