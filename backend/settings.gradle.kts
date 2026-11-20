pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "openfsp"

include(
    "shared:ddd",
    "shared:protocol",
    "shared:server",
    "shared:http",
    "shared:ui",
    "gateway:domain",
    "gateway:infrastructure",
    "simulator:domain",
    "simulator:memory",
    "simulator:infrastructure",
    "validator:domain",
    "validator:infrastructure",
    "sdk",
    "sdk-testing"
)

project(":shared:protocol").projectDir = file("../sdks/kotlin/protocol")
project(":sdk").projectDir = file("../sdks/kotlin")
project(":sdk-testing").projectDir = file("../sdks/kotlin/testing")
