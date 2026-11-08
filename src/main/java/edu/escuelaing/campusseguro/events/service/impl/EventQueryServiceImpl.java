package edu.escuelaing.campusseguro.events.service.impl;

import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.client.brigade.BrigadeGateway;
import edu.escuelaing.campusseguro.events.client.brigade.IncidentIndicators;
import edu.escuelaing.campusseguro.events.client.checkin.CheckInGateway;
import edu.escuelaing.campusseguro.events.client.checkin.CheckInIndicators;
import edu.escuelaing.campusseguro.events.dto.response.EvaluationResponse;
import edu.escuelaing.campusseguro.events.dto.response.EventDetailResponse;
import edu.escuelaing.campusseguro.events.dto.response.EventMonitoringResponse;
import edu.escuelaing.campusseguro.events.dto.response.EventResponse;
import edu.escuelaing.campusseguro.events.dto.response.EventSummaryResponse;
import edu.escuelaing.campusseguro.events.dto.response.EventWarning;
import edu.escuelaing.campusseguro.events.dto.response.PageResponse;
import edu.escuelaing.campusseguro.events.entity.EventBrigadierEntity;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.enums.BrigadierStatus;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.exception.InvalidFieldException;
import edu.escuelaing.campusseguro.events.exception.ResourceNotFoundException;
import edu.escuelaing.campusseguro.events.mapper.EventMapper;
import edu.escuelaing.campusseguro.events.repository.EventAlertRepository;
import edu.escuelaing.campusseguro.events.repository.EventBrigadierRepository;
import edu.escuelaing.campusseguro.events.repository.EventParticipantRepository;
import edu.escuelaing.campusseguro.events.repository.EventRepository;
import edu.escuelaing.campusseguro.events.repository.EventScopeItemRepository;
import edu.escuelaing.campusseguro.events.security.AccessActor;
import edu.escuelaing.campusseguro.events.security.EventAccessPolicy;
import edu.escuelaing.campusseguro.events.security.Role;
import edu.escuelaing.campusseguro.events.service.EventEvaluationService;
import edu.escuelaing.campusseguro.events.service.EventQueryService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Default implementation of {@link EventQueryService}.
 *
 * <p>The database reads run inside a transaction; the calls to CHI and BEI run after it, so no
 * database connection stays open while waiting for another service.
 */
@Service
public class EventQueryServiceImpl implements EventQueryService {

