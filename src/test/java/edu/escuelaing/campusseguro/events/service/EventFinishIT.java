package edu.escuelaing.campusseguro.events.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.escuelaing.campusseguro.events.client.alert.StubAlertGateway;
import edu.escuelaing.campusseguro.events.client.brigade.BrigadierInfo;
import edu.escuelaing.campusseguro.events.client.brigade.StubBrigadeGateway;
import edu.escuelaing.campusseguro.events.client.checkin.StubCheckInGateway;
import edu.escuelaing.campusseguro.events.dto.request.CreateEventRequest;
import edu.escuelaing.campusseguro.events.dto.request.ScopeItemRequest;
import edu.escuelaing.campusseguro.events.dto.response.EventFinishResponse;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.EventStateHistoryEntity;
import edu.escuelaing.campusseguro.events.entity.enums.EventAlertPurpose;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.exception.InvalidStateTransitionException;
import edu.escuelaing.campusseguro.events.repository.EventAlertRepository;
import edu.escuelaing.campusseguro.events.repository.EventBrigadierRepository;
import edu.escuelaing.campusseguro.events.repository.EventEvaluationRepository;
import edu.escuelaing.campusseguro.events.repository.EventRepository;
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

/** Starts and finishes an event against a real database with the stub gateways of the local profile. */
class EventFinishIT extends PostgresIntegrationTest {

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
    private EventEvaluationRepository evaluations;
    @Autowired
    private EventBrigadierRepository brigadiers;
    @Autowired
    private StubAlertGateway alertStub;
    @Autowired
    private StubCheckInGateway checkInStub;
    @Autowired
    private StubBrigadeGateway brigadeStub;

    private final AccessActor coordinator = new AccessActor("coord-it", Set.of(Role.COORDINADOR));

    /** Creates a scheduled event and starts it, as the scheduler would do. */
    private UUID runningEvent(String name) {
        CreateEventRequest request = new CreateEventRequest(
                name, null, Set.of(types.findByCode("SISMO").orElseThrow().getId()),
                Instant.now().plus(2, ChronoUnit.DAYS),
                List.of(new ScopeItemRequest(ScopeType.ZONE, "z1")), Set.of(Role.ESTUDIANTE));
        UUID eventId = creation.create(request, coordinator).id();
        EventEntity event = events.findById(eventId).orElseThrow();
        event.setScheduledStart(Instant.now().minusSeconds(30));
        events.saveAndFlush(event);
        lifecycle.startDueEvents();
        assertEquals(EventStatus.IN_PROGRESS, events.findById(eventId).orElseThrow().getStatus());
        return eventId;
    }

    @Test
    void finishingChangesTheStatusTellsEveryoneAndSavesTheEvaluation() {
        UUID eventId = runningEvent("Finish IT");

        EventFinishResponse response = lifecycle.finish(eventId, coordinator);

        assertEquals(EventStatus.FINISHED, response.status());
        assertNotNull(response.endedAt());
        assertNotNull(response.evaluation());
        assertTrue(response.evaluation().complete());
        assertEquals(EventStatus.FINISHED, events.findById(eventId).orElseThrow().getStatus());
        assertEquals(EventStatus.FINISHED, checkInStub.lastState(eventId).orElseThrow().status());
        assertEquals(EventStatus.FINISHED, brigadeStub.lastState(eventId).orElseThrow().status());
        assertTrue(evaluations.findByEventId(eventId).isPresent());
        assertEquals(1, alertLinks.findByEventIdAndPurpose(eventId, EventAlertPurpose.FINISH).size());
        assertTrue(alertStub.created().stream().anyMatch(command ->
                command.eventId().equals(eventId) && command.purpose() == EventAlertPurpose.FINISH));

        List<EventStateHistoryEntity> history = stateService.history(eventId);
        EventStateHistoryEntity last = history.stream()
                .filter(entry -> entry.getToStatus() == EventStatus.FINISHED).findFirst().orElseThrow();
        assertEquals("coord-it", last.getChangedBy());
    }

    @Test
    void theFinalListOfBrigadiersIsCopiedWhenTheEventFinishes() {
        UUID eventId = runningEvent("Final brigadiers IT");
        brigadeStub.givenBrigadiers(eventId, List.of(new BrigadierInfo("u-7", "Ana", "z1", true)));
        assertTrue(brigadiers.findByEventId(eventId).isEmpty());

        lifecycle.finish(eventId, coordinator);

        assertEquals(1, brigadiers.findByEventId(eventId).size());
    }

    @Test
    void aFinishedEventCannotBeFinishedAgain() {
        UUID eventId = runningEvent("Finish twice IT");
        lifecycle.finish(eventId, coordinator);

        assertThrows(InvalidStateTransitionException.class, () -> lifecycle.finish(eventId, coordinator));

        assertEquals(1, evaluations.findAll().stream().filter(row -> row.getEventId().equals(eventId)).count());
    }
}
