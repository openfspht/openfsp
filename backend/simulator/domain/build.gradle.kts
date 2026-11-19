plugins {
    id("openfsp.kotlin")
}

dependencies {
    api(project(":ddd"))
    api(project(":protocol"))

    testImplementation(project(":simulator:memory"))
}
