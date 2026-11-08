package edu.escuelaing.campusseguro.events.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.client.RestClient;

/** Starts a fake HTTP server on a free port before each test. Used by the REST gateway tests. */
public abstract class WireMockTestBase {

    protected WireMockServer server;
    protected RestClientFactory factory;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        factory = new RestClientFactory(RestClient.builder(), 500, 1000, "");
    }

    @AfterEach
    void stopServer() {
        if (server.isRunning()) {
            server.stop();
        }
    }

    /** Base URL of the fake server. */
    protected String baseUrl() {
        return "http://localhost:" + server.port();
    }
}
