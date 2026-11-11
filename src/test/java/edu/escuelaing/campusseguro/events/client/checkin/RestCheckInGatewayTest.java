package edu.escuelaing.campusseguro.events.client.checkin;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
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
    private static final String CONSOLIDATED = "/api/v1/chi/eventos/{eventId}/consolidado";
    private static final String CHECKINS = "/api/v1/chi/eventos/{eventId}/checkins";
    private static final String KEY = "a-service-key-with-at-least-32-characters";
    private static final String SYSTEM_USER = "b256f8d8-e1ab-451b-ac4b-06cf67defb25";

    private final UUID eventId = UUID.randomUUID();

    private RestCheckInGateway gateway() {
        return gateway(KEY, SYSTEM_USER);
    }

    private RestCheckInGateway gateway(String key, String systemUser) {
        return new RestCheckInGateway(
                factory, baseUrl(), key, systemUser, "ORGANIZADOR", STATE, CONSOLIDATED, CHECKINS);
    }

    private EventStatePayload payload(EventStatus status) {
        return new EventStatePayload(
                eventId, "Drill", true, status,
                Instant.parse("2026-12-01T15:00:00Z"), null,
                List.of(new ScopeRef(ScopeType.BUILDING, "b1"), new ScopeRef(ScopeType.ZONE, "z1")),
                Set.of(Role.ESTUDIANTE));
    }

    @Test
    void failsToStartWithoutAServiceKey() {
        assertThrows(IllegalStateException.class, () -> gateway(" ", SYSTEM_USER));
    }

    @Test
    void pushesTheStateWithSpanishNamesAndTheServiceHeaders() {
        String path = "/api/v1/chi/eventos/" + eventId + "/estado";
        server.stubFor(put(urlEqualTo(path)).willReturn(aResponse().withStatus(204)));

        gateway().pushState(payload(EventStatus.SCHEDULED));

        server.verify(putRequestedFor(urlEqualTo(path))
                .withHeader("X-Service-Key", equalTo(KEY))
                .withHeader("X-Service-Name", equalTo("ms06"))
                .withHeader("Authorization", absent())
                .withRequestBody(containing("\"estado\":\"PROGRAMADO\""))
                .withRequestBody(containing("\"simulacro\":true"))
                .withRequestBody(containing("\"fechaInicio\":\"2026-12-01T15:00:00Z\""))
                .withRequestBody(containing("\"edificios\":[\"b1\"]"))
                .withRequestBody(containing("\"zonas\":[\"z1\"]"))
                .withRequestBody(containing("\"rolesConvocados\":[\"ESTUDIANTE\"]")));
    }

    @Test
    void aDraftIsSentWithoutStartDate() {
        String path = "/api/v1/chi/eventos/" + eventId + "/estado";
        server.stubFor(put(urlEqualTo(path)).willReturn(aResponse().withStatus(204)));

        gateway().pushState(payload(EventStatus.DRAFT));

        server.verify(putRequestedFor(urlEqualTo(path))
                .withRequestBody(containing("\"estado\":\"BORRADOR\""))
                .withRequestBody(notContaining("\"fechaInicio\":\"2026")));
    }

    @Test
    void pushFailureMeansUnavailable() {
        server.stubFor(put(urlEqualTo("/api/v1/chi/eventos/" + eventId + "/estado"))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(ExternalServiceUnavailableException.class, () -> gateway().pushState(payload(EventStatus.SCHEDULED)));
    }

    @Test
    void readsTheIndicatorsFromTheConsolidatedAnswer() {
        String path = "/api/v1/chi/eventos/" + eventId + "/consolidado";
        server.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"evento_id\":\"" + eventId + "\",\"estado_evento\":\"EN_EJECUCION\","
                                + "\"total_reportados\":12,"
                                + "\"totales_por_estado\":{\"SAFE\":10,\"EVACUATING\":1,\"NEEDS_ASSISTANCE\":1,"
                                + "\"OFF_CAMPUS\":0},"
                                + "\"por_punto_encuentro\":[],\"calculado_en\":\"2026-12-01T15:05:00Z\"}")));

        CheckInIndicators result = gateway().indicators(eventId);

        assertThat(result.eventId()).isEqualTo(eventId);
        assertThat(result.updatedAt()).isEqualTo(Instant.parse("2026-12-01T15:05:00Z"));
        assertThat(result.totalCheckins()).isEqualTo(12);
        assertThat(result.byStatus())
                .containsEntry("SAFE", 10)
                .containsEntry("EVACUATING", 1)
                .containsEntry("NEEDS_ASSISTANCE", 1)
                .containsEntry("OFF_CAMPUS", 0);
        assertThat(result.participationPct()).isNull();
        server.verify(getRequestedFor(urlEqualTo(path))
                .withHeader("X-Service-Key", equalTo(KEY))
                .withHeader("X-User-Id", equalTo(SYSTEM_USER))
                .withHeader("X-User-Role", equalTo("ORGANIZADOR")));
    }

    @Test
    void indicatorsNeedAValidSystemUser() {
        assertThrows(ExternalServiceUnavailableException.class, () -> gateway(KEY, "").indicators(eventId));
        assertThrows(ExternalServiceUnavailableException.class, () -> gateway(KEY, "not-a-uuid").indicators(eventId));
    }

    @Test
    void indicatorsFailureMeansUnavailable() {
        server.stubFor(get(urlEqualTo("/api/v1/chi/eventos/" + eventId + "/consolidado"))
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
                                + "\"nombre\":\"Ana\",\"estado\":\"SAFE\",\"zonaId\":\"z1\","
                                + "\"registradoEn\":\"2026-12-01T15:06:00Z\"}]}")));

        CheckInPage page = gateway().checkins(eventId, "SAFE", 1, 5);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.total()).isEqualTo(7);
        assertThat(page.items()).containsExactly(
                new CheckInItem("u1", "Ana", "SAFE", "z1", Instant.parse("2026-12-01T15:06:00Z")));
        server.verify(getRequestedFor(urlPathEqualTo(path))
                .withHeader("X-Service-Key", equalTo(KEY))
                .withQueryParam("estado", equalTo("SAFE"))
                .withQueryParam("page", equalTo("1"))
                .withQueryParam("size", equalTo("5")));
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
        server.verify(getRequestedFor(urlPathEqualTo(path)).withQueryParam("estado", absent()));
    }

    @Test
    void checkinsFailureMeansUnavailable() {
        server.stubFor(get(urlPathEqualTo("/api/v1/chi/eventos/" + eventId + "/checkins"))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(ExternalServiceUnavailableException.class, () -> gateway().checkins(eventId, null, 0, 10));
    }
}
