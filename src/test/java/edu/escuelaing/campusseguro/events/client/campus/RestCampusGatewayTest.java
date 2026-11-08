package edu.escuelaing.campusseguro.events.client.campus;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
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

    private static final String VALIDATE = "/api/v1/catalogo/validar-alcance";
    private static final String ROUTES = "/api/v1/catalogo/rutas";

    private RestCampusGateway gateway() {
        return new RestCampusGateway(factory, baseUrl(), VALIDATE, ROUTES);
    }

    @Test
    void acceptsValidIds() {
        server.stubFor(post(urlEqualTo(VALIDATE))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"invalidIds\":[]}")));

        assertDoesNotThrow(() -> gateway().validateScope(ScopeType.BUILDING, List.of("b1")));

        server.verify(postRequestedFor(urlEqualTo(VALIDATE))
                .withRequestBody(containing("\"tipo\":\"BUILDING\""))
                .withRequestBody(containing("b1")));
    }

    @Test
    void skipsTheCallForEmptyIds() {
        assertDoesNotThrow(() -> gateway().validateScope(ScopeType.ZONE, List.of()));
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @Test
    void rejectsInvalidIds() {
        server.stubFor(post(urlEqualTo(VALIDATE))
                .willReturn(aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"invalidIds\":[\"z9\"]}")));

        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> gateway().validateScope(ScopeType.ZONE, List.of("z1", "z9")));

        assertThat(ex.field()).isEqualTo("scope");
        assertThat(ex.invalidIds()).containsExactly("z9");
    }

    @Test
    void validationFailureMeansUnavailable() {
        server.stubFor(post(urlEqualTo(VALIDATE)).willReturn(aResponse().withStatus(503)));

        assertThrows(ExternalServiceUnavailableException.class,
                () -> gateway().validateScope(ScopeType.ZONE, List.of("z1")));
    }

    @Test
    void readsTheRoutes() {
        server.stubFor(post(urlEqualTo(ROUTES))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"tipo\":\"ZONE\",\"id\":\"z1\",\"nombre\":\"Zone 1\",\"url\":\"http://maps/z1\"}]")));

        List<ScopeRoute> routes = gateway().routes(List.of(new ScopeRef(ScopeType.ZONE, "z1")));

        assertThat(routes).containsExactly(new ScopeRoute(ScopeType.ZONE, "z1", "Zone 1", "http://maps/z1"));
    }

    @Test
    void noScopeMeansNoRoutesAndNoCall() {
        assertThat(gateway().routes(List.of())).isEmpty();
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @Test
    void routesFailureMeansUnavailable() {
        server.stubFor(post(urlEqualTo(ROUTES)).willReturn(aResponse().withStatus(500)));

        assertThrows(ExternalServiceUnavailableException.class,
                () -> gateway().routes(List.of(new ScopeRef(ScopeType.ZONE, "z1"))));
    }
}
