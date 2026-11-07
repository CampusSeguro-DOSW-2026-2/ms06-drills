package edu.escuelaing.campusseguro.events.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AccessActorTest {

    @Test
    void anonymousWhenThereIsNoAuthentication() {
        AccessActor actor = AccessActor.from(null);

        assertEquals("", actor.userId());
        assertTrue(actor.roles().isEmpty());
    }

    @Test
    void takesTheUserAndTheRecognizedRoles() {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                "user-1",
                "n/a",
                List.of(
                        new SimpleGrantedAuthority("ROLE_BRIGADISTA"),
                        new SimpleGrantedAuthority("ROLE_DESCONOCIDO")));

        AccessActor actor = AccessActor.from(authentication);

        assertEquals("user-1", actor.userId());
        assertEquals(Set.of(Role.BRIGADISTA), actor.roles());
        assertTrue(actor.hasRole(Role.BRIGADISTA));
        assertFalse(actor.hasRole(Role.COORDINADOR));
    }
}
