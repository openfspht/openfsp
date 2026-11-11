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
    "gateway:domain",
    "gateway:infrastructure",
    "simulator:domain",
    "simulator:infrastructure",
    "validator",
    "validator-web"
)
