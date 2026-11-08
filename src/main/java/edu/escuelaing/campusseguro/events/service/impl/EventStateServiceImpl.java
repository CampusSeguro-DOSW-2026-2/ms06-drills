package edu.escuelaing.campusseguro.events.service.impl;

import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.EventStateHistoryEntity;
import edu.escuelaing.campusseguro.events.entity.enums.AuditRecordType;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.exception.ResourceNotFoundException;
import edu.escuelaing.campusseguro.events.repository.EventRepository;
import edu.escuelaing.campusseguro.events.repository.EventStateHistoryRepository;
import edu.escuelaing.campusseguro.events.service.AuditService;
import edu.escuelaing.campusseguro.events.service.EventStateMachine;
import edu.escuelaing.campusseguro.events.service.EventStateService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default implementation of {@link EventStateService}. */
@Service
public class EventStateServiceImpl implements EventStateService {

    private static final int REASON_MAX_LENGTH = 300;

    private final EventRepository events;
    private final EventStateHistoryRepository history;
    private final EventStateMachine stateMachine;
    private final AuditService audit;
    private final Clock clock;

    public EventStateServiceImpl(
            EventRepository events,
            EventStateHistoryRepository history,
            EventStateMachine stateMachine,
            AuditService audit,
            Clock clock) {
        this.events = events;
        this.history = history;
        this.stateMachine = stateMachine;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
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
        boolean finished = target == EventStatus.FINISHED;
        audit.record(
                finished ? AuditRecordType.FINALIZATION : AuditRecordType.MODIFICATION,
                finished ? "EVENT_FINISHED" : "EVENT_STATUS_CHANGED",
                actor, saved, from + " -> " + target + (reason == null ? "" : ": " + reason));
        return saved;
    }

    @Override
    @Transactional
    public void recordCreation(EventEntity event, String actor) {
        record(event.getId(), null, event.getStatus(), actor, null, clock.instant());
        audit.record(AuditRecordType.CREATION, "EVENT_CREATED", actor, event, "Created as " + event.getStatus());
    }

    @Override
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
