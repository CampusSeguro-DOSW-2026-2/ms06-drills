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
import edu.escuelaing.campusseguro.events.entity.enums.EventAlertPurpose;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.repository.EventAlertRepository;
import edu.escuelaing.campusseguro.events.security.Role;
import edu.escuelaing.campusseguro.events.service.EventLifecycleService;
import edu.escuelaing.campusseguro.events.service.EventStateService;
import edu.escuelaing.campusseguro.events.service.SchedulingOutcome;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default implementation of {@link EventLifecycleService}. */
@Service
public class EventLifecycleServiceImpl implements EventLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(EventLifecycleServiceImpl.class);

    private static final String BRIGADIERS_TITLE = "Convocatoria de brigadistas: ";
    private static final String BRIGADIERS_TEXT =
            "Si es posible, activa tu estado y selecciona la zona que atenderás en este evento.";
    private static final String START_TITLE = "Inicio de evento: ";
    private static final String START_TEXT = "El evento ha comenzado. Sigue las indicaciones de los brigadistas.";

    private final CheckInGateway checkIn;
    private final BrigadeGateway brigade;
    private final AlertGateway alerts;
    private final EventAlertRepository alertLinks;
    private final EventStateService stateService;

    public EventLifecycleServiceImpl(
            CheckInGateway checkIn,
            BrigadeGateway brigade,
            AlertGateway alerts,
            EventAlertRepository alertLinks,
            EventStateService stateService) {
        this.checkIn = checkIn;
        this.brigade = brigade;
        this.alerts = alerts;
        this.alertLinks = alertLinks;
        this.stateService = stateService;
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
