package edu.escuelaing.campusseguro.events.entity.enums;

/**
 * Why an alert linked to an event was created. An event creates several alerts
 * in ALE (MS03); this link table tells which is which.
 */
public enum EventAlertPurpose {

    /** Asks brigadiers to activate and choose a zone. Immediate when Scheduled and when an edit is confirmed. */
    BRIGADISTS,
    /** Tells participants the event started. Scheduled for the start date. */
    GENERAL,
    /** Reports a confirmed change in an In progress event (brigadiers and participants). */
    EDIT,
    /** Tells participants the event finished or its conditions have been modified. */
    FINISH,
}
