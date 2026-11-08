package edu.escuelaing.campusseguro.events.service;

import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.EventStateHistoryEntity;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.exception.ResourceNotFoundException;
import edu.escuelaing.campusseguro.events.repository.EventRepository;
import edu.escuelaing.campusseguro.events.repository.EventStateHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changes the status of an event and writes the status history.
 *
 * <p>It only changes the status and its dates. Side effects (alerts, calls to CHI/BEI)
 * belong to the services that call it, so a failure there never leaves a half-done change here.
 */
@Service
public class EventStateService {

    private static final int REASON_MAX_LENGTH = 300;

    private final EventRepository events;
    private final EventStateHistoryRepository history;
    private final EventStateMachine stateMachine;
    private final Clock clock;

    public EventStateService(
            EventRepository events,
            EventStateHistoryRepository history,
            EventStateMachine stateMachine,
            Clock clock) {
        this.events = events;
        this.history = history;
        this.stateMachine = stateMachine;
        this.clock = clock;
    }

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
    @Transactional
    public EventEntity transition(UUID eventId, EventStatus target, String actor, String reason) {
        EventEntity event = events.findForUpdate(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        EventStatus from = event.getStatus();
        stateMachine.assertTransition(from, target);

        Instant now = clock.instant();
        event.setStatus(target);
        if (target == EventStatus.IN_PROGRESS) {
            event.setStartedAt(now);
        }
        if (target == EventStatus.FINISHED) {
            event.setEndedAt(now);
        }

        EventEntity saved = events.save(event);
        record(eventId, from, target, actor, reason, now);
        return saved;
    }

    /** Writes the first history entry of a new event (no previous status). */
    @Transactional
    public void recordCreation(EventEntity event, String actor) {
        record(event.getId(), null, event.getStatus(), actor, null, clock.instant());
    }

    /**
     * Status history of an event, oldest change first.
     *
     * @throws ResourceNotFoundException if the event does not exist
     */
    @Transactional(readOnly = true)
    public List<EventStateHistoryEntity> history(UUID eventId) {
        if (!events.existsById(eventId)) {
            throw new ResourceNotFoundException("Event not found: " + eventId);
        }
        return history.findByEventIdOrderByChangedAtAsc(eventId);
    }

    private void record(UUID eventId, EventStatus from, EventStatus to, String actor, String reason, Instant at) {
        EventStateHistoryEntity entry = new EventStateHistoryEntity();
        entry.setEventId(eventId);
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setChangedBy(actor);
        entry.setReason(cut(reason));
        entry.setChangedAt(at);
        history.save(entry);
    }

    private String cut(String reason) {
        if (reason == null || reason.length() <= REASON_MAX_LENGTH) {
            return reason;
        }
        return reason.substring(0, REASON_MAX_LENGTH);
    }
}
