package edu.escuelaing.campusseguro.events.client.campus;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.client.WireMockTestBase;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.exception.InvalidReferenceException;
import java.util.List;
import org.junit.jupiter.api.Test;

class RestCampusGatewayTest extends WireMockTestBase {

    private static final String BUILDING = "/api/v1/buildings/{id}";
    private static final String ZONE = "/api/v1/safe-zones/{id}";
    private static final String ROUTES = "/api/v1/buildings/{id}/evacuation-routes";
    private static final String KEY = "a-service-key-with-at-least-32-characters";
    private static final String SYSTEM_USER = "b256f8d8-e1ab-451b-ac4b-06cf67defb25";

    private RestCampusGateway gateway() {
        return gateway(KEY, SYSTEM_USER);
    }

    private RestCampusGateway gateway(String key, String systemUser) {
        return new RestCampusGateway(factory, baseUrl(), key, systemUser, "ORGANIZADOR", BUILDING, ZONE, ROUTES);
    }

    @Test
    void failsToStartWithoutAServiceKey() {
        assertThrows(IllegalStateException.class, () -> gateway(" ", SYSTEM_USER));
    }

    @Test
    void acceptsExistingBuildingsAndSendsTheHeaders() {
        server.stubFor(get(urlEqualTo("/api/v1/buildings/b1")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"id\":\"b1\"}")));

        assertDoesNotThrow(() -> gateway().validateScope(ScopeType.BUILDING, List.of("b1")));

        server.verify(getRequestedFor(urlEqualTo("/api/v1/buildings/b1"))
                .withHeader("X-Service-Key", equalTo(KEY))
                .withHeader("X-Service-Name", equalTo("ms06"))
                .withHeader("X-User-Id", equalTo(SYSTEM_USER))
                .withHeader("X-User-Role", equalTo("ORGANIZADOR")));
    }

    @Test
    void usesTheSafeZoneRouteForZones() {
        server.stubFor(get(urlEqualTo("/api/v1/safe-zones/z1")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"id\":\"z1\"}")));

        assertDoesNotThrow(() -> gateway().validateScope(ScopeType.ZONE, List.of("z1")));

        server.verify(getRequestedFor(urlEqualTo("/api/v1/safe-zones/z1")));
    }

    @Test
    void skipsTheCallForEmptyIds() {
        assertDoesNotThrow(() -> gateway().validateScope(ScopeType.ZONE, List.of()));
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @Test
    void notFoundAndBadIdsAreInvalidReferences() {
        server.stubFor(get(urlEqualTo("/api/v1/safe-zones/z1")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{}")));
        server.stubFor(get(urlEqualTo("/api/v1/safe-zones/z9")).willReturn(aResponse().withStatus(404)));
        server.stubFor(get(urlEqualTo("/api/v1/safe-zones/bad")).willReturn(aResponse().withStatus(400)));

        InvalidReferenceException ex = assertThrows(InvalidReferenceException.class,
                () -> gateway().validateScope(ScopeType.ZONE, List.of("z1", "z9", "bad")));

        assertThat(ex.field()).isEqualTo("scope");
        assertThat(ex.invalidIds()).containsExactly("z9", "bad");
    }

    @Test
    void otherFailuresMeanUnavailable() {
        server.stubFor(get(urlEqualTo("/api/v1/buildings/b1")).willReturn(aResponse().withStatus(503)));

        assertThrows(ExternalServiceUnavailableException.class,
                () -> gateway().validateScope(ScopeType.BUILDING, List.of("b1")));
    }

    @Test
    void forbiddenMeansUnavailableNotInvalid() {
        server.stubFor(get(urlEqualTo("/api/v1/buildings/b1")).willReturn(aResponse().withStatus(403)));

        assertThrows(ExternalServiceUnavailableException.class,
                () -> gateway().validateScope(ScopeType.BUILDING, List.of("b1")));
    }

    @Test
    void withoutASystemUserCampusIsUnavailable() {
        assertThrows(ExternalServiceUnavailableException.class,
                () -> gateway(KEY, "").validateScope(ScopeType.BUILDING, List.of("b1")));
    }

    @Test
    void readsTheBuildingRoutesAndSkipsSpacesWithoutRoute() {
        server.stubFor(get(urlEqualTo("/api/v1/buildings/b1/evacuation-routes")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("[{\"espacioId\":\"s1\",\"espacioNombre\":\"Lab 1\",\"tieneRuta\":true,"
                        + "\"zonaSeguraNombre\":\"Plaza\",\"recorrido\":\"x\",\"otro\":1},"
                        + "{\"espacioId\":\"s2\",\"espacioNombre\":\"Lab 2\",\"tieneRuta\":false}]")));

        List<ScopeRoute> routes = gateway().routes(List.of(
                new ScopeRef(ScopeType.BUILDING, "b1"), new ScopeRef(ScopeType.ZONE, "z1")));

        assertThat(routes).containsExactly(new ScopeRoute(ScopeType.BUILDING, "b1", "Lab 1 -> Plaza", null));
        assertThat(server.getAllServeEvents()).hasSize(1);
    }

    @Test
    void aMissingBuildingHasNoRoutes() {
        server.stubFor(get(urlEqualTo("/api/v1/buildings/b1/evacuation-routes"))
                .willReturn(aResponse().withStatus(422)));

        assertThat(gateway().routes(List.of(new ScopeRef(ScopeType.BUILDING, "b1")))).isEmpty();
    }

    @Test
    void noScopeMeansNoRoutesAndNoCall() {
        assertThat(gateway().routes(List.of())).isEmpty();
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @Test
    void routesFailureMeansUnavailable() {
        server.stubFor(get(urlEqualTo("/api/v1/buildings/b1/evacuation-routes"))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(ExternalServiceUnavailableException.class,
                () -> gateway().routes(List.of(new ScopeRef(ScopeType.BUILDING, "b1"))));
    }
}
