package edu.escuelaing.campusseguro.events.service.impl;

import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.EventStateHistoryEntity;
import edu.escuelaing.campusseguro.events.entity.enums.AuditRecordType;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.exception.ResourceNotFoundException;
import edu.escuelaing.campusseguro.events.repository.EventRepository;
import edu.escuelaing.campusseguro.events.repository.EventStateHistoryRepository;
import edu.escuelaing.campusseguro.events.service.AuditExtras;
import edu.escuelaing.campusseguro.events.service.AuditService;
import edu.escuelaing.campusseguro.events.service.EventStateMachine;
import edu.escuelaing.campusseguro.events.service.EventStateService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        auditChange(saved, from, target, actor, now);
        return saved;
    }

    /**
     * Audits the change as the audit catalog says. The free-text reason is never sent to MS08.
     * EVENT_STARTED (time = startedAt) is the zero point of the check-in and evacuation times in MS08.
     */
    private void auditChange(EventEntity event, EventStatus from, EventStatus target, String actor, Instant now) {
        if (target == EventStatus.IN_PROGRESS) {
            audit.record(AuditRecordType.MODIFICATION, "EVENT_STARTED", actor, event, null,
                    new AuditExtras(null,
                            Map.of("automatico", EventLifecycleServiceImpl.SYSTEM_ACTOR.equals(actor)), now));
        } else if (target == EventStatus.FINISHED) {
            Map<String, Object> attributes = new LinkedHashMap<>();
            if (event.getStartedAt() != null) {
                attributes.put("duracion_segundos", Duration.between(event.getStartedAt(), now).getSeconds());
            }
            audit.record(AuditRecordType.FINALIZATION, "EVENT_FINISHED", actor, event, null,
                    new AuditExtras(null, attributes, now));
        } else {
            audit.record(AuditRecordType.MODIFICATION, "EVENT_STATUS_CHANGED", actor, event, null,
                    AuditExtras.attributes(Map.of("estado_anterior", from.name(), "estado_nuevo", target.name())));
        }
    }

    @Override
    @Transactional
    public void recordCreation(EventEntity event, String actor) {
        record(event.getId(), null, event.getStatus(), actor, null, clock.instant());
        audit.record(AuditRecordType.CREATION, "EVENT_CREATED", actor, event, null);
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
