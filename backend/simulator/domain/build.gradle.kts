plugins {
    id("openfsp.kotlin")
}

dependencies {
    api(project(":shared:ddd"))
    api(project(":shared:server"))

    testImplementation(project(":simulator:memory"))
}