    private static final Logger log = LoggerFactory.getLogger(EventQueryServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final EventRepository events;
    private final EventScopeItemRepository scopeItems;
    private final EventParticipantRepository participants;
    private final EventAlertRepository alertLinks;
    private final EventBrigadierRepository brigadiers;
    private final EventEvaluationService evaluation;
    private final CheckInGateway checkIn;
    private final BrigadeGateway brigade;
    private final EventMapper mapper;
    private final EventAccessPolicy accessPolicy;
    private final TransactionTemplate tx;

    public EventQueryServiceImpl(
            EventRepository events,
            EventScopeItemRepository scopeItems,
            EventParticipantRepository participants,
            EventAlertRepository alertLinks,
            EventBrigadierRepository brigadiers,
            EventEvaluationService evaluation,
            CheckInGateway checkIn,
            BrigadeGateway brigade,
            EventMapper mapper,
            EventAccessPolicy accessPolicy,
            TransactionTemplate tx) {
        this.events = events;
        this.scopeItems = scopeItems;
        this.participants = participants;
        this.alertLinks = alertLinks;
        this.brigadiers = brigadiers;
        this.evaluation = evaluation;
        this.checkIn = checkIn;
        this.brigade = brigade;
        this.mapper = mapper;
        this.accessPolicy = accessPolicy;
        this.tx = tx;
    }

    @Override
    public PageResponse<EventSummaryResponse> search(String title, EventStatus status, int page, int size) {
        if (page < 0) {
            throw new InvalidFieldException("page", "The page number cannot be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidFieldException("size", "The page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        Collection<EventStatus> statuses = status == null ? EnumSet.allOf(EventStatus.class) : Set.of(status);
        PageRequest request = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return tx.execute(state -> {
            Page<EventEntity> found = events.search(likePattern(title), statuses, request);
            List<EventSummaryResponse> content = found.getContent().stream().map(this::summary).toList();
            return new PageResponse<>(content, page, size, found.getTotalElements(), found.getTotalPages());
        });
    }

    @Override
    public EventDetailResponse detail(UUID eventId) {
        Detail base = tx.execute(state -> {
            EventEntity event = events.findById(eventId)
                    .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
            List<ScopeRef> scope = scope(eventId);
            Set<Role> roles = roles(eventId);
            EventResponse response = mapper.toResponse(
                    event, scope, roles, alertLinks.findByEventId(eventId), List.of());
            return new Detail(response, brigadiers.findByEventId(eventId));
        });
        EventStatus status = base.event().status();
        EventMonitoringResponse monitoring = status == EventStatus.IN_PROGRESS
                ? monitor(eventId, base.brigadiers()) : null;
        EvaluationResponse saved = status == EventStatus.FINISHED
                ? evaluation.find(eventId).orElse(null) : null;
        return new EventDetailResponse(base.event(), monitoring, saved);
    }

    @Override
    public List<EventSummaryResponse> active(AccessActor actor) {
        return tx.execute(state -> events.findByStatusOrderByStartedAtDesc(EventStatus.IN_PROGRESS).stream()
                .filter(event -> accessPolicy.canManage(actor, event.isSimulacro())
                        || concerns(actor, roles(event.getId())))
                .map(this::summary)
                .toList());
    }

    /** Real-time indicators. A module that does not answer leaves its part empty and adds a message. */
    private EventMonitoringResponse monitor(UUID eventId, List<EventBrigadierEntity> savedBrigadiers) {
        List<EventWarning> messages = new ArrayList<>();

        EventMonitoringResponse.CheckInProgress checkInProgress = null;
        try {
            CheckInIndicators numbers = checkIn.indicators(eventId);
            checkInProgress = new EventMonitoringResponse.CheckInProgress(
                    numbers.updatedAt(), numbers.totalCheckins(),
                    numbers.byStatus() == null ? Map.of() : numbers.byStatus(), numbers.participationPct());
            if (numbers.totalCheckins() == 0) {
                messages.add(new EventWarning("NO_CHECKINS", "No check-ins have been registered yet."));
            }
        } catch (ExternalServiceUnavailableException ex) {
            log.warn("CHI did not answer when the monitoring of event {} was requested", eventId);
            messages.add(new EventWarning("CHECKIN_UNAVAILABLE",
                    "Check-in monitoring is currently unavailable because CHI does not answer."));
        }

        EventMonitoringResponse.IncidentProgress incidentProgress = null;
        try {
            IncidentIndicators numbers = brigade.indicators(eventId);
            incidentProgress = new EventMonitoringResponse.IncidentProgress(
                    numbers.updatedAt(), numbers.totalIncidents(), numbers.casesAttended(),
                    numbers.casesResolved(), Math.max(0, numbers.totalIncidents() - numbers.casesAttended()),
                    numbers.avgAttentionSeconds());
            if (numbers.totalIncidents() == 0) {
                messages.add(new EventWarning("NO_INCIDENTS", "No incidents have been registered yet."));
            }
        } catch (ExternalServiceUnavailableException ex) {
            log.warn("BEI did not answer when the monitoring of event {} was requested", eventId);
            messages.add(new EventWarning("INCIDENTS_UNAVAILABLE",
                    "Incident monitoring is currently unavailable because BEI does not answer."));
        }

        if (savedBrigadiers.isEmpty()) {
            messages.add(new EventWarning("NO_BRIGADIERS", "No brigadiers have been registered yet."));
        }
        Instant syncedAt = savedBrigadiers.stream()
                .map(EventBrigadierEntity::getSyncedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new EventMonitoringResponse(
                checkInProgress, incidentProgress, byZone(savedBrigadiers), syncedAt, messages);
    }

    /** Groups the brigadiers by zone; zones are sorted by id and the ones without zone go last. */
    private List<EventMonitoringResponse.ZoneBrigadiers> byZone(List<EventBrigadierEntity> rows) {
        Map<String, List<EventBrigadierEntity>> grouped = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> row.getZoneId() == null ? "" : row.getZoneId(),
                        LinkedHashMap::new, Collectors.toList()));
        return grouped.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, List<EventBrigadierEntity>> entry) ->
                        entry.getKey().isEmpty()).thenComparing(Map.Entry::getKey))
                .map(entry -> {
                    List<EventMonitoringResponse.Brigadier> list = entry.getValue().stream()
                            .sorted(Comparator.comparing(row -> Objects.toString(row.getName(), "")))
                            .map(row -> new EventMonitoringResponse.Brigadier(
                                    row.getBrigadierId(), row.getName(), row.getStatus() == BrigadierStatus.ACTIVE))
                            .toList();
                    long active = list.stream().filter(EventMonitoringResponse.Brigadier::active).count();
                    return new EventMonitoringResponse.ZoneBrigadiers(
                            entry.getKey().isEmpty() ? null : entry.getKey(), active, list);
                })
                .toList();
    }

    private EventSummaryResponse summary(EventEntity event) {
        return mapper.toSummary(event, scope(event.getId()), roles(event.getId()));
    }

    private List<ScopeRef> scope(UUID eventId) {
        return scopeItems.findByEventId(eventId).stream()
                .map(item -> new ScopeRef(item.getScopeType(), item.getScopeRefId()))
                .toList();
    }

    private Set<Role> roles(UUID eventId) {
        return participants.findByEventId(eventId).stream()
                .map(row -> row.getRole())
                .collect(Collectors.toSet());
    }

    private boolean concerns(AccessActor actor, Set<Role> invited) {
        return actor.roles().stream().anyMatch(invited::contains);
    }

    /** Lower-case LIKE pattern that finds the text anywhere in the name; {@code !} escapes % and _. */
    private String likePattern(String title) {
        if (title == null || title.isBlank()) {
            return "%";
        }
        String escaped = title.trim().toLowerCase()
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    /** What is read inside the transaction. */
    private record Detail(EventResponse event, List<EventBrigadierEntity> brigadiers) {
    }
}
