package edu.escuelaing.campusseguro.events.dto.response;

import edu.escuelaing.campusseguro.events.entity.EventTypeEntity;
import java.util.UUID;

/**
 * An event type of the catalog.
 *
 * @param id     identifier
 * @param code   stable code
 * @param name   display name
 * @param active {@code true} if it can be used in new events
 */
public record EventTypeResponse(UUID id, String code, String name, boolean active) {

    /** Builds the response from the entity. */
    public static EventTypeResponse from(EventTypeEntity type) {
        return new EventTypeResponse(type.getId(), type.getCode(), type.getName(), type.isActive());
    }
}
