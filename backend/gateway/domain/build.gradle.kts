plugins {
    id("openfsp.kotlin")
    `java-test-fixtures`
}

dependencies {
    api(project(":ddd"))
    api(project(":protocol"))
}
