package edu.escuelaing.campusseguro.events.client.checkin;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.escuelaing.campusseguro.events.client.EventStatePayload;
import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.client.WireMockTestBase;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RestCheckInGatewayTest extends WireMockTestBase {

    private static final String STATE = "/api/v1/chi/eventos/{eventId}/estado";
    private static final String INDICATORS = "/api/v1/chi/eventos/{eventId}/indicadores";
    private static final String CHECKINS = "/api/v1/chi/eventos/{eventId}/checkins";

    private final UUID eventId = UUID.randomUUID();

    private RestCheckInGateway gateway() {
        return new RestCheckInGateway(factory, baseUrl(), STATE, INDICATORS, CHECKINS);
    }

    private EventStatePayload payload() {
        return new EventStatePayload(
                eventId, "Drill", true, EventStatus.SCHEDULED,
                Instant.parse("2026-12-01T15:00:00Z"), null,
                List.of(new ScopeRef(ScopeType.BUILDING, "b1"), new ScopeRef(ScopeType.ZONE, "z1")),
                Set.of(Role.ESTUDIANTE));
    }

    @Test
    void pushesTheStateWithSpanishNames() {
        String path = "/api/v1/chi/eventos/" + eventId + "/estado";
        server.stubFor(put(urlEqualTo(path)).willReturn(aResponse().withStatus(204)));

        gateway().pushState(payload());

        server.verify(putRequestedFor(urlEqualTo(path))
                .withRequestBody(containing("\"estado\":\"PROGRAMADO\""))
                .withRequestBody(containing("\"simulacro\":true"))
                .withRequestBody(containing("\"escenario\""))
                .withRequestBody(containing("\"edificios\":[\"b1\"]"))
                .withRequestBody(containing("\"zonas\":[\"z1\"]"))
                .withRequestBody(containing("\"rolesConvocados\":[\"ESTUDIANTE\"]")));
    }

    @Test
    void pushFailureMeansUnavailable() {
        server.stubFor(put(urlEqualTo("/api/v1/chi/eventos/" + eventId + "/estado"))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(ExternalServiceUnavailableException.class, () -> gateway().pushState(payload()));
    }

    @Test
    void readsTheIndicators() {
        server.stubFor(get(urlEqualTo("/api/v1/chi/eventos/" + eventId + "/indicadores"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"eventoId\":\"" + eventId + "\",\"actualizadoEn\":\"2026-12-01T15:05:00Z\","
                                + "\"totalCheckins\":12,\"porEstado\":{\"SEGURO\":10,\"AYUDA\":2},"
                                + "\"porcentajeParticipacion\":80.5}")));

        CheckInIndicators result = gateway().indicators(eventId);

        assertThat(result.eventId()).isEqualTo(eventId);
        assertThat(result.updatedAt()).isEqualTo(Instant.parse("2026-12-01T15:05:00Z"));
        assertThat(result.totalCheckins()).isEqualTo(12);
        assertThat(result.byStatus()).containsEntry("SEGURO", 10).containsEntry("AYUDA", 2);
        assertThat(result.participationPct()).isEqualByComparingTo("80.5");
    }

    @Test
    void indicatorsFailureMeansUnavailable() {
        server.stubFor(get(urlEqualTo("/api/v1/chi/eventos/" + eventId + "/indicadores"))
                .willReturn(aResponse().withStatus(503)));

        assertThrows(ExternalServiceUnavailableException.class, () -> gateway().indicators(eventId));
    }

    @Test
    void readsOnePageOfCheckinsWithFilter() {
        String path = "/api/v1/chi/eventos/" + eventId + "/checkins";
        server.stubFor(get(urlPathEqualTo(path))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"page\":1,\"size\":5,\"total\":7,\"items\":[{\"usuarioId\":\"u1\","
                                + "\"nombre\":\"Ana\",\"estado\":\"SEGURO\",\"zonaId\":\"z1\","
                                + "\"registradoEn\":\"2026-12-01T15:06:00Z\"}]}")));

        CheckInPage page = gateway().checkins(eventId, "SEGURO", 1, 5);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.total()).isEqualTo(7);
        assertThat(page.items()).containsExactly(
                new CheckInItem("u1", "Ana", "SEGURO", "z1", Instant.parse("2026-12-01T15:06:00Z")));
        server.verify(getRequestedFor(urlPathEqualTo(path))
                .withQueryParam("estado", com.github.tomakehurst.wiremock.client.WireMock.equalTo("SEGURO"))
                .withQueryParam("page", com.github.tomakehurst.wiremock.client.WireMock.equalTo("1"))
                .withQueryParam("size", com.github.tomakehurst.wiremock.client.WireMock.equalTo("5")));
    }

    @Test
    void withoutStatusTheFilterIsNotSent() {
        String path = "/api/v1/chi/eventos/" + eventId + "/checkins";
        server.stubFor(get(urlPathEqualTo(path))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"page\":0,\"size\":10,\"total\":0,\"items\":[]}")));

        CheckInPage page = gateway().checkins(eventId, null, 0, 10);

        assertThat(page.items()).isEmpty();
        server.verify(getRequestedFor(urlPathEqualTo(path))
                .withQueryParam("estado", com.github.tomakehurst.wiremock.client.WireMock.absent()));
    }

    @Test
    void checkinsFailureMeansUnavailable() {
        server.stubFor(get(urlPathEqualTo("/api/v1/chi/eventos/" + eventId + "/checkins"))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(ExternalServiceUnavailableException.class, () -> gateway().checkins(eventId, null, 0, 10));
    }
}
