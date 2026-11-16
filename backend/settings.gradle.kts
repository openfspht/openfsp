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
    "ddd",
    "protocol",
    "rules",
    "gateway:domain",
    "gateway:infrastructure",
    "simulator:domain",
    "simulator:infrastructure",
    "validator:domain",
    "validator:infrastructure"
)
