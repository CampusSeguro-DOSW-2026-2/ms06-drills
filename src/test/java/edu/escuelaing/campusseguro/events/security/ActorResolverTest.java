package edu.escuelaing.campusseguro.events.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Tests getting the authenticated user and the cut to 64 characters. */
class ActorResolverTest {

    private final ActorResolver resolver = new ActorResolver();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String name) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        name, "n/a", List.of(new SimpleGrantedAuthority("ROLE_COORDINADOR"))));
    }

    @Test
    void returnsTheAuthenticatedName() {
        authenticateAs("user-1");

        assertEquals("user-1", resolver.resolve());
    }

    @Test
    void truncatesLongNamesTo64Characters() {
        authenticateAs("x".repeat(100));

        assertEquals("x".repeat(64), resolver.resolve());
    }

    @Test
    void failsWithoutAuthentication() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, resolver::resolve);
    }

    @Test
    void failsWhenTheAuthenticationIsNotAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated("user-1", "n/a"));

        assertThrows(AuthenticationCredentialsNotFoundException.class, resolver::resolve);
    }
}
