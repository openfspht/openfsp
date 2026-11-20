plugins {
    id("openfsp.kotlin")
}

description = "Apache HTTP client whose every connection goes through a caller-supplied address resolver"

dependencies {
    api(libs.httpclient5)
}
