package edu.escuelaing.campusseguro.events.client.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.escuelaing.campusseguro.events.exception.InvalidReferenceException;
import edu.escuelaing.campusseguro.events.security.Role;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LocalIdentityGatewayTest {

    private final LocalIdentityGateway gateway = new LocalIdentityGateway();

    @Test
    void acceptsNullAndEmptyRoles() {
        assertDoesNotThrow(() -> gateway.validateRoles(null));
        assertDoesNotThrow(() -> gateway.validateRoles(Set.of()));
    }

    @Test
    void acceptsEveryRoleOfTheCatalog() {
        assertDoesNotThrow(() -> gateway.validateRoles(EnumSet.allOf(Role.class)));
    }

    @Test
    void rejectsAMissingRole() {
        Set<Role> roles = new HashSet<>();
        roles.add(Role.ESTUDIANTE);
        roles.add(null);

        InvalidReferenceException error =
                assertThrows(InvalidReferenceException.class, () -> gateway.validateRoles(roles));

        assertThat(error.field()).isEqualTo("participantRoles");
        assertThat(error.invalidIds()).containsExactly("null");
    }
}
