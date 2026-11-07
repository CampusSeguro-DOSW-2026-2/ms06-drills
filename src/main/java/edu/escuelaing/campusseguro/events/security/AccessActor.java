package edu.escuelaing.campusseguro.events.security;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * User ID and valid roles.
 */
public record AccessActor(String userId, Set<Role> roles) {

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

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
