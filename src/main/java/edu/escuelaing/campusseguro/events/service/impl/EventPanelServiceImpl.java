package edu.escuelaing.campusseguro.events.service.impl;

import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.client.campus.CampusGateway;
import edu.escuelaing.campusseguro.events.client.campus.ScopeRoute;
import edu.escuelaing.campusseguro.events.dto.response.EventPanelResponse;
import edu.escuelaing.campusseguro.events.dto.response.EventWarning;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.EventStartFailureEntity;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.entity.enums.ExternalModule;
import edu.escuelaing.campusseguro.events.exception.BusinessException;
import edu.escuelaing.campusseguro.events.exception.ErrorCode;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.exception.ResourceNotFoundException;
import edu.escuelaing.campusseguro.events.repository.EventRepository;
import edu.escuelaing.campusseguro.events.repository.EventScopeItemRepository;
import edu.escuelaing.campusseguro.events.repository.EventStartFailureRepository;
import edu.escuelaing.campusseguro.events.security.AccessActor;
import edu.escuelaing.campusseguro.events.security.EventAccessPolicy;
import edu.escuelaing.campusseguro.events.service.EventPanelService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default implementation of {@link EventPanelService}. */
@Service
public class EventPanelServiceImpl implements EventPanelService {

    private static final Logger log = LoggerFactory.getLogger(EventPanelServiceImpl.class);

    private final EventRepository events;
    private final EventScopeItemRepository scopeItems;
    private final EventStartFailureRepository failures;
    private final CampusGateway campus;
    private final EventAccessPolicy accessPolicy;

    public EventPanelServiceImpl(
            EventRepository events,
            EventScopeItemRepository scopeItems,
            EventStartFailureRepository failures,
            CampusGateway campus,
            EventAccessPolicy accessPolicy) {
        this.events = events;
        this.scopeItems = scopeItems;
        this.failures = failures;
        this.campus = campus;
        this.accessPolicy = accessPolicy;
    }

    @Override
    @Transactional(readOnly = true)
    public EventPanelResponse panel(UUID eventId, AccessActor actor) {
        EventEntity event = events.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        if (event.getStatus() != EventStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.EVENT_NOT_ACTIVE,
                    "The event panel is only available while the event is in progress");
        }

        List<EventWarning> warnings = new ArrayList<>();
        List<EventPanelResponse.Route> routes = loadRoutes(eventId, warnings);
        boolean routesAvailable = routes != null;

        List<EventStartFailureEntity> pending = failures.findByEventIdAndResolvedAtIsNull(eventId);
        boolean checkInEnabled = pending.stream().noneMatch(row -> row.getModule() == ExternalModule.CHI);
        boolean incidentsEnabled = pending.stream().noneMatch(row -> row.getModule() == ExternalModule.BEI);
        if (accessPolicy.canManage(actor, event.isSimulacro())) {
            pending.forEach(row -> warnings.add(startWarning(row.getModule())));
        }

        return new EventPanelResponse(
                event.getId(), event.getName(), event.isSimulacro(), event.getStartedAt(),
                routesAvailable ? routes : List.of(), routesAvailable,
                checkInEnabled, incidentsEnabled, warnings);
    }

    /** Routes from CAM, or {@code null} (and a warning) if CAM does not answer. */
    private List<EventPanelResponse.Route> loadRoutes(UUID eventId, List<EventWarning> warnings) {
        List<ScopeRef> scope = scopeItems.findByEventId(eventId).stream()
                .map(item -> new ScopeRef(item.getScopeType(), item.getScopeRefId()))
                .toList();
        try {
            return campus.routes(scope).stream().map(this::toRoute).toList();
        } catch (ExternalServiceUnavailableException ex) {
            log.warn("CAM did not answer when the panel of event {} was requested", eventId);
            warnings.add(new EventWarning("ROUTES_UNAVAILABLE",
                    "The route maps are not available because CAM is unavailable."));
            return null;
        }
    }

    private EventPanelResponse.Route toRoute(ScopeRoute route) {
        return new EventPanelResponse.Route(route.type(), route.refId(), route.name(), route.url());
    }

    private EventWarning startWarning(ExternalModule module) {
        String effect = switch (module) {
            case CHI -> "The check-in is not enabled yet.";
            case BEI -> "The brigadier panels and the incident registration are not enabled yet.";
            case ALE -> "The start alert has not been sent yet.";
        };
        return new EventWarning("START_PENDING_" + module.name(),
                module + " did not answer when the event started. " + effect + " The system keeps trying.");
    }
}
