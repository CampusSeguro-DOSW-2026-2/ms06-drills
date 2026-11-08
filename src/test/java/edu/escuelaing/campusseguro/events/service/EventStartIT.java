package edu.escuelaing.campusseguro.events.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.escuelaing.campusseguro.events.client.alert.StubAlertGateway;
import edu.escuelaing.campusseguro.events.client.brigade.StubBrigadeGateway;
import edu.escuelaing.campusseguro.events.client.checkin.StubCheckInGateway;
import edu.escuelaing.campusseguro.events.dto.request.CreateEventRequest;
import edu.escuelaing.campusseguro.events.dto.request.ScopeItemRequest;
import edu.escuelaing.campusseguro.events.dto.response.EventResponse;
import edu.escuelaing.campusseguro.events.entity.EventAlertEntity;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.EventStateHistoryEntity;
import edu.escuelaing.campusseguro.events.entity.enums.EventAlertPurpose;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.repository.EventAlertRepository;
import edu.escuelaing.campusseguro.events.repository.EventRepository;
import edu.escuelaing.campusseguro.events.repository.EventStartFailureRepository;
import edu.escuelaing.campusseguro.events.repository.EventTypeRepository;
import edu.escuelaing.campusseguro.events.repository.PostgresIntegrationTest;
import edu.escuelaing.campusseguro.events.security.AccessActor;
import edu.escuelaing.campusseguro.events.security.Role;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Starts events against a real database with the stub gateways of the local profile. */
class EventStartIT extends PostgresIntegrationTest {

    @Autowired
    private EventCreationService creation;
    @Autowired
    private EventLifecycleService lifecycle;
    @Autowired
    private EventStateService stateService;
    @Autowired
    private EventRepository events;
    @Autowired
    private EventTypeRepository types;
    @Autowired
    private EventAlertRepository alertLinks;
    @Autowired
    private EventStartFailureRepository failures;
    @Autowired
    private StubAlertGateway alertStub;
    @Autowired
    private StubCheckInGateway checkInStub;
    @Autowired
    private StubBrigadeGateway brigadeStub;

    private final AccessActor coordinator = new AccessActor("coord-it", Set.of(Role.COORDINADOR));

    private UUID scheduledEvent(String name) {
        CreateEventRequest request = new CreateEventRequest(
                name, null, Set.of(types.findByCode("SISMO").orElseThrow().getId()),
                Instant.now().plus(2, ChronoUnit.DAYS),
                List.of(new ScopeItemRequest(ScopeType.ZONE, "z1")), Set.of(Role.ESTUDIANTE));
        EventResponse response = creation.create(request, coordinator);
        assertEquals(EventStatus.SCHEDULED, response.status());
        return response.id();
    }

    /** Moves the start date to the past, as if the time had passed. */
    private void makeDue(UUID eventId) {
        EventEntity event = events.findById(eventId).orElseThrow();
        event.setScheduledStart(Instant.now().minusSeconds(30));
        events.saveAndFlush(event);
    }

    @Test
    void aDueEventStartsAndEveryServiceIsTold() {
        UUID eventId = scheduledEvent("Start IT");
        makeDue(eventId);

        assertTrue(lifecycle.startDueEvents() >= 1);

        EventEntity started = events.findById(eventId).orElseThrow();
        assertEquals(EventStatus.IN_PROGRESS, started.getStatus());
        assertNotNull(started.getStartedAt());
        assertEquals(EventStatus.IN_PROGRESS, checkInStub.lastState(eventId).orElseThrow().status());
        assertEquals(EventStatus.IN_PROGRESS, brigadeStub.lastState(eventId).orElseThrow().status());
        EventAlertEntity general = alertLinks.findByEventIdAndPurpose(eventId, EventAlertPurpose.GENERAL).get(0);
        assertTrue(alertStub.activated().contains(general.getAlertId()));
        assertTrue(failures.findByEventIdAndResolvedAtIsNull(eventId).isEmpty());

        List<EventStateHistoryEntity> history = stateService.history(eventId);
        EventStateHistoryEntity last = history.stream()
                .filter(entry -> entry.getToStatus() == EventStatus.IN_PROGRESS).findFirst().orElseThrow();
        assertEquals("system", last.getChangedBy());
    }

    @Test
    void anEventWhoseDateHasNotArrivedStaysScheduled() {
        UUID eventId = scheduledEvent("Not yet IT");

        lifecycle.startDueEvents();

        assertEquals(EventStatus.SCHEDULED, events.findById(eventId).orElseThrow().getStatus());
    }

    @Test
    void anEventIsStartedOnlyOnce() {
        UUID eventId = scheduledEvent("Once IT");
        makeDue(eventId);

        lifecycle.startDueEvents();
        lifecycle.startDueEvents();

        long starts = stateService.history(eventId).stream()
                .filter(entry -> entry.getToStatus() == EventStatus.IN_PROGRESS).count();
        assertEquals(1, starts);
    }
}
