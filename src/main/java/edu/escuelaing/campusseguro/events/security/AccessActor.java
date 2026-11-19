package edu.escuelaing.campusseguro.events.security;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Who makes the request: user identifier, roles and permissions.
 *
 * <p>Permissions decide what the user may do. Roles are only used to know which events invite the user as a
 * participant and to propagate the identity to the other modules.
 *
 * <p>MS01 keeps the roles as data, so a role that is not in the {@link Role} enum still travels in the request and
 * counts as a participant. {@link #roles()} has the roles of the official catalog and {@link #roleNames()} every role
 * name of the user.
 */
public record AccessActor(String userId, Set<Role> roles, Set<String> roleNames, Set<String> permissions) {

    private static final int MAX_ID_LENGTH = 64;
    private static final String ROLE_PREFIX = "ROLE_";

    public AccessActor {
        roles = roles == null ? Set.of() : roles;
        permissions = permissions == null ? Set.of() : permissions;
        roleNames = roleNames == null || roleNames.isEmpty()
                ? roles.stream().map(Role::name).collect(Collectors.toUnmodifiableSet())
                : roleNames.stream()
                        .filter(name -> name != null && !name.isBlank())
                        .collect(Collectors.toUnmodifiableSet());
    }

    /** Keeps the previous shape for callers that only know the official roles. */
    public AccessActor(String userId, Set<Role> roles, Set<String> permissions) {
        this(userId, roles, Set.of(), permissions);
    }

    /**
     * Builds the actor from the current authentication. Authorities that start with {@code ROLE_} are roles, also the
     * ones MS06 does not know; every other authority is a permission. Without authentication it returns an empty actor.
     */
    public static AccessActor from(Authentication authentication) {
        if (authentication == null) {
            return new AccessActor("", Set.of(), Set.of(), Set.of());
        }
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> roleNames = authorities.stream()
                .filter(value -> value.startsWith(ROLE_PREFIX))
                .map(value -> value.substring(ROLE_PREFIX.length()))
                .filter(RoleNames::hasStoredFormat)
                .collect(Collectors.toUnmodifiableSet());
        Set<Role> roles = roleNames.stream()
                .map(Role::fromClaim)
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> permissions = authorities.stream()
                .filter(value -> !value.startsWith(ROLE_PREFIX))
                .collect(Collectors.toUnmodifiableSet());
        return new AccessActor(authentication.getName(), roles, roleNames, permissions);
    }

    /** User id cut to 64 characters, the size of the columns that store the author of an action. */
    public String auditId() {
        return userId.length() <= MAX_ID_LENGTH ? userId : userId.substring(0, MAX_ID_LENGTH);
    }

    /** Tells if the actor has the given role. */
    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    /** Tells if the actor has the permission for this kind of event and action. */
    public boolean can(boolean simulacro, Permission.Action action) {
        return permissions.contains(Permission.code(simulacro, action));
    }
}
