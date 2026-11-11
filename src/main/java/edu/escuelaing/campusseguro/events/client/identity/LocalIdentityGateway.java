package edu.escuelaing.campusseguro.events.client.identity;

import edu.escuelaing.campusseguro.events.exception.InvalidReferenceException;
import edu.escuelaing.campusseguro.events.security.Role;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * {@link IdentityGateway} that checks the invited roles locally, without calling MS01.
 *
 * <p>The official role catalog is the {@link Role} enum. MS01 only lists every role to an ADMIN
 * user, so MS06 (which calls with a system or coordinator identity) cannot ask it for the catalog.
 * A role name that is not in the enum is already rejected when the request body is read, so here
 * only a missing (null) role can still be invalid.
 */
@Component
public class LocalIdentityGateway implements IdentityGateway {

    private static final String FIELD = "participantRoles";

    @Override
    public void validateRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return;
        }
        Set<Role> catalog = EnumSet.allOf(Role.class);
        List<String> invalid = roles.stream()
                .filter(role -> role == null || !catalog.contains(role))
                .map(role -> Objects.toString(role))
                .toList();
        if (!invalid.isEmpty()) {
            throw new InvalidReferenceException(FIELD, invalid);
        }
    }
}
