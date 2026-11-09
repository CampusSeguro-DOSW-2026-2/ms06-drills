package edu.escuelaing.campusseguro.events.service.impl;

import edu.escuelaing.campusseguro.events.client.EventStatePayload;
import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.client.alert.AlertActor;
import edu.escuelaing.campusseguro.events.client.alert.AlertCommand;
import edu.escuelaing.campusseguro.events.client.alert.AlertGateway;
import edu.escuelaing.campusseguro.events.client.alert.AlertTypes;
import edu.escuelaing.campusseguro.events.client.brigade.BrigadeGateway;
import edu.escuelaing.campusseguro.events.client.campus.CampusGateway;
import edu.escuelaing.campusseguro.events.client.checkin.CheckInGateway;
import edu.escuelaing.campusseguro.events.client.identity.IdentityGateway;
import edu.escuelaing.campusseguro.events.dto.request.CreateEventRequest;
import edu.escuelaing.campusseguro.events.dto.request.ScopeItemRequest;
import edu.escuelaing.campusseguro.events.dto.response.EventResponse;
import edu.escuelaing.campusseguro.events.dto.response.EventWarning;
import edu.escuelaing.campusseguro.events.entity.EventAlertEntity;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.EventParticipantEntity;
import edu.escuelaing.campusseguro.events.entity.EventScopeItemEntity;
import edu.escuelaing.campusseguro.events.entity.EventTypeEntity;
import edu.escuelaing.campusseguro.events.entity.enums.EventAlertPurpose;
import edu.escuelaing.campusseguro.events.entity.enums.AuditRecordType;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.exception.BusinessException;
import edu.escuelaing.campusseguro.events.exception.ErrorCode;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.exception.InvalidFieldException;
import edu.escuelaing.campusseguro.events.exception.InvalidReferenceException;
import edu.escuelaing.campusseguro.events.exception.ResourceNotFoundException;
import edu.escuelaing.campusseguro.events.mapper.EventMapper;
import edu.escuelaing.campusseguro.events.repository.EventAlertRepository;
import edu.escuelaing.campusseguro.events.repository.EventParticipantRepository;
import edu.escuelaing.campusseguro.events.repository.EventRepository;
import edu.escuelaing.campusseguro.events.repository.EventScopeItemRepository;
import edu.escuelaing.campusseguro.events.repository.EventTypeRepository;
import edu.escuelaing.campusseguro.events.security.AccessActor;
import edu.escuelaing.campusseguro.events.security.EventAccessPolicy;
import edu.escuelaing.campusseguro.events.security.Role;
import edu.escuelaing.campusseguro.events.service.AuditService;
import edu.escuelaing.campusseguro.events.service.EventLifecycleService;
import edu.escuelaing.campusseguro.events.service.EventStateService;
import edu.escuelaing.campusseguro.events.service.EventUpdateService;
import edu.escuelaing.campusseguro.events.service.SchedulingOutcome;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default implementation of {@link EventUpdateService}. */
@Service
public class EventUpdateServiceImpl implements EventUpdateService {

    private static final Logger log = LoggerFactory.getLogger(EventUpdateServiceImpl.class);

    private static final String BRIGADIERS_TITLE = "Evento modificado: ";
    private static final String BRIGADIERS_TEXT =
            "El evento fue modificado. Verifica tu asignación: puedes cambiar de zona, desactivarte o retirar tu asignación.";
    private static final String PARTICIPANTS_TITLE = "Evento actualizado: ";
    private static final String PARTICIPANTS_TEXT =
            "El evento fue modificado. Revisa la información actualizada y sigue las indicaciones.";

    private final EventRepository events;
    private final EventTypeRepository eventTypes;
    private final EventScopeItemRepository scopeItems;
    private final EventParticipantRepository participants;
    private final EventAlertRepository alertLinks;
    private final CampusGateway campus;
    private final IdentityGateway identity;
    private final EventLifecycleService lifecycle;
    private final EventStateService stateService;
    private final AuditService audit;
    private final CheckInGateway checkIn;
    private final BrigadeGateway brigade;
    private final AlertGateway alerts;
    private final EventAccessPolicy accessPolicy;
    private final EventMapper mapper;
    private final Clock clock;

