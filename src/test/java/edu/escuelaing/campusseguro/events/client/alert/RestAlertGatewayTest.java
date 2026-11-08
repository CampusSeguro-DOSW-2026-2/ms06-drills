package edu.escuelaing.campusseguro.events.client.alert;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
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
    private static final String DRAFT = "/api/v1/alertas/{id}/borrador";
    private static final String ACTIVATE = "/api/v1/alertas/{id}/activar";

    private RestAlertGateway gateway() {
        return new RestAlertGateway(factory, baseUrl(), ALERTS, RESCHEDULE, DRAFT, ACTIVATE);
    }

    private AlertCommand command(boolean drill) {
        return new AlertCommand(
                UUID.randomUUID(),
                EventAlertPurpose.GENERAL,
                drill,
                "Drill",
                "Go to the meeting point",
                List.of(new ScopeRef(ScopeType.BUILDING, "b1"), new ScopeRef(ScopeType.ZONE, "z1")),
                Set.of(Role.ESTUDIANTE),
                Instant.parse("2026-12-01T15:00:00Z"),
                false);
    }

    @Test
    void createsTheAlertAndReturnsItsId() {
        server.stubFor(post(urlEqualTo(ALERTS))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"alert-1\"}")));

        String id = gateway().createAlert(command(true));

        assertThat(id).isEqualTo("alert-1");
        server.verify(postRequestedFor(urlEqualTo(ALERTS))
                .withRequestBody(containing("\"type\":\"SIMULATION\""))
                .withRequestBody(containing("\"purpose\":\"GENERAL\""))
                .withRequestBody(containing("\"origin\""))
                .withRequestBody(containing("BUILDING"))
                .withRequestBody(containing("ZONE"))
                .withRequestBody(containing("ESTUDIANTE")));
    }

    @Test
    void realEventsUseTheOtherType() {
        server.stubFor(post(urlEqualTo(ALERTS))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"alert-2\"}")));

        gateway().createAlert(command(false));

        server.verify(postRequestedFor(urlEqualTo(ALERTS)).withRequestBody(containing("\"type\":\"OTHER\"")));
    }

    @Test
    void answerWithoutIdMeansUnavailable() {
        server.stubFor(post(urlEqualTo(ALERTS))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        assertThrows(ExternalServiceUnavailableException.class, () -> gateway().createAlert(command(false)));
    }

    @Test
    void createFailureMeansUnavailable() {
        server.stubFor(post(urlEqualTo(ALERTS)).willReturn(aResponse().withStatus(500)));

        assertThrows(ExternalServiceUnavailableException.class, () -> gateway().createAlert(command(false)));
    }

    @Test
    void reschedulesTheAlert() {
        server.stubFor(patch(urlEqualTo("/api/v1/alertas/alert-1/programar"))
                .willReturn(aResponse().withStatus(204)));

        gateway().reschedule("alert-1", Instant.parse("2026-12-02T10:00:00Z"));

        server.verify(patchRequestedFor(urlEqualTo("/api/v1/alertas/alert-1/programar"))
                .withRequestBody(containing("2026-12-02T10:00:00Z")));
    }

    @Test
    void returnsTheAlertToDraft() {
        server.stubFor(patch(urlEqualTo("/api/v1/alertas/alert-1/borrador"))
                .willReturn(aResponse().withStatus(204)));

        gateway().backToDraft("alert-1");

        server.verify(patchRequestedFor(urlEqualTo("/api/v1/alertas/alert-1/borrador")));
    }

    @Test
    void activatesTheAlert() {
        server.stubFor(patch(urlEqualTo("/api/v1/alertas/alert-1/activar"))
                .willReturn(aResponse().withStatus(200)));

        gateway().activate("alert-1");

        server.verify(patchRequestedFor(urlEqualTo("/api/v1/alertas/alert-1/activar")));
    }

    @Test
    void activateFailureMeansUnavailable() {
        server.stubFor(patch(urlEqualTo("/api/v1/alertas/alert-1/activar"))
                .willReturn(aResponse().withStatus(503)));

        assertThrows(ExternalServiceUnavailableException.class, () -> gateway().activate("alert-1"));
    }

    @Test
    void patchFailuresMeanUnavailable() {
        server.stubFor(patch(urlEqualTo("/api/v1/alertas/alert-1/borrador"))
                .willReturn(aResponse().withStatus(404)));
        server.stubFor(patch(urlEqualTo("/api/v1/alertas/alert-1/programar"))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(ExternalServiceUnavailableException.class, () -> gateway().backToDraft("alert-1"));
        assertThrows(ExternalServiceUnavailableException.class,
                () -> gateway().reschedule("alert-1", Instant.now()));
    }
}
