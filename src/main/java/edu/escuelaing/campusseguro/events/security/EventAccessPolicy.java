package edu.escuelaing.campusseguro.events.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Access rules of the events: who may manage which kind of event.
 *
 * <p>The coordinator manages real events and the organizer manages drills. The admin and the
 * auditor can read events but cannot create or edit them.
 */
@Component
public class EventAccessPolicy {

    /**
     * Tells if the actor may create or edit an event of the given kind.
     *
     * @param actor     who makes the request
     * @param simulacro {@code true} for a drill, {@code false} for a real event
     */
    public boolean canManage(AccessActor actor, boolean simulacro) {
        return actor.hasRole(simulacro ? Role.ORGANIZADOR : Role.COORDINADOR);
    }

    /**
     * Same rule as {@link #canManage}, but throws when the actor is not allowed.
     *
     * @param action what the actor tries to do, used in the message (for example "create")
     * @throws AccessDeniedException if the role does not match the kind of event
     */
    public void assertCanManage(AccessActor actor, boolean simulacro, String action) {
        if (!canManage(actor, simulacro)) {
            throw new AccessDeniedException("Only the " + (simulacro ? Role.ORGANIZADOR : Role.COORDINADOR)
                    + " can " + action + " this kind of event");
        }
    }
}
