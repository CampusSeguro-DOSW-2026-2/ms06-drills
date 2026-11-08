package edu.escuelaing.campusseguro.events.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Gets the text that identifies the author of an action
 * (status history, audit).
 */
@Component
public class ActorResolver {

    private static final int MAX_ACTOR_LENGTH = 64;

    /**
     * Returns the identifier of the authenticated user, cut to 64 characters
     * (the maximum of columns like {@code created_by} and {@code changed_by}).
     *
     * @throws AuthenticationCredentialsNotFoundException if there is no authenticated user
     */
    public String resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("No hay un usuario autenticado");
        }
        String actor = authentication.getName();
        return actor.length() <= MAX_ACTOR_LENGTH ? actor : actor.substring(0, MAX_ACTOR_LENGTH);
    }
}
