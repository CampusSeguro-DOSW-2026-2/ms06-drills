package edu.escuelaing.campusseguro.events.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Class that gets the text that is used to identify the user that made the action
 */
@Component
public class ActorResolver {

    private static final int MAX_ACTOR_LENGTH = 64;

    public String resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("No hay un usuario autenticado");
        }
        String actor = authentication.getName();
        return actor.length() <= MAX_ACTOR_LENGTH ? actor : actor.substring(0, MAX_ACTOR_LENGTH);
    }
}
