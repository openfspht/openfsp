pluginManagement {
    includeBuild("../../backend/build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../../backend/gradle/libs.versions.toml"))
        }
    }
}

includeBuild("../../backend") {
    dependencySubstitution {
        substitute(module("org.openfsp:openfsp-protocol")).using(project(":protocol"))
    }
}

rootProject.name = "openfsp-sdk"

include(":sdk-testing")
project(":sdk-testing").projectDir = file("testing")
