package edu.escuelaing.campusseguro.events.client;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class RestClientFactoryTest extends WireMockTestBase {

    @Test
    void sendsTheServiceTokenWhenConfigured() {
        server.stubFor(get(urlEqualTo("/ping")).willReturn(aResponse().withStatus(200)));
        RestClientFactory withToken = new RestClientFactory(RestClient.builder(), 500, 1000, "abc");

        withToken.create(baseUrl()).get().uri("/ping").retrieve().toBodilessEntity();

        server.verify(getRequestedFor(urlEqualTo("/ping")).withHeader("Authorization", equalTo("Bearer abc")));
    }

    @Test
    void sendsNoAuthorizationHeaderWithoutToken() {
        server.stubFor(get(urlEqualTo("/ping")).willReturn(aResponse().withStatus(200)));

        factory.create(baseUrl()).get().uri("/ping").retrieve().toBodilessEntity();

        server.verify(getRequestedFor(urlEqualTo("/ping")).withHeader("Authorization", absent()));
    }
}
