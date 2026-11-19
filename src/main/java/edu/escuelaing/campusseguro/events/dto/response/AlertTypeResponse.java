package edu.escuelaing.campusseguro.events.dto.response;

import edu.escuelaing.campusseguro.events.entity.EventTypeEntity;

/**
 * An alert type of the MS06 catalog, as MS03 receives it.
 *
 * <p>Only the code and the display name are exposed: MS03 deduces that a type
 * was removed from the catalog by its absence, so {@code id} and {@code active}
 * are not part of the contract.
 *
 * @param code stable code (for example {@code SISMO})
 * @param name display name
 */
public record AlertTypeResponse(String code, String name) {

    /** Builds the response from the event type entity. */
    public static AlertTypeResponse from(EventTypeEntity type) {
        return new AlertTypeResponse(type.getCode(), type.getName());
    }
}
