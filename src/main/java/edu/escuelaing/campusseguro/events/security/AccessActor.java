package edu.escuelaing.campusseguro.events.security;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Who makes the request: user identifier and recognized roles.
 */
public record AccessActor(String userId, Set<Role> roles) {

    private static final int MAX_ID_LENGTH = 64;

    /**
     * Builds the actor from the current authentication. Ignores authorities that are
     * not a {@link Role}; without authentication it returns an empty actor.
     */
    public static AccessActor from(Authentication authentication) {
        if (authentication == null) {
            return new AccessActor("", Set.of());
        }
        Set<Role> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(Role::fromClaim)
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
        return new AccessActor(authentication.getName(), roles);
    }

    /** User id cut to 64 characters, the size of the columns that store the author of an action. */
    public String auditId() {
        return userId.length() <= MAX_ID_LENGTH ? userId : userId.substring(0, MAX_ID_LENGTH);
    }

    /**
     * Tells if the actor may create or edit an event of the given kind: the coordinator manages
     * real events and the organizer manages drills.
     */
    public boolean canManageEvent(boolean simulacro) {
        return hasRole(simulacro ? Role.ORGANIZADOR : Role.COORDINADOR);
    }

    /** Tells if the actor has the given role. */
    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
