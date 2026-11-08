package edu.escuelaing.campusseguro.events.service;

import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.client.campus.CampusGateway;
import edu.escuelaing.campusseguro.events.client.identity.IdentityGateway;
import edu.escuelaing.campusseguro.events.dto.CreateEventRequest;
import edu.escuelaing.campusseguro.events.dto.EventMapper;
import edu.escuelaing.campusseguro.events.dto.EventResponse;
import edu.escuelaing.campusseguro.events.dto.EventWarning;
import edu.escuelaing.campusseguro.events.dto.ScopeItemRequest;
import edu.escuelaing.campusseguro.events.entity.EventAlertEntity;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.EventParticipantEntity;
import edu.escuelaing.campusseguro.events.entity.EventScopeItemEntity;
import edu.escuelaing.campusseguro.events.entity.EventTypeEntity;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.exception.InvalidFieldException;
import edu.escuelaing.campusseguro.events.exception.InvalidReferenceException;
import edu.escuelaing.campusseguro.events.repository.EventParticipantRepository;
import edu.escuelaing.campusseguro.events.repository.EventRepository;
import edu.escuelaing.campusseguro.events.repository.EventScopeItemRepository;
import edu.escuelaing.campusseguro.events.repository.EventTypeRepository;
import edu.escuelaing.campusseguro.events.security.AccessActor;
import edu.escuelaing.campusseguro.events.security.Role;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates an event (RF-EVE-01).
 *
 * <p>Who can create: the coordinator creates real events and the organizer creates drills
 * (an event with the SIMULACRO type). The event is Scheduled only if it has a future start date,
 * a scope and participants; otherwise it is Draft. If CAM or AUT cannot validate the scope or the
 * roles, those parts are left pending and the event stays Draft.
 */
@Service
public class EventCreationService {

    private final EventRepository events;
    private final EventTypeRepository eventTypes;
    private final EventScopeItemRepository scopeItems;
    private final EventParticipantRepository participants;
    private final EventStateService stateService;
    private final EventSchedulingService scheduling;
    private final CampusGateway campus;
    private final IdentityGateway identity;
    private final Clock clock;

    public EventCreationService(
            EventRepository events,
            EventTypeRepository eventTypes,
            EventScopeItemRepository scopeItems,
            EventParticipantRepository participants,
            EventStateService stateService,
            EventSchedulingService scheduling,
            CampusGateway campus,
            IdentityGateway identity,
            Clock clock) {
        this.events = events;
        this.eventTypes = eventTypes;
        this.scopeItems = scopeItems;
        this.participants = participants;
        this.stateService = stateService;
        this.scheduling = scheduling;
        this.campus = campus;
        this.identity = identity;
        this.clock = clock;
    }

    /**
     * Creates the event.
     *
     * @param request data of the event
     * @param actor   user who creates it
     * @return the saved event, with warnings if something stayed pending
     * @throws AccessDeniedException               if the role does not match the kind of event
     * @throws InvalidReferenceException           if a type, building, zone or role is unknown
     * @throws InvalidFieldException               if the start date is not in the future
     * @throws ExternalServiceUnavailableException if CHI is down and the event had to be scheduled
     */
    @Transactional
    public EventResponse create(CreateEventRequest request, AccessActor actor) {
        Set<EventTypeEntity> types = loadTypes(request.typeIds());
        boolean simulacro = types.stream().anyMatch(type -> EventTypeEntity.SIMULACRO_CODE.equals(type.getCode()));
        assertMayCreate(actor, simulacro);
        if (request.startDate() != null && !request.startDate().isAfter(clock.instant())) {
            throw new InvalidFieldException("startDate", "The start date must be in the future");
        }

        List<EventWarning> warnings = new ArrayList<>();
        List<ScopeRef> scope = validatedScope(request.scope(), warnings);
        Set<Role> roles = validatedRoles(request.participantRoles(), warnings);

        EventEntity event = saveEvent(request, types, simulacro, actor);
        saveScope(event, scope);
        saveRoles(event, roles);
        stateService.recordCreation(event, actor.auditId());

        List<EventAlertEntity> alerts = List.of();
        if (request.startDate() != null && !scope.isEmpty() && !roles.isEmpty()) {
            SchedulingOutcome outcome = scheduling.schedule(event, scope, roles, actor.auditId());
            warnings.addAll(outcome.warnings());
            alerts = outcome.alerts();
        }
        return EventMapper.toResponse(event, scope, roles, alerts, warnings);
    }

    /** The coordinator creates real events and the organizer creates drills. */
    private void assertMayCreate(AccessActor actor, boolean simulacro) {
        Role required = simulacro ? Role.ORGANIZADOR : Role.COORDINADOR;
        if (!actor.hasRole(required)) {
            throw new AccessDeniedException("Only the " + required + " can create this kind of event");
        }
    }

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

    /** Validates the scope with CAM. If CAM is down the scope is left pending (empty). */
    private List<ScopeRef> validatedScope(List<ScopeItemRequest> requested, List<EventWarning> warnings) {
        Set<ScopeRef> unique = new LinkedHashSet<>();
        if (requested != null) {
            requested.forEach(item -> unique.add(new ScopeRef(item.type(), item.id())));
        }
        if (unique.isEmpty()) {
            return List.of();
        }
        Map<ScopeType, List<String>> byType = new EnumMap<>(ScopeType.class);
        unique.forEach(ref -> byType.computeIfAbsent(ref.type(), type -> new ArrayList<>()).add(ref.id()));
        try {
            byType.forEach(campus::validateScope);
        } catch (ExternalServiceUnavailableException ex) {
            warnings.add(new EventWarning("SCOPE_PENDING",
                    "CAM is unavailable: the scope was not saved. Add it later to schedule the event."));
            return List.of();
        }
        return List.copyOf(unique);
    }

    /** Validates the roles: only community roles can be invited. If AUT is down the roles are left pending. */
    private Set<Role> validatedRoles(Set<Role> requested, List<EventWarning> warnings) {
        if (requested == null || requested.isEmpty()) {
            return Set.of();
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
                    "AUT is unavailable: the participants were not saved. Add them later to schedule the event."));
            return Set.of();
        }
        return roles;
    }

    private EventEntity saveEvent(CreateEventRequest request, Set<EventTypeEntity> types, boolean simulacro,
            AccessActor actor) {
        EventEntity event = new EventEntity();
        event.setName(request.name().strip());
        event.setDescription(request.description());
        event.setStatus(EventStatus.DRAFT);
        event.setSimulacro(simulacro);
        event.setScheduledStart(request.startDate());
        event.setCreatedBy(actor.auditId());
        event.setTypes(types);
        return events.save(event);
    }

    private void saveScope(EventEntity event, Collection<ScopeRef> scope) {
        List<EventScopeItemEntity> items = scope.stream().map(ref -> {
            EventScopeItemEntity item = new EventScopeItemEntity();
            item.setEventId(event.getId());
            item.setScopeType(ref.type());
            item.setScopeRefId(ref.id());
            return item;
        }).toList();
        scopeItems.saveAll(items);
    }

    private void saveRoles(EventEntity event, Collection<Role> roles) {
        List<EventParticipantEntity> items = roles.stream().map(role -> {
            EventParticipantEntity item = new EventParticipantEntity();
            item.setEventId(event.getId());
            item.setRole(role);
            return item;
        }).toList();
        participants.saveAll(items);
    }
}
