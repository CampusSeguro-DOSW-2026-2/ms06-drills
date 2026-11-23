package edu.escuelaing.campusseguro.events.client.audit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.escuelaing.campusseguro.events.client.WireMockTestBase;
import edu.escuelaing.campusseguro.events.exception.AuditRejectedException;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The real gateway sends the MS08 contract through the API Gateway with the service key. */
class RestAuditGatewayTest extends WireMockTestBase {

    private static final String PATH = "/api/v1/audit/events";
    private static final String KEY = "k".repeat(32);

    private RestAuditGateway gateway(String url) {
        return new RestAuditGateway(factory, url, KEY, PATH);
    }

    private AuditRecord record() {
        UUID eventId = UUID.randomUUID();
        return new AuditRecord(UUID.randomUUID().toString(), null, "MS06", "EVENT_STARTED",
                "evento:" + eventId, "EXITO", "2026-12-01T10:00:00Z", "corr-1",
                "SCHEDULED_TO_IN_PROGRESS", eventId.toString(), "SIMULACRO", Map.of("automatico", true));
    }

    @Test
    void postsTheFactWithTheMs08ContractAndTheServiceKey() {
        server.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(202)));

        gateway(baseUrl()).send(record());

        server.verify(postRequestedFor(urlEqualTo(PATH))
                .withHeader("X-Service-Key", equalTo(KEY))
                .withHeader("X-Correlation-ID", equalTo("corr-1"))
                .withRequestBody(containing("\"modulo\":\"MS06\""))
                .withRequestBody(containing("\"accion\":\"EVENT_STARTED\""))
                .withRequestBody(containing("\"resultado\":\"EXITO\""))
                .withRequestBody(containing("\"fecha_hora\":\"2026-12-01T10:00:00Z\""))
                .withRequestBody(containing("\"correlation_id\":\"corr-1\""))
                .withRequestBody(containing("\"evento_id\""))
                .withRequestBody(containing("\"tipo_evento\":\"SIMULACRO\""))
                .withRequestBody(containing("\"recurso_afectado\":\"evento:"))
                .withRequestBody(containing("\"atributos\":{\"automatico\":true}")));
    }

    @Test
    void emptyFieldsAreNotSent() {
        server.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(202)));
        AuditRecord withoutUser = new AuditRecord(UUID.randomUUID().toString(), null, "MS06", "EVENT_TYPE_CREATED",
                null, "EXITO", "2026-12-01T10:00:00Z", "corr-2", null, null, null, null);

        gateway(baseUrl()).send(withoutUser);

        server.verify(postRequestedFor(urlEqualTo(PATH))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.notMatching(".*usuario_id.*"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.notMatching(".*evento_id.*")));
    }

    @Test
    void aBadRequestAnswerIsARejectionThatMustNotBeRetried() {
        server.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(400)));

        assertThrows(AuditRejectedException.class, () -> gateway(baseUrl()).send(record()));
    }

    @Test
    void anAuthenticationOrServerErrorIsAnOutageToRetryLater() {
        server.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(403)));
        assertThrows(ExternalServiceUnavailableException.class, () -> gateway(baseUrl()).send(record()));

        server.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(503)));
        assertThrows(ExternalServiceUnavailableException.class, () -> gateway(baseUrl()).send(record()));
    }

    @Test
    void aServerThatIsNotThereBecomesAnUnavailableException() {
        String url = baseUrl();
        server.stop();

        assertThrows(ExternalServiceUnavailableException.class, () -> gateway(url).send(record()));
    }

    @Test
    void theServiceKeyIsRequired() {
        assertThrows(IllegalStateException.class, () -> new RestAuditGateway(factory, baseUrl(), " ", PATH));
        assertDoesNotThrow(() -> gateway(baseUrl()));
    }
}
