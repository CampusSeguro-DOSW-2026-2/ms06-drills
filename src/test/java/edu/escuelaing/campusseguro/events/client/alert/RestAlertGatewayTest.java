package edu.escuelaing.campusseguro.events.client.alert;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.client.WireMockTestBase;
import edu.escuelaing.campusseguro.events.entity.enums.EventAlertPurpose;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RestAlertGatewayTest extends WireMockTestBase {

    private static final String ALERTS = "/api/v1/alertas";
    private static final String RESCHEDULE = "/api/v1/alertas/{id}/programar";
    private static final String ARCHIVE = "/api/v1/alertas/{id}/archivar";
    private static final String ACTIVATE = "/api/v1/alertas/{id}/activar";
    private static final AlertActor ACTOR = new AlertActor(
            "11111111-1111-4111-8111-111111111111",
            Set.of(Role.ESTUDIANTE));

    private RestAlertGateway gateway() {
        return new RestAlertGateway(
                factory,
                baseUrl(),
                "test-service-key",
                "",
                "ORGANIZADOR",
                ALERTS,
                RESCHEDULE,
                ARCHIVE,
                ACTIVATE);
    }

    private AlertCommand command(boolean drill) {
        return new AlertCommand(
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                EventAlertPurpose.GENERAL,
                drill,
                "Drill",
                "Go to the meeting point",
                List.of(new ScopeRef(ScopeType.BUILDING, "b1"), new ScopeRef(ScopeType.ZONE, "z1")),
                Set.of(Role.ESTUDIANTE),
                Instant.parse("2026-12-01T15:00:00Z"),
                false,
                List.of("OTHER"));
    }

    @Test
    void createsTheAlertAndReturnsItsId() {
        server.stubFor(post(urlEqualTo(ALERTS))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"alert-1\"}")));

        String id = gateway().createAlert(command(true), ACTOR);

        assertThat(id).isEqualTo("alert-1");
        server.verify(postRequestedFor(urlEqualTo(ALERTS))
                .withHeader("X-Service-Key", equalTo("test-service-key"))
                .withHeader("X-User-Id", equalTo(ACTOR.userId()))
                .withHeader("X-User-Role", equalTo("ESTUDIANTE"))
                .withHeader("X-Correlation-ID", containing("-"))
                .withRequestBody(containing("\"types\":[\"OTHER\"]"))
                .withRequestBody(containing("\"purpose\":\"GENERAL\""))
                .withRequestBody(containing("\"origin\":{\"type\":\"SIMULATION\""))
                .withRequestBody(containing("\"simulationId\":\"22222222-2222-4222-8222-222222222222\""))
                .withRequestBody(containing("BUILDING"))
                .withRequestBody(containing("ZONE"))
                .withRequestBody(containing("ESTUDIANTE")));
    }

    @Test
    void realEventsUseTheManualOrigin() {
        server.stubFor(post(urlEqualTo(ALERTS))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"alert-2\"}")));

        gateway().createAlert(command(false), ACTOR);

        server.verify(postRequestedFor(urlEqualTo(ALERTS))
                .withRequestBody(containing("\"origin\":{\"type\":\"MANUAL\",\"simulationId\":null}")));
    }

    @Test
    void answerWithoutIdMeansUnavailable() {
        server.stubFor(post(urlEqualTo(ALERTS))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        assertThrows(ExternalServiceUnavailableException.class,
                () -> gateway().createAlert(command(false), ACTOR));
    }

    @Test
    void createFailureMeansUnavailable() {
        server.stubFor(post(urlEqualTo(ALERTS)).willReturn(aResponse().withStatus(500)));

        assertThrows(ExternalServiceUnavailableException.class,
                () -> gateway().createAlert(command(false), ACTOR));
    }

    @Test
    void reschedulesTheAlert() {
        server.stubFor(patch(urlEqualTo("/api/v1/alertas/alert-1/programar"))
                .willReturn(aResponse().withStatus(200)));

        gateway().reschedule("alert-1", Instant.parse("2026-12-02T10:00:00Z"), ACTOR);

        server.verify(patchRequestedFor(urlEqualTo("/api/v1/alertas/alert-1/programar"))
                .withHeader("X-Service-Key", equalTo("test-service-key"))
                .withHeader("X-User-Id", equalTo(ACTOR.userId()))
                .withRequestBody(containing("2026-12-02T10:00:00Z")));
    }

    @Test
    void archivesTheAlert() {
        server.stubFor(patch(urlEqualTo("/api/v1/alertas/alert-1/archivar"))
                .willReturn(aResponse().withStatus(200)));

        gateway().archive("alert-1", ACTOR);

        server.verify(patchRequestedFor(urlEqualTo("/api/v1/alertas/alert-1/archivar"))
                .withHeader("X-Service-Key", equalTo("test-service-key"))
                .withHeader("X-User-Role", equalTo("ESTUDIANTE")));
    }

    @Test
    void activatesTheAlert() {
        server.stubFor(patch(urlEqualTo("/api/v1/alertas/alert-1/activar"))
                .willReturn(aResponse().withStatus(200)));

        gateway().activate("alert-1", ACTOR);

        server.verify(patchRequestedFor(urlEqualTo("/api/v1/alertas/alert-1/activar"))
                .withHeader("X-Service-Key", equalTo("test-service-key"))
                .withHeader("X-User-Id", equalTo(ACTOR.userId())));
    }

    @Test
    void activateFailureMeansUnavailable() {
        server.stubFor(patch(urlEqualTo("/api/v1/alertas/alert-1/activar"))
                .willReturn(aResponse().withStatus(503)));

        assertThrows(ExternalServiceUnavailableException.class,
                () -> gateway().activate("alert-1", ACTOR));
    }

    @Test
    void patchFailuresMeanUnavailable() {
        server.stubFor(patch(urlEqualTo("/api/v1/alertas/alert-1/archivar"))
                .willReturn(aResponse().withStatus(404)));
        server.stubFor(patch(urlEqualTo("/api/v1/alertas/alert-1/programar"))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(ExternalServiceUnavailableException.class,
                () -> gateway().archive("alert-1", ACTOR));
        assertThrows(ExternalServiceUnavailableException.class,
                () -> gateway().reschedule("alert-1", Instant.now(), ACTOR));
    }
}
