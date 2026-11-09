package edu.escuelaing.campusseguro.events.client;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Builds the HTTP clients used to call other microservices: same timeouts for all and,
 * if configured, a service token sent as a Bearer token.
 */
@Component
public class RestClientFactory {

    private final RestClient.Builder builder;
    private final RestClient.Builder gatewayBuilder;

    public RestClientFactory(
            RestClient.Builder builder,
            @Value("${app.clients.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${app.clients.read-timeout-ms:5000}") long readTimeoutMs,
            @Value("${app.clients.service-token:}") String serviceToken) {
        // The JDK HttpClient is used because HttpURLConnection does not support PATCH.
        // HTTP/1.1 is forced: the default HTTP/2 upgrade fails against some servers when a body is sent.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.builder = builder.clone().requestFactory(requestFactory);
        this.gatewayBuilder = this.builder.clone();
        if (serviceToken != null && !serviceToken.isBlank()) {
            this.builder.defaultHeader("Authorization", "Bearer " + serviceToken);
        }
    }

    /** Creates a client whose paths are relative to {@code baseUrl}. */
    public RestClient create(String baseUrl) {
        return builder.clone().baseUrl(baseUrl).build();
    }

    /**
     * Creates a client for the API Gateway. It never sends {@code Authorization}: the Gateway
     * accepts either a JWT or a service key, never both.
     */
    public RestClient createGateway(String baseUrl) {
        return gatewayBuilder.clone().baseUrl(baseUrl).build();
    }
}