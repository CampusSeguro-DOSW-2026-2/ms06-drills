package edu.escuelaing.campusseguro.events.client.brigade;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
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

class RestBrigadeGatewayTest extends WireMockTestBase {

    private static final String STATE = "/api/v1/bei/eventos/{eventId}/estado";
    private static final String BRIGADIERS = "/api/v1/bei/eventos/{eventId}/brigadistas";
    private static final String INDICATORS = "/api/v1/bei/eventos/{eventId}/indicadores";

    private final UUID eventId = UUID.randomUUID();

    private RestBrigadeGateway gateway() {
        return new RestBrigadeGateway(factory, baseUrl(), STATE, BRIGADIERS, INDICATORS);
    }

    private EventStatePayload payload(boolean drill) {
        return new EventStatePayload(
                eventId, "Event", drill, EventStatus.IN_PROGRESS,
                Instant.parse("2026-12-01T15:00:00Z"), null,
                List.of(new ScopeRef(ScopeType.ZONE, "z1")), Set.of(Role.BRIGADISTA));
    }

    @Test
    void pushesTheStateWithSpanishNames() {
        String path = "/api/v1/bei/eventos/" + eventId + "/estado";
        server.stubFor(put(urlEqualTo(path)).willReturn(aResponse().withStatus(204)));

        gateway().pushState(payload(true));

        server.verify(putRequestedFor(urlEqualTo(path))
                .withRequestBody(containing("\"estado\":\"EN_EJECUCION\""))
                .withRequestBody(containing("\"tipoEvento\":\"SIMULACRO\""))
                .withRequestBody(containing("\"zonaId\":\"z1\"")));
    }

    @Test
    void realEventsAreSentAsReal() {
        String path = "/api/v1/bei/eventos/" + eventId + "/estado";
        server.stubFor(put(urlEqualTo(path)).willReturn(aResponse().withStatus(204)));

        gateway().pushState(payload(false));

        server.verify(putRequestedFor(urlEqualTo(path)).withRequestBody(containing("\"tipoEvento\":\"REAL\"")));
    }

    @Test
    void pushFailureMeansUnavailable() {
        server.stubFor(put(urlEqualTo("/api/v1/bei/eventos/" + eventId + "/estado"))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(ExternalServiceUnavailableException.class, () -> gateway().pushState(payload(false)));
    }

    @Test
    void readsTheBrigadiers() {
        server.stubFor(get(urlEqualTo("/api/v1/bei/eventos/" + eventId + "/brigadistas"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"eventoId\":\"" + eventId + "\",\"actualizadoEn\":\"2026-12-01T15:05:00Z\","
                                + "\"brigadistas\":[{\"brigadistaId\":\"b1\",\"nombre\":\"Ana\","
                                + "\"zonaId\":\"z1\",\"estado\":\"ACTIVO\"},"
                                + "{\"brigadistaId\":\"b2\",\"nombre\":\"Luis\",\"zonaId\":\"z2\","
                                + "\"estado\":\"DESACTIVADO\"}]}")));

        BrigadierSnapshot snapshot = gateway().brigadiers(eventId);

        assertThat(snapshot.updatedAt()).isEqualTo(Instant.parse("2026-12-01T15:05:00Z"));
        assertThat(snapshot.brigadiers()).containsExactly(
                new BrigadierInfo("b1", "Ana", "z1", true),
                new BrigadierInfo("b2", "Luis", "z2", false));
    }

    @Test
    void brigadiersFailureMeansUnavailable() {
        server.stubFor(get(urlEqualTo("/api/v1/bei/eventos/" + eventId + "/brigadistas"))
                .willReturn(aResponse().withStatus(502)));

        assertThrows(ExternalServiceUnavailableException.class, () -> gateway().brigadiers(eventId));
    }

    @Test
    void readsTheIndicators() {
        server.stubFor(get(urlEqualTo("/api/v1/bei/eventos/" + eventId + "/indicadores"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"eventoId\":\"" + eventId + "\",\"actualizadoEn\":\"2026-12-01T15:05:00Z\","
                                + "\"totalIncidentes\":5,\"totalCasosAtendidos\":4,\"totalCasosResueltos\":3,"
                                + "\"tiempoPromedioAtencionSegundos\":120,"
                                + "\"porBrigadista\":[{\"brigadistaId\":\"b1\",\"casosAtendidos\":4,"
                                + "\"casosResueltos\":3}]}")));

        IncidentIndicators result = gateway().indicators(eventId);

        assertThat(result.totalIncidents()).isEqualTo(5);
        assertThat(result.casesAttended()).isEqualTo(4);
        assertThat(result.casesResolved()).isEqualTo(3);
        assertThat(result.avgAttentionSeconds()).isEqualTo(120L);
        assertThat(result.byBrigadier()).containsExactly(new BrigadierPerformance("b1", 4, 3));
    }

    @Test
    void averageTimeIsOptional() {
        server.stubFor(get(urlEqualTo("/api/v1/bei/eventos/" + eventId + "/indicadores"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"actualizadoEn\":\"2026-12-01T15:05:00Z\",\"totalIncidentes\":0,"
                                + "\"totalCasosAtendidos\":0,\"totalCasosResueltos\":0}")));

        IncidentIndicators result = gateway().indicators(eventId);

        assertThat(result.avgAttentionSeconds()).isNull();
        assertThat(result.byBrigadier()).isEmpty();
    }

    @Test
    void indicatorsFailureMeansUnavailable() {
        server.stubFor(get(urlEqualTo("/api/v1/bei/eventos/" + eventId + "/indicadores"))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(ExternalServiceUnavailableException.class, () -> gateway().indicators(eventId));
    }
}
