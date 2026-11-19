package edu.escuelaing.campusseguro.events.security;

import edu.escuelaing.campusseguro.events.security.Permission.Action;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Access rules of the events. They are based on the permissions MS01 gives to the user, one set
 * for drills ({@code ms06:drill:*}) and one for real events ({@code ms06:real_event:*}).
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
        return can(actor, simulacro, Action.MANAGE);
    }

    /** Tells if the actor has the permission of the action for this kind of event. */
    public boolean can(AccessActor actor, boolean simulacro, Action action) {
        return actor.can(simulacro, action);
    }

    /**
     * Same rule as {@link #canManage}, but throws when the actor is not allowed.
     *
     * @param action what the actor tries to do, used in the message (for example "create")
     * @throws AccessDeniedException if the actor does not have the permission
     */
    public void assertCanManage(AccessActor actor, boolean simulacro, String action) {
        assertCan(actor, simulacro, Action.MANAGE, action);
    }

    /**
     * Throws when the actor does not have the permission of the action for this kind of event.
     *
     * @param permissionAction the permission that is required
     * @param description      what the actor tries to do, used in the message (for example "finish")
     */
    public void assertCan(AccessActor actor, boolean simulacro, Action permissionAction, String description) {
        if (!can(actor, simulacro, permissionAction)) {
            throw new AccessDeniedException("Missing permission " + Permission.code(simulacro, permissionAction)
                    + " to " + description + " this kind of event");
        }
    }
}
