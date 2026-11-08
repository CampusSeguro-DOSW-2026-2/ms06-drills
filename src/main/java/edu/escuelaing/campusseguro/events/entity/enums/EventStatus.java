package edu.escuelaing.campusseguro.events.entity.enums;

/**
 * Life cycle statuses of an event.
 *
 * <p>Allowed transitions (checked by the state machine in step 3):
 * Draft ⇄ Scheduled, Scheduled → In progress and In progress → Finished.
 * There is no "Evaluated" status: the evaluation is created automatically on finish.
 */
public enum EventStatus {

    /** Draft: incomplete data or no start date. Can be edited and sends no alerts yet. */
    DRAFT,
    /** Scheduled: has a start date and brigadiers were already alerted. */
    SCHEDULED,
    /** In progress: the event started; real-time monitoring is on. */
    IN_PROGRESS,
    /** Finished: a coordinator or organizer ended it; it has an automatic evaluation. */
    FINISHED
}
