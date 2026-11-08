package edu.escuelaing.campusseguro.events.client;

import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;

/** Names of the event statuses in the contracts with CHI and BEI (Spanish, as agreed with them). */
public final class StatusLabels {

    private StatusLabels() {
    }

    /** Contract name of a status: BORRADOR, PROGRAMADO, EN_EJECUCION or FINALIZADO. */
    public static String of(EventStatus status) {
        return switch (status) {
            case DRAFT -> "BORRADOR";
            case SCHEDULED -> "PROGRAMADO";
            case IN_PROGRESS -> "EN_EJECUCION";
            case FINISHED -> "FINALIZADO";
        };
    }
}
