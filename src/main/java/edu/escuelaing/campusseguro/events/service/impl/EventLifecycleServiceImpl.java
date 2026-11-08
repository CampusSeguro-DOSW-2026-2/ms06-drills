package edu.escuelaing.campusseguro.events.service.impl;

import edu.escuelaing.campusseguro.events.client.EventStatePayload;
import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.client.alert.AlertCommand;
import edu.escuelaing.campusseguro.events.client.alert.AlertGateway;
import edu.escuelaing.campusseguro.events.client.brigade.BrigadeGateway;
import edu.escuelaing.campusseguro.events.client.checkin.CheckInGateway;
import edu.escuelaing.campusseguro.events.dto.response.EventWarning;
import edu.escuelaing.campusseguro.events.entity.EventAlertEntity;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.EventStartFailureEntity;
import edu.escuelaing.campusseguro.events.entity.enums.EventAlertPurpose;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.entity.enums.ExternalModule;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.repository.EventAlertRepository;
import edu.escuelaing.campusseguro.events.repository.EventParticipantRepository;
import edu.escuelaing.campusseguro.events.repository.EventRepository;
import edu.escuelaing.campusseguro.events.repository.EventScopeItemRepository;
import edu.escuelaing.campusseguro.events.repository.EventStartFailureRepository;
import edu.escuelaing.campusseguro.events.security.Role;
import edu.escuelaing.campusseguro.events.service.EventLifecycleService;
import edu.escuelaing.campusseguro.events.service.EventStateService;
import edu.escuelaing.campusseguro.events.service.SchedulingOutcome;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Default implementation of {@link EventLifecycleService}. */
@Service
public class EventLifecycleServiceImpl implements EventLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(EventLifecycleServiceImpl.class);

    private static final String BRIGADIERS_TITLE = "Convocatoria de brigadistas: ";
    private static final String BRIGADIERS_TEXT =
            "Si es posible, activa tu estado y selecciona la zona que atenderás en este evento.";
    private static final String START_TITLE = "Inicio de evento: ";
    private static final String START_TEXT = "El evento ha comenzado. Sigue las indicaciones de los brigadistas.";

    /** Author written in the history when the system starts an event. */
    static final String SYSTEM_ACTOR = "system";
    private static final int ERROR_MAX_LENGTH = 500;

    private final CheckInGateway checkIn;
    private final BrigadeGateway brigade;
    private final AlertGateway alerts;
    private final EventAlertRepository alertLinks;
    private final EventStateService stateService;
    private final EventRepository events;
    private final EventScopeItemRepository scopeItems;
    private final EventParticipantRepository participants;
    private final EventStartFailureRepository failures;
    private final TransactionTemplate tx;
    private final Clock clock;

    public EventLifecycleServiceImpl(
            CheckInGateway checkIn,
            BrigadeGateway brigade,
            AlertGateway alerts,
            EventAlertRepository alertLinks,
            EventStateService stateService,
            EventRepository events,
            EventScopeItemRepository scopeItems,
            EventParticipantRepository participants,
            EventStartFailureRepository failures,
            TransactionTemplate tx,
            Clock clock) {
        this.checkIn = checkIn;
        this.brigade = brigade;
        this.alerts = alerts;
        this.alertLinks = alertLinks;
        this.stateService = stateService;
        this.events = events;
        this.scopeItems = scopeItems;
        this.participants = participants;
        this.failures = failures;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SchedulingOutcome schedule(EventEntity event, List<ScopeRef> scope, Set<Role> roles, String actor) {
        EventStatePayload scheduled = payload(event, EventStatus.SCHEDULED, scope, roles);
        EventStatePayload draft = payload(event, EventStatus.DRAFT, scope, roles);

        checkIn.pushState(scheduled);

        try {
            brigade.pushState(scheduled);
        } catch (ExternalServiceUnavailableException ex) {
            bestEffort(() -> checkIn.pushState(draft));
            return stayDraft("BEI");
        }

        List<EventAlertEntity> links = new ArrayList<>();
        try {
            links.add(createAlert(event, brigadiersAlert(event, scope)));
            links.add(createAlert(event, generalAlert(event, scope, roles)));
        } catch (ExternalServiceUnavailableException ex) {
            links.forEach(link -> bestEffort(() -> alerts.backToDraft(link.getAlertId())));
            bestEffort(() -> checkIn.pushState(draft));
            bestEffort(() -> brigade.pushState(draft));
            return stayDraft("ALE");
        }

        List<EventAlertEntity> saved = alertLinks.saveAll(links);
        stateService.transition(event.getId(), EventStatus.SCHEDULED, actor, "Scheduled with all its data");
        return new SchedulingOutcome(true, List.of(), saved);
    }

    @Override
    public int startDueEvents() {
        List<UUID> due = events.findByStatusAndScheduledStartLessThanEqual(EventStatus.SCHEDULED, clock.instant())
                .stream().map(EventEntity::getId).toList();
        int started = 0;
        for (UUID eventId : due) {
            try {
                if (Boolean.TRUE.equals(tx.execute(status -> startOne(eventId)))) {
                    started++;
                }
            } catch (RuntimeException ex) {
                log.error("Could not start event {}", eventId, ex);
            }
        }
        return started;
    }

    @Override
    public int retryPendingStartCalls() {
        List<UUID> pending = failures.findByResolvedAtIsNull().stream()
                .map(EventStartFailureEntity::getId).toList();
        int closed = 0;
        for (UUID failureId : pending) {
            try {
                if (Boolean.TRUE.equals(tx.execute(status -> retryOne(failureId)))) {
                    closed++;
                }
            } catch (RuntimeException ex) {
                log.error("Could not retry the pending start call {}", failureId, ex);
            }
        }
        return closed;
    }

    /**
     * Starts one event inside the transaction of the caller. The event is locked and checked
     * again, because it could have been edited or started since the list of due events was read.
     *
     * @return {@code true} if the event was started
     */
    private boolean startOne(UUID eventId) {
        EventEntity event = events.findForUpdate(eventId).orElse(null);
        Instant now = clock.instant();
        if (event == null
                || event.getStatus() != EventStatus.SCHEDULED
                || event.getScheduledStart() == null
                || event.getScheduledStart().isAfter(now)) {
            return false;
        }
        stateService.transition(eventId, EventStatus.IN_PROGRESS, SYSTEM_ACTOR, "Started automatically");

        EventStatePayload payload = payload(event, EventStatus.IN_PROGRESS, scopeOf(eventId), rolesOf(eventId));
        for (ExternalModule module : ExternalModule.values()) {
            attempt(eventId, module, payload);
        }
        return true;
    }

    /**
     * Repeats one failed call. It is closed if the call works or if the event is not In progress
     * any more (for example, it was already finished).
     *
     * @return {@code true} if the pending call was closed
     */
    private boolean retryOne(UUID failureId) {
        EventStartFailureEntity failure = failures.findById(failureId).orElse(null);
        if (failure == null || !failure.isOpen()) {
            return false;
        }
        EventEntity event = events.findById(failure.getEventId()).orElse(null);
        if (event == null || event.getStatus() != EventStatus.IN_PROGRESS) {
            failure.setResolvedAt(clock.instant());
            failures.save(failure);
            return true;
        }
        EventStatePayload payload = payload(event, EventStatus.IN_PROGRESS, scopeOf(event.getId()), rolesOf(event.getId()));
        attempt(event.getId(), failure.getModule(), payload);
        return failures.findById(failureId).map(row -> !row.isOpen()).orElse(false);
    }

    /** Calls one module and saves the result: a failure is stored or updated, a success closes it. */
    private void attempt(UUID eventId, ExternalModule module, EventStatePayload payload) {
        try {
            switch (module) {
                case CHI -> checkIn.pushState(payload);
                case BEI -> brigade.pushState(payload);
                case ALE -> activateStartAlert(eventId);
            }
            closeFailure(eventId, module);
        } catch (ExternalServiceUnavailableException ex) {
            log.warn("{} did not answer when event {} started: it will be retried", module, eventId);
            saveFailure(eventId, module, ex);
        }
    }

    /** Sends the alert that tells the participants the event started. */
    private void activateStartAlert(UUID eventId) {
        for (EventAlertEntity link : alertLinks.findByEventIdAndPurpose(eventId, EventAlertPurpose.GENERAL)) {
            alerts.activate(link.getAlertId());
        }
    }

    private void closeFailure(UUID eventId, ExternalModule module) {
        failures.findByEventIdAndModule(eventId, module).filter(EventStartFailureEntity::isOpen).ifPresent(row -> {
            row.setResolvedAt(clock.instant());
            failures.save(row);
        });
    }

    private void saveFailure(UUID eventId, ExternalModule module, ExternalServiceUnavailableException ex) {
        EventStartFailureEntity row = failures.findByEventIdAndModule(eventId, module).orElseGet(() -> {
            EventStartFailureEntity created = new EventStartFailureEntity();
            created.setEventId(eventId);
            created.setModule(module);
            return created;
        });
        row.setAttempts(row.getAttempts() + 1);
        row.setLastError(cut(ex.getMessage()));
        row.setResolvedAt(null);
        failures.save(row);
    }

    private List<ScopeRef> scopeOf(UUID eventId) {
        return scopeItems.findByEventId(eventId).stream()
                .map(item -> new ScopeRef(item.getScopeType(), item.getScopeRefId()))
                .toList();
    }

    private Set<Role> rolesOf(UUID eventId) {
        return participants.findByEventId(eventId).stream()
                .map(participant -> participant.getRole())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
    }

    private String cut(String text) {
        if (text == null || text.length() <= ERROR_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, ERROR_MAX_LENGTH);
    }

    private EventAlertEntity createAlert(EventEntity event, AlertCommand command) {
        String alertId = alerts.createAlert(command);
        EventAlertEntity link = new EventAlertEntity();
        link.setEventId(event.getId());
        link.setAlertId(alertId);
        link.setPurpose(command.purpose());
        return link;
    }

    /** Alert that asks brigadiers to activate and choose a zone. It is sent right away. */
    private AlertCommand brigadiersAlert(EventEntity event, List<ScopeRef> scope) {
        return new AlertCommand(
                event.getId(), EventAlertPurpose.BRIGADISTS, event.isSimulacro(),
                BRIGADIERS_TITLE + event.getName(), BRIGADIERS_TEXT,
                scope, Set.of(Role.BRIGADISTA), null, true);
    }

    /** Alert that tells participants the event started. It is scheduled for the start date. */
    private AlertCommand generalAlert(EventEntity event, List<ScopeRef> scope, Set<Role> roles) {
        return new AlertCommand(
                event.getId(), EventAlertPurpose.GENERAL, event.isSimulacro(),
                START_TITLE + event.getName(), START_TEXT,
                scope, roles, event.getScheduledStart(), false);
    }

    private EventStatePayload payload(EventEntity event, EventStatus status, List<ScopeRef> scope, Set<Role> roles) {
        return new EventStatePayload(
                event.getId(), event.getName(), event.isSimulacro(), status,
                event.getScheduledStart(), event.getEndedAt(), scope, roles);
    }

    private SchedulingOutcome stayDraft(String service) {
        EventWarning warning = new EventWarning(
                "SAVED_AS_DRAFT",
                "The event was saved as a draft because " + service + " is unavailable. Try to schedule it again later.");
        return new SchedulingOutcome(false, List.of(warning), List.of());
    }

    /** Runs an undo action; a failure is only logged because the main operation must not fail because of it. */
    private void bestEffort(Runnable undo) {
        try {
            undo.run();
        } catch (RuntimeException ex) {
            log.warn("Could not undo a call to another service", ex);
        }
    }
}
