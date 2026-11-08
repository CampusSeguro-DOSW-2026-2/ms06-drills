package edu.escuelaing.campusseguro.events.client;

import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What CHI and BEI need to know about an event. MS06 sends it every time the event is
 * scheduled, edited, started or finished; the call is idempotent, so it can be repeated.
 *
 * @param eventId      event identifier
 * @param name         event name
 * @param simulacro    true if the event is a drill
 * @param status       current status
 * @param startDate    scheduled start (empty in draft)
 * @param endDate      real end (only when finished)
 * @param scope        buildings and zones of the event
 * @param invitedRoles roles invited to the event
 */
public record EventStatePayload(
        UUID eventId,
        String name,
        boolean simulacro,
        EventStatus status,
        Instant startDate,
        Instant endDate,
        List<ScopeRef> scope,
        Set<Role> invitedRoles) {
}
