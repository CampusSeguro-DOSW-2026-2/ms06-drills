package edu.escuelaing.campusseguro.events.dto.response;

import edu.escuelaing.campusseguro.events.entity.enums.EventAlertPurpose;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * An event with its configuration.
 *
 * @param id               identifier
 * @param name             name
 * @param description      goal and context
 * @param status           current status
 * @param simulacro        {@code true} if it is a drill
 * @param startDate        scheduled start (empty if never set)
 * @param startedAt        real start
 * @param endedAt          real end
 * @param types            catalog types
 * @param scope            buildings and zones
 * @param participantRoles invited roles
 * @param alerts           ALE alerts created by the event
 * @param createdBy        creator
 * @param createdAt        creation time
 * @param warnings         what stayed pending or was downgraded when the answer was built
 */
public record EventResponse(
        UUID id,
        String name,
        String description,
        EventStatus status,
        boolean simulacro,
        Instant startDate,
        Instant startedAt,
        Instant endedAt,
        List<EventTypeResponse> types,
        List<ScopeItem> scope,
        Set<Role> participantRoles,
        List<AlertLink> alerts,
        String createdBy,
        Instant createdAt,
        List<EventWarning> warnings) {

    /** A building or zone of the scope. */
    public record ScopeItem(ScopeType type, String id) {
    }

    /** An ALE alert created by the event and why. */
    public record AlertLink(EventAlertPurpose purpose, String alertId) {
    }
}
