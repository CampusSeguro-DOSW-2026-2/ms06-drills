package edu.escuelaing.campusseguro.events.service;

import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.EventStateHistoryEntity;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;

/**
 * Changes the status of an event and writes the status history.
 *
 * <p>It only changes the status and its dates. Side effects (alerts, calls to CHI/BEI)
 * belong to the services that call it, so a failure there never leaves a half-done change here.
 */
public interface EventStateService {

    /**
     * Moves an event to a new status.
     *
     * <p>The event is locked (pessimistic lock) so two simultaneous actions cannot change its
     * status at the same time. Entering IN_PROGRESS sets {@code startedAt} and entering FINISHED
     * sets {@code endedAt}, both with the real time of the change.
     *
     * @param eventId event to change
     * @param target  new status
     * @param actor   user or process that asks for the change
     * @param reason  optional note (cut to 300 characters)
     * @return the saved event
     * @throws ResourceNotFoundException                                                       if the event does not exist
     * @throws edu.escuelaing.campusseguro.events.exception.InvalidStateTransitionException if the change is not allowed
     */
    EventEntity transition(UUID eventId, EventStatus target, String actor, String reason);

    /** Writes the first history entry of a new event (no previous status). */
    void recordCreation(EventEntity event, String actor);

    /**
     * Status history of an event, oldest change first.
     *
     * @throws ResourceNotFoundException if the event does not exist
     */
    List<EventStateHistoryEntity> history(UUID eventId);
}
