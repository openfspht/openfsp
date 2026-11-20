import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.vanniktech.maven.publish")
}

val libs = versionCatalogs.named("libs")
val javaRelease = 17

group = "org.openfsp"
version = providers.gradleProperty("sdkVersion").getOrElse("0.0.0-SNAPSHOT")

kotlin {
    jvmToolchain(25)
    compilerOptions {
        allWarningsAsErrors = true
        jvmTarget = JvmTarget.fromTarget(javaRelease.toString())
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xjdk-release=$javaRelease")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaRelease
}

dependencies {
    testImplementation(platform(libs.findLibrary("spring-boot-dependencies").get()))
    testImplementation(platform(libs.findLibrary("kotlin-bom").get()))
    testImplementation(libs.findLibrary("kotlin-test-junit5").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

dependencyLocking {
    lockAllConfigurations()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

mavenPublishing {
    configure(KotlinJvm(javadocJar = JavadocJar.Empty(), sourcesJar = SourcesJar.Sources()))
    publishToMavenCentral(automaticRelease = true)
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
    coordinates(artifactId = "openfsp-${project.name.removePrefix("openfsp-")}")
    pom {
        name = "OpenFSP ${project.name.removePrefix("openfsp-")}"
        description = provider { project.description }
        url = "https://openfsp.org"
        licenses {
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "openfspht"
                name = "OpenFSP"
                url = "https://github.com/openfspht"
            }
        }
        scm {
            url = "https://github.com/openfspht/openfsp"
            connection = "scm:git:https://github.com/openfspht/openfsp.git"
            developerConnection = "scm:git:ssh://git@github.com/openfspht/openfsp.git"
        }
    }
}
