plugins {
    id("openfsp.library")
}

description = "OpenFSP protocol models, problem catalogue and webhook signatures"

dependencies {
    api(libs.jackson.annotations)
    testImplementation(libs.jackson.databind)
}
