package edu.escuelaing.campusseguro.events.entity.enums;

/**
 * Status of a brigadier in an event, as reported by BEI (MS05).
 */
public enum BrigadierStatus {

    /** Active brigadier assigned to a zone. */
    ACTIVE,
    /** Brigadier deactivated or who removed the assignment. */
    INACTIVE
}
