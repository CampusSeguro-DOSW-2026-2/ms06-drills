package edu.escuelaing.campusseguro.events.client.alert;

import edu.escuelaing.campusseguro.events.security.AccessActor;
import edu.escuelaing.campusseguro.events.security.Role;
import java.util.Set;

/**
 * Identity that MS06 propagates to MS03 through the Gateway.
 *
 * <p>The Gateway requires {@code X-User-Id}, {@code X-User-Role} and {@code X-User-Permission} to travel together when
 * a service acts on behalf of a user. For automatic calls (the scheduled start of an event) MS06 uses the user who
 * created the event and the role that authorizes the operation.
 *
 * @param userId    UUID of the acting user
 * @param roleNames names of the roles of the acting user, including the ones MS01 created
 */
public record AlertActor(String userId, Set<String> roleNames) {

    public AlertActor {
        roleNames = roleNames == null ? Set.of() : Set.copyOf(roleNames);
    }

    /** Takes the identity from the authenticated request. */
    public static AlertActor from(AccessActor actor) {
        return new AlertActor(actor.userId(), actor.roleNames());
    }

    /** Identity for a call that is not triggered by an authenticated request. */
    public static AlertActor system(String userId, Role role) {
        return new AlertActor(userId, Set.of(role.name()));
    }
}
