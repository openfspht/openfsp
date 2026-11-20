plugins {
    id("openfsp.kotlin")
}

dependencies {
    api(project(":shared:ddd"))
    api(project(":shared:protocol"))

    testImplementation(project(":simulator:memory"))
}