    public EventUpdateServiceImpl(
            EventRepository events,
            EventTypeRepository eventTypes,
            EventScopeItemRepository scopeItems,
            EventParticipantRepository participants,
            EventAlertRepository alertLinks,
            CampusGateway campus,
            IdentityGateway identity,
            EventLifecycleService lifecycle,
            EventStateService stateService,
            AuditService audit,
            CheckInGateway checkIn,
            BrigadeGateway brigade,
            AlertGateway alerts,
            EventAccessPolicy accessPolicy,
            EventMapper mapper,
            Clock clock) {
        this.events = events;
        this.eventTypes = eventTypes;
        this.scopeItems = scopeItems;
        this.participants = participants;
        this.alertLinks = alertLinks;
        this.campus = campus;
        this.identity = identity;
        this.lifecycle = lifecycle;
        this.stateService = stateService;
        this.audit = audit;
        this.checkIn = checkIn;
        this.brigade = brigade;
        this.alerts = alerts;
        this.accessPolicy = accessPolicy;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** What is known about an edit once the request has been validated. */
    private record Edit(
            EventEntity event,
            List<ScopeRef> scope,
            Set<Role> roles,
            EventStatePayload before,
            Instant oldStart,
            boolean changed,
            List<EventWarning> warnings,
            String actor,
            Set<Role> actorRoles) {

        AccessActor accessActor() {
            return new AccessActor(actor, actorRoles);
        }
    }

    @Override
    @Transactional
    public EventResponse update(UUID eventId, CreateEventRequest request, AccessActor actor) {
        EventEntity event = events.findForUpdate(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        EventStatus status = event.getStatus();
        if (status == EventStatus.FINISHED) {
            throw new BusinessException(ErrorCode.EVENT_NOT_EDITABLE, "A finished event cannot be edited");
        }
        accessPolicy.assertCanManage(actor, event.isSimulacro(), "edit");

        Set<EventTypeEntity> types = loadTypes(request.typeIds());
        boolean drill = types.stream().anyMatch(type -> EventTypeEntity.SIMULACRO_CODE.equals(type.getCode()));
        if (drill != event.isSimulacro()) {
            throw new InvalidFieldException("typeIds", "A drill cannot become a real event or the other way around");
        }
        checkStartDate(event, request.startDate());

        List<EventWarning> warnings = new ArrayList<>();
        List<ScopeRef> oldScope = currentScope(eventId);
        Set<Role> oldRoles = currentRoles(eventId);
        List<ScopeRef> scope = validatedScope(request.scope(), warnings).orElse(oldScope);
        Set<Role> roles = validatedRoles(request.participantRoles(), warnings).orElse(oldRoles);
        if (status == EventStatus.IN_PROGRESS && (scope.isEmpty() || roles.isEmpty())) {
            throw new InvalidFieldException(scope.isEmpty() ? "scope" : "participantRoles",
                    "A started event must keep its scope and its participants");
        }

        Edit edit = new Edit(
                event, scope, roles,
                payload(event, status, oldScope, oldRoles),
                event.getScheduledStart(),
                hasChanges(event, request, types, oldScope, scope, oldRoles, roles),
                warnings,
                actor.auditId(),
                actor.roles());
        if (edit.changed()) {
            audit.record(AuditRecordType.MODIFICATION, "EVENT_UPDATED", actor.auditId(), event, "Event edited");
        }

        return switch (status) {
            case DRAFT -> editDraft(edit, request, types);
            case SCHEDULED -> editScheduled(edit, request, types);
            case IN_PROGRESS -> editInProgress(edit, request, types);
            case FINISHED -> throw new BusinessException(ErrorCode.EVENT_NOT_EDITABLE, "A finished event cannot be edited");
        };
    }

    // ---------------------------------------------------------------- Draft

    private EventResponse editDraft(Edit edit, CreateEventRequest request, Set<EventTypeEntity> types) {
        apply(edit, request, types);
        if (isComplete(edit)) {
            SchedulingOutcome outcome = lifecycle.schedule(
                    edit.event(), edit.scope(), edit.roles(), edit.accessActor());
            edit.warnings().addAll(outcome.warnings());
        }
        return respond(edit);
    }

    // ------------------------------------------------------------ Scheduled

    private EventResponse editScheduled(Edit edit, CreateEventRequest request, Set<EventTypeEntity> types) {
        if (!edit.changed()) {
            return respond(edit);
        }
        apply(edit, request, types);
        if (isComplete(edit)) {
            syncScheduled(edit);
        } else {
            backToDraft(edit);
        }
        return respond(edit);
    }

    /** Still complete: CHI and BEI get the new data, the general alert follows the date, brigadiers are alerted. */
    private void syncScheduled(Edit edit) {
        EventEntity event = edit.event();
        EventStatePayload now = payload(event, EventStatus.SCHEDULED, edit.scope(), edit.roles());
        boolean dateChanged = !Objects.equals(edit.oldStart(), event.getScheduledStart());
        List<Runnable> undo = new ArrayList<>();
        try {
            checkIn.pushState(now);
            undo.add(() -> checkIn.pushState(edit.before()));
            brigade.pushState(now);
            undo.add(() -> brigade.pushState(edit.before()));
            if (dateChanged) {
                for (EventAlertEntity general : alertLinks.findByEventIdAndPurpose(event.getId(), EventAlertPurpose.GENERAL)) {
                    alerts.reschedule(general.getAlertId(), event.getScheduledStart(), alertActor(edit));
                    undo.add(() -> alerts.reschedule(general.getAlertId(), edit.oldStart(), alertActor(edit)));
                }
            }
            alertLinks.saveAll(List.of(createAlert(event, brigadiersAlert(event, edit), edit)));
        } catch (ExternalServiceUnavailableException ex) {
            runUndo(undo);
            throw ex;
        }
    }

    /**
     * The data is no longer complete: the general alert goes back to draft, CHI and BEI are told the event
     * is a draft, and the status changes. Alerts already sent (brigadiers) stay as they are.
     */
    private void backToDraft(Edit edit) {
        EventEntity event = edit.event();
        EventStatePayload draft = payload(event, EventStatus.DRAFT, edit.scope(), edit.roles());
        List<EventAlertEntity> general = alertLinks.findByEventIdAndPurpose(event.getId(), EventAlertPurpose.GENERAL);
        List<Runnable> undo = new ArrayList<>();
        try {
            for (EventAlertEntity link : general) {
                alerts.archive(link.getAlertId(), alertActor(edit));
                undo.add(() -> alerts.reschedule(link.getAlertId(), edit.oldStart(), alertActor(edit)));
            }
            checkIn.pushState(draft);
            undo.add(() -> checkIn.pushState(edit.before()));
            brigade.pushState(draft);
        } catch (ExternalServiceUnavailableException ex) {
            runUndo(undo);
            throw ex;
        }
        alertLinks.deleteAll(general);
        stateService.transition(event.getId(), EventStatus.DRAFT, edit.actor(), "Edited: data incomplete, back to draft");
    }

    // ----------------------------------------------------------- In progress

    private EventResponse editInProgress(Edit edit, CreateEventRequest request, Set<EventTypeEntity> types) {
        if (!edit.changed()) {
            return respond(edit);
        }
        apply(edit, request, types);
        EventEntity event = edit.event();
        EventStatePayload now = payload(event, EventStatus.IN_PROGRESS, edit.scope(), edit.roles());
        List<Runnable> undo = new ArrayList<>();
        try {
            checkIn.pushState(now);
            undo.add(() -> checkIn.pushState(edit.before()));
            brigade.pushState(now);
            undo.add(() -> brigade.pushState(edit.before()));
            EventAlertEntity forBrigadiers = createAlert(event, brigadiersAlert(event, edit), edit);
            EventAlertEntity forParticipants = createAlert(event, participantsAlert(event, edit), edit);
            alertLinks.saveAll(List.of(forBrigadiers, forParticipants));
        } catch (ExternalServiceUnavailableException ex) {
            runUndo(undo);
            throw ex;
        }
        return respond(edit);
    }

    // --------------------------------------------------------------- Helpers

    /**
     * Draft and Scheduled events need a future date. A Scheduled event may keep its current date even if it
     * is about to start. A started event cannot change its date.
     */
    private void checkStartDate(EventEntity event, Instant requested) {
        switch (event.getStatus()) {
            case DRAFT -> requireFuture(requested);
            case SCHEDULED -> {
                if (!Objects.equals(requested, event.getScheduledStart())) {
                    requireFuture(requested);
                }
            }
            case IN_PROGRESS -> {
                if (!Objects.equals(requested, event.getScheduledStart())) {
                    throw new InvalidFieldException("startDate", "The start date of a started event cannot change");
                }
            }
            default -> {
            }
        }
    }

    private void requireFuture(Instant requested) {
        if (requested != null && !requested.isAfter(clock.instant())) {
            throw new InvalidFieldException("startDate", "The start date must be in the future");
        }
    }

    /** Loads the requested types; a type that does not exist or is not active is rejected. */
    private Set<EventTypeEntity> loadTypes(Set<UUID> typeIds) {
        List<EventTypeEntity> found = eventTypes.findByIdIn(typeIds);
        Set<UUID> usable = new LinkedHashSet<>();
        found.stream().filter(EventTypeEntity::isActive).forEach(type -> usable.add(type.getId()));
        List<String> invalid = typeIds.stream().filter(id -> !usable.contains(id)).map(UUID::toString).sorted().toList();
        if (!invalid.isEmpty()) {
            throw new InvalidReferenceException("typeIds", invalid);
        }
        return new LinkedHashSet<>(found);
    }

    /** Validates the scope with CAM. Empty result: CAM is down, a warning is added and the caller keeps the current scope. */
    private Optional<List<ScopeRef>> validatedScope(List<ScopeItemRequest> requested, List<EventWarning> warnings) {
        Set<ScopeRef> unique = new LinkedHashSet<>();
        if (requested != null) {
            requested.forEach(item -> unique.add(new ScopeRef(item.type(), item.id())));
        }
        if (unique.isEmpty()) {
            return Optional.of(List.of());
        }
        Map<ScopeType, List<String>> byType = new EnumMap<>(ScopeType.class);
        unique.forEach(ref -> byType.computeIfAbsent(ref.type(), type -> new ArrayList<>()).add(ref.id()));
        try {
            byType.forEach(campus::validateScope);
        } catch (ExternalServiceUnavailableException ex) {
            warnings.add(new EventWarning("SCOPE_PENDING",
                    "CAM is unavailable: the scope changes were not saved. Send them again later."));
            return Optional.empty();
        }
        return Optional.of(List.copyOf(unique));
    }

    /**
     * Validates the roles: only community roles can be invited.
     * Empty result: AUT is down, a warning is added and the caller keeps the current roles.
     */
    private Optional<Set<Role>> validatedRoles(Set<Role> requested, List<EventWarning> warnings) {
        if (requested == null || requested.isEmpty()) {
            return Optional.of(Set.of());
        }
        List<String> notInvitable = requested.stream()
                .filter(role -> !role.isCommunity())
                .map(Role::name)
                .sorted()
                .toList();
        if (!notInvitable.isEmpty()) {
            throw new InvalidReferenceException("participantRoles", notInvitable);
        }
        Set<Role> roles = EnumSet.copyOf(requested);
        try {
            identity.validateRoles(roles);
        } catch (ExternalServiceUnavailableException ex) {
            warnings.add(new EventWarning("PARTICIPANTS_PENDING",
                    "AUT is unavailable: the participant changes were not saved. Send them again later."));
            return Optional.empty();
        }
        return Optional.of(roles);
    }

    private boolean isComplete(Edit edit) {
        return edit.event().getScheduledStart() != null && !edit.scope().isEmpty() && !edit.roles().isEmpty();
    }

    private boolean hasChanges(
            EventEntity event, CreateEventRequest request, Set<EventTypeEntity> types,
            List<ScopeRef> oldScope, List<ScopeRef> scope, Set<Role> oldRoles, Set<Role> roles) {
        Set<UUID> oldTypeIds = event.getTypes().stream().map(EventTypeEntity::getId).collect(Collectors.toSet());
        Set<UUID> newTypeIds = types.stream().map(EventTypeEntity::getId).collect(Collectors.toSet());
        return !Objects.equals(event.getName(), request.name().strip())
                || !Objects.equals(blankToNull(event.getDescription()), blankToNull(request.description()))
                || !oldTypeIds.equals(newTypeIds)
                || !Objects.equals(event.getScheduledStart(), request.startDate())
                || !new HashSet<>(oldScope).equals(new HashSet<>(scope))
                || !oldRoles.equals(roles);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Writes the new data: event fields and, comparing with what is stored, the scope and the roles. */
    private void apply(Edit edit, CreateEventRequest request, Set<EventTypeEntity> types) {
        EventEntity event = edit.event();
        event.setName(request.name().strip());
        event.setDescription(request.description());
        event.setTypes(types);
        event.setScheduledStart(request.startDate());
        events.save(event);
        syncScope(event.getId(), edit.scope());
        syncRoles(event.getId(), edit.roles());
    }

    /** Removes the scope items that are no longer wanted and adds the new ones (no delete-then-insert of the same row). */
    private void syncScope(UUID eventId, List<ScopeRef> wanted) {
        List<EventScopeItemEntity> stored = scopeItems.findByEventId(eventId);
        Set<ScopeRef> wantedSet = new HashSet<>(wanted);
        Set<ScopeRef> storedRefs = stored.stream()
                .map(item -> new ScopeRef(item.getScopeType(), item.getScopeRefId()))
                .collect(Collectors.toSet());
        scopeItems.deleteAll(stored.stream()
                .filter(item -> !wantedSet.contains(new ScopeRef(item.getScopeType(), item.getScopeRefId())))
                .toList());
        scopeItems.saveAll(wanted.stream().filter(ref -> !storedRefs.contains(ref)).map(ref -> {
            EventScopeItemEntity item = new EventScopeItemEntity();
            item.setEventId(eventId);
            item.setScopeType(ref.type());
            item.setScopeRefId(ref.id());
            return item;
        }).toList());
    }

    private void syncRoles(UUID eventId, Set<Role> wanted) {
        List<EventParticipantEntity> stored = participants.findByEventId(eventId);
        Set<Role> storedRoles = stored.stream().map(EventParticipantEntity::getRole).collect(Collectors.toSet());
        participants.deleteAll(stored.stream().filter(item -> !wanted.contains(item.getRole())).toList());
        participants.saveAll(wanted.stream().filter(role -> !storedRoles.contains(role)).map(role -> {
            EventParticipantEntity item = new EventParticipantEntity();
            item.setEventId(eventId);
            item.setRole(role);
            return item;
        }).toList());
    }

    private List<ScopeRef> currentScope(UUID eventId) {
        return scopeItems.findByEventId(eventId).stream()
                .map(item -> new ScopeRef(item.getScopeType(), item.getScopeRefId()))
                .toList();
    }

    private Set<Role> currentRoles(UUID eventId) {
        Set<Role> roles = participants.findByEventId(eventId).stream()
                .map(EventParticipantEntity::getRole)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
        return Collections.unmodifiableSet(roles);
    }

    private EventResponse respond(Edit edit) {
        EventEntity event = edit.event();
        return mapper.toResponse(
                event, edit.scope(), edit.roles(), alertLinks.findByEventId(event.getId()), edit.warnings());
    }

    private EventStatePayload payload(EventEntity event, EventStatus status, List<ScopeRef> scope, Set<Role> roles) {
        return new EventStatePayload(
                event.getId(), event.getName(), event.isSimulacro(), status,
                event.getScheduledStart(), event.getEndedAt(), scope, roles);
    }

    /** Asks ALE for an alert and returns the link to save. */
    private EventAlertEntity createAlert(EventEntity event, AlertCommand command, Edit edit) {
        EventAlertEntity link = new EventAlertEntity();
        link.setEventId(event.getId());
        link.setAlertId(alerts.createAlert(command, alertActor(edit)));
        link.setPurpose(command.purpose());
        return link;
    }

    private AlertActor alertActor(Edit edit) {
        return AlertActor.from(edit.accessActor());
    }

    /** Alert for brigadiers: check the assignment after the changes. It is sent right away. */
    private AlertCommand brigadiersAlert(EventEntity event, Edit edit) {
        return new AlertCommand(
                event.getId(), EventAlertPurpose.EDIT, event.isSimulacro(),
                BRIGADIERS_TITLE + event.getName(), BRIGADIERS_TEXT,
                edit.scope(), Set.of(Role.BRIGADISTA), null, true,
                AlertTypes.from(event.getTypes()));
    }

    /** Alert for participants of a started event: the information changed. It is sent right away. */
    private AlertCommand participantsAlert(EventEntity event, Edit edit) {
        return new AlertCommand(
                event.getId(), EventAlertPurpose.EDIT, event.isSimulacro(),
                PARTICIPANTS_TITLE + event.getName(), PARTICIPANTS_TEXT,
                edit.scope(), edit.roles(), null, true,
                AlertTypes.from(event.getTypes()));
    }

    /** Runs the undo actions, last one first. A failure is only logged: the original error is the one that matters. */
    private void runUndo(List<Runnable> undo) {
        for (int i = undo.size() - 1; i >= 0; i--) {
            try {
                undo.get(i).run();
            } catch (RuntimeException ex) {
                log.warn("Could not undo a call to another service", ex);
            }
        }
    }
}
