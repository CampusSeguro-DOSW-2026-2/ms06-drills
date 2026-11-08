package edu.escuelaing.campusseguro.events.dto.response;

import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * An event as shown in a list.
 *
 * @param id               identifier
 * @param name             name
 * @param status           current status
 * @param simulacro        {@code true} if it is a drill
 * @param startDate        scheduled start (empty if never set)
 * @param startedAt        real start
 * @param endedAt          real end
 * @param types            names of the event types
 * @param scope            buildings and zones
 * @param participantRoles invited roles
 * @param createdBy        who created (and is responsible for) the event
 */
public record EventSummaryResponse(
        UUID id,
        String name,
        EventStatus status,
        boolean simulacro,
        Instant startDate,
        Instant startedAt,
        Instant endedAt,
        List<String> types,
        List<EventResponse.ScopeItem> scope,
        Set<Role> participantRoles,
        String createdBy) {
}
