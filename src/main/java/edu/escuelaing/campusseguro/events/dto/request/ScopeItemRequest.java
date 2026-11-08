package edu.escuelaing.campusseguro.events.dto.request;

import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A building or zone of the event scope.
 *
 * @param type building or zone
 * @param id   identifier in CAM
 */
public record ScopeItemRequest(@NotNull ScopeType type, @NotBlank @Size(max = 64) String id) {
}
