plugins {
    id("openfsp.kotlin")
    `java-test-fixtures`
}

dependencies {
    api(project(":shared:ddd"))
    api(project(":shared:server"))
}
