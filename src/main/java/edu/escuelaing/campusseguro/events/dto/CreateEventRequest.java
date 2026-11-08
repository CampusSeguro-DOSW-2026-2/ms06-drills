package edu.escuelaing.campusseguro.events.dto;

import edu.escuelaing.campusseguro.events.security.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Data to create an event
 *
 * @param name             event name
 * @param description      goal and context (optional)
 * @param typeIds          catalog types, from 1 to 4
 * @param startDate        scheduled start; must be in the future (optional)
 * @param scope            buildings and zones from CAM (optional)
 * @param participantRoles community roles that are invited (optional)
 */
public record CreateEventRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 1000) String description,
        @NotEmpty @Size(max = 4) Set<UUID> typeIds,
        Instant startDate,
        List<@Valid ScopeItemRequest> scope,
        Set<Role> participantRoles) {
}
