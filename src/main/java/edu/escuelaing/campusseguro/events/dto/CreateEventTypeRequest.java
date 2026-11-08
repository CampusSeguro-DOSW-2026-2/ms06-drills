package edu.escuelaing.campusseguro.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data to add a type to the catalog. The code is generated from the name.
 *
 * @param name display name, unique
 */
public record CreateEventTypeRequest(@NotBlank @Size(max = 80) String name) {
}
