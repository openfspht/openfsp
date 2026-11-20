package org.openfsp.simulator

import org.springframework.http.HttpStatusCode
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient

fun localClient(port: Int): RestClient =
    RestClient
        .builder()
        .requestFactory(JdkClientHttpRequestFactory())
        .baseUrl("http://127.0.0.1:$port")
        .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
        .build()
