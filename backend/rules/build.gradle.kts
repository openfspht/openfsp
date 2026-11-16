plugins {
    id("openfsp.kotlin")
}

description = "Request field rules shared by the gateway and the native simulator"

dependencies {
    api(project(":protocol"))
}
