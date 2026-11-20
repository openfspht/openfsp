plugins {
    id("openfsp.kotlin")
}

description = "Server-side helpers shared by the gateway, the simulator and the validator"

dependencies {
    api(project(":shared:protocol"))
    implementation(libs.jackson.databind)
}
