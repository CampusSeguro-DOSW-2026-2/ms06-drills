package edu.escuelaing.campusseguro.events.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.escuelaing.campusseguro.events.client.alert.StubAlertGateway;
import edu.escuelaing.campusseguro.events.client.checkin.StubCheckInGateway;
import edu.escuelaing.campusseguro.events.dto.request.CreateEventRequest;
import edu.escuelaing.campusseguro.events.dto.response.EventResponse;
import edu.escuelaing.campusseguro.events.dto.request.ScopeItemRequest;
import edu.escuelaing.campusseguro.events.entity.EventStateHistoryEntity;
import edu.escuelaing.campusseguro.events.entity.enums.EventAlertPurpose;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.repository.EventAlertRepository;
import edu.escuelaing.campusseguro.events.repository.EventParticipantRepository;
import edu.escuelaing.campusseguro.events.repository.EventScopeItemRepository;
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

/** Creates events against a real database with the stub gateways of the local profile. */
class EventCreationIT extends PostgresIntegrationTest {

    @Autowired
    private EventCreationService creation;
    @Autowired
    private EventStateService stateService;
    @Autowired
    private EventTypeRepository types;
    @Autowired
    private EventScopeItemRepository scope;
    @Autowired
    private EventParticipantRepository participants;
    @Autowired
    private EventAlertRepository alerts;
    @Autowired
    private StubAlertGateway alertStub;
    @Autowired
    private StubCheckInGateway checkInStub;

    private final AccessActor coordinator = new AccessActor("coord-it", Set.of(Role.COORDINADOR));

    private UUID typeId(String code) {
        return types.findByCode(code).orElseThrow().getId();
    }

    @Test
    void draftEventIsSavedWithItsData() {
        CreateEventRequest request = new CreateEventRequest(
                "Draft IT", null, Set.of(typeId("SISMO")), null,
                List.of(new ScopeItemRequest(ScopeType.BUILDING, "b1")), Set.of(Role.PROFESOR));

        EventResponse response = creation.create(request, coordinator);

        assertEquals(EventStatus.DRAFT, response.status());
        assertEquals(1, scope.findByEventId(response.id()).size());
        assertEquals(1, participants.findByEventId(response.id()).size());
        assertTrue(alerts.findByEventId(response.id()).isEmpty());
        List<EventStateHistoryEntity> history = stateService.history(response.id());
        assertEquals(List.of(EventStatus.DRAFT), history.stream().map(EventStateHistoryEntity::getToStatus).toList());
    }

    @Test
    void completeEventIsScheduledWithTwoAlertsAndChiIsTold() {
        int alertsBefore = alertStub.created().size();
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        CreateEventRequest request = new CreateEventRequest(
                "Scheduled IT", "desc", Set.of(typeId("INCENDIO")), start,
                List.of(new ScopeItemRequest(ScopeType.ZONE, "z1")), Set.of(Role.ESTUDIANTE, Role.PROFESOR));

        EventResponse response = creation.create(request, coordinator);

        assertEquals(EventStatus.SCHEDULED, response.status());
        assertEquals(2, response.alerts().size());
        assertEquals(alertsBefore + 2, alertStub.created().size());
        assertEquals(1, alerts.findByEventIdAndPurpose(response.id(), EventAlertPurpose.BRIGADISTS).size());
        assertEquals(1, alerts.findByEventIdAndPurpose(response.id(), EventAlertPurpose.GENERAL).size());
        assertEquals(EventStatus.SCHEDULED, checkInStub.lastState(response.id()).orElseThrow().status());

        List<EventStateHistoryEntity> history = stateService.history(response.id());
        assertEquals(
                Set.of(EventStatus.DRAFT, EventStatus.SCHEDULED),
                history.stream().map(EventStateHistoryEntity::getToStatus).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2, history.size());
    }
}
