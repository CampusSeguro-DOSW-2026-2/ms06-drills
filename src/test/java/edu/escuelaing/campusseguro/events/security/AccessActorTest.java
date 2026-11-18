package edu.escuelaing.campusseguro.events.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.escuelaing.campusseguro.events.security.Permission.Action;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Tests that the actor is built from the authentication and answers its roles and permissions. */
class AccessActorTest {

    @Test
    void anonymousWhenThereIsNoAuthentication() {
        AccessActor actor = AccessActor.from(null);

        assertEquals("", actor.userId());
        assertTrue(actor.roles().isEmpty());
        assertTrue(actor.permissions().isEmpty());
    }

    @Test
    void takesTheUserTheRecognizedRolesAndThePermissions() {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                "user-1",
                "n/a",
                List.of(
                        new SimpleGrantedAuthority("ROLE_BRIGADISTA"),
                        new SimpleGrantedAuthority("ROLE_DESCONOCIDO"),
                        new SimpleGrantedAuthority("ms06:drill:read")));

        AccessActor actor = AccessActor.from(authentication);

        assertEquals("user-1", actor.userId());
        assertEquals(Set.of(Role.BRIGADISTA), actor.roles());
        assertEquals(Set.of("ms06:drill:read"), actor.permissions());
        assertTrue(actor.hasRole(Role.BRIGADISTA));
        assertFalse(actor.hasRole(Role.COORDINADOR));
    }

    @Test
    void canChecksTheKindOfEventAndTheAction() {
        AccessActor actor = new AccessActor("u", Set.of(), Set.of("ms06:drill:read", "ms06:real_event:manage"));

        assertTrue(actor.can(true, Action.READ));
        assertFalse(actor.can(false, Action.READ));
        assertTrue(actor.can(false, Action.MANAGE));
        assertFalse(actor.can(true, Action.MANAGE));
    }

    @Test
    void auditIdIsCutTo64Characters() {
        assertEquals("user-1", new AccessActor("user-1", Set.of(), Set.of()).auditId());
        assertEquals(64, new AccessActor("x".repeat(100), Set.of(), Set.of()).auditId().length());
    }
}
