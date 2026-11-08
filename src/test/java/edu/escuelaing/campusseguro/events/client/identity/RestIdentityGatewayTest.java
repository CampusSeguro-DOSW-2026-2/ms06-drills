package edu.escuelaing.campusseguro.events.client.identity;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.escuelaing.campusseguro.events.client.WireMockTestBase;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.exception.InvalidReferenceException;
import edu.escuelaing.campusseguro.events.security.Role;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RestIdentityGatewayTest extends WireMockTestBase {

    private static final String PATH = "/api/v1/catalogo/validar-destinatarios";

    private RestIdentityGateway gateway() {
        return new RestIdentityGateway(factory, baseUrl(), PATH);
    }

    @Test
    void acceptsRolesWhenNothingIsInvalid() {
        server.stubFor(post(urlEqualTo(PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"invalidIds\":[]}")));

        assertDoesNotThrow(() -> gateway().validateRoles(Set.of(Role.ESTUDIANTE)));

        server.verify(postRequestedFor(urlEqualTo(PATH))
                .withRequestBody(containing("\"tipo\":\"ROLE\""))
                .withRequestBody(containing("ESTUDIANTE")));
    }

    @Test
    void doesNotCallWhenThereAreNoRoles() {
        assertDoesNotThrow(() -> gateway().validateRoles(Set.of()));
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @Test
    void rejectsRolesTheServiceMarksAsInvalid() {
        server.stubFor(post(urlEqualTo(PATH))
                .willReturn(aResponse().withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"invalidIds\":[\"AUDITOR\"]}")));

        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class, () -> gateway().validateRoles(Set.of(Role.AUDITOR)));

        assertThat(ex.field()).isEqualTo("participantRoles");
        assertThat(ex.invalidIds()).containsExactly("AUDITOR");
    }

    @Test
    void serverErrorMeansUnavailable() {
        server.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(500)));

        assertThrows(ExternalServiceUnavailableException.class,
                () -> gateway().validateRoles(Set.of(Role.ESTUDIANTE)));
    }

    @Test
    void badRequestWithoutInvalidIdsMeansUnavailable() {
        server.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(400)));

        assertThrows(ExternalServiceUnavailableException.class,
                () -> gateway().validateRoles(Set.of(Role.ESTUDIANTE)));
    }

    @Test
    void connectionRefusedMeansUnavailable() {
        RestIdentityGateway gateway = gateway();
        server.stop();

        assertThrows(ExternalServiceUnavailableException.class,
                () -> gateway.validateRoles(Set.of(Role.ESTUDIANTE)));
    }
}
