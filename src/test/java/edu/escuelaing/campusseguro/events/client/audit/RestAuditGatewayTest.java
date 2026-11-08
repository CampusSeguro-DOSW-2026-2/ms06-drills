package edu.escuelaing.campusseguro.events.client.audit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.escuelaing.campusseguro.events.client.WireMockTestBase;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RestAuditGatewayTest extends WireMockTestBase {

    private static final String PATH = "/api/v1/auditoria/registros";

    private AuditRecord record() {
        UUID eventId = UUID.randomUUID();
        return new AuditRecord(UUID.randomUUID(), "CREATION", "coord-1", "EVENT_CREATED",
                Instant.parse("2026-12-01T10:00:00Z"), new AuditRecord.EventRef(eventId, "Drill"),
                "EVENT:" + eventId, "SUCCESS", "Created as DRAFT", "MS06");
    }

    @Test
    void postsTheRecordToMs08() {
        server.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(201)));

        new RestAuditGateway(factory, baseUrl(), PATH).send(record());

        server.verify(postRequestedFor(urlEqualTo(PATH))
                .withRequestBody(containing("\"tipoRegistro\":\"CREATION\""))
                .withRequestBody(containing("\"accion\":\"EVENT_CREATED\""))
                .withRequestBody(containing("\"origen\":\"MS06\"")));
    }

    @Test
    void anErrorAnswerBecomesAnUnavailableException() {
        server.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(503)));

        assertThrows(ExternalServiceUnavailableException.class,
                () -> new RestAuditGateway(factory, baseUrl(), PATH).send(record()));
    }

    @Test
    void aServerThatIsNotThereBecomesAnUnavailableException() {
        String url = baseUrl();
        server.stop();

        assertThrows(ExternalServiceUnavailableException.class,
                () -> new RestAuditGateway(factory, url, PATH).send(record()));
    }
}
