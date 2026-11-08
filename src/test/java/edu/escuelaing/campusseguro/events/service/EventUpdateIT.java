package edu.escuelaing.campusseguro.events.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.escuelaing.campusseguro.events.client.alert.StubAlertGateway;
import edu.escuelaing.campusseguro.events.client.checkin.StubCheckInGateway;
import edu.escuelaing.campusseguro.events.dto.request.CreateEventRequest;
import edu.escuelaing.campusseguro.events.dto.response.EventResponse;
import edu.escuelaing.campusseguro.events.dto.request.ScopeItemRequest;
import edu.escuelaing.campusseguro.events.entity.EventParticipantEntity;
import edu.escuelaing.campusseguro.events.entity.EventScopeItemEntity;
import edu.escuelaing.campusseguro.events.entity.EventStateHistoryEntity;
import edu.escuelaing.campusseguro.events.entity.enums.EventAlertPurpose;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.exception.BusinessException;
import edu.escuelaing.campusseguro.events.exception.ErrorCode;
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
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Edits events against a real database with the stub gateways of the local profile. */
class EventUpdateIT extends PostgresIntegrationTest {

    @Autowired
    private EventCreationService creation;
    @Autowired
    private EventUpdateService update;
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

    private UUID sismo() {
        return types.findByCode("SISMO").orElseThrow().getId();
    }

    private static Instant inDays(long days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
    }

    private static List<ScopeItemRequest> zones(String... ids) {
        return java.util.Arrays.stream(ids).map(id -> new ScopeItemRequest(ScopeType.ZONE, id)).toList();
    }

    private CreateEventRequest request(String name, Instant start, List<ScopeItemRequest> items, Set<Role> roles) {
        return new CreateEventRequest(name, "desc", Set.of(sismo()), start, items, roles);
    }

    private EventResponse createScheduled(String name) {
        return creation.create(request(name, inDays(3), zones("z1"), Set.of(Role.ESTUDIANTE)), coordinator);
    }

    private long alertCount(UUID eventId, EventAlertPurpose purpose) {
        return alerts.findByEventIdAndPurpose(eventId, purpose).size();
    }

    @Test
    void aDraftIsScheduledWhenItIsCompleted() {
        EventResponse draft = creation.create(request("Draft to schedule", null, null, null), coordinator);
        assertEquals(EventStatus.DRAFT, draft.status());

        EventResponse edited = update.update(draft.id(),
                request("Draft to schedule", inDays(2), zones("z1"), Set.of(Role.PROFESOR)), coordinator);

        assertEquals(EventStatus.SCHEDULED, edited.status());
        assertEquals(2, edited.alerts().size());
        assertEquals(1, scope.findByEventId(draft.id()).size());
        assertEquals(1, participants.findByEventId(draft.id()).size());
    }

    @Test
    void editingAScheduledEventSendsAnEditAlertAndReschedulesTheGeneralOne() {
        EventResponse scheduled = createScheduled("Scheduled to edit");
        String generalId = scheduled.alerts().stream()
                .filter(link -> link.purpose() == EventAlertPurpose.GENERAL).findFirst().orElseThrow().alertId();
        Instant newStart = inDays(5);

        EventResponse edited = update.update(scheduled.id(),
                request("Scheduled edited", newStart, zones("z1"), Set.of(Role.ESTUDIANTE)), coordinator);

        assertEquals(EventStatus.SCHEDULED, edited.status());
        assertEquals("Scheduled edited", edited.name());
        assertEquals(1, alertCount(scheduled.id(), EventAlertPurpose.EDIT));
        assertTrue(alertStub.rescheduled().contains(generalId + "@" + newStart));
        assertEquals("Scheduled edited", checkInStub.lastState(scheduled.id()).orElseThrow().name());
        assertEquals(1, scope.findByEventId(scheduled.id()).size());
    }

    @Test
    void removingTheDateSendsTheEventBackToDraft() {
        EventResponse scheduled = createScheduled("Scheduled to draft");
        String generalId = scheduled.alerts().stream()
                .filter(link -> link.purpose() == EventAlertPurpose.GENERAL).findFirst().orElseThrow().alertId();

        EventResponse edited = update.update(scheduled.id(),
                request("Scheduled to draft", null, zones("z1"), Set.of(Role.ESTUDIANTE)), coordinator);

        assertEquals(EventStatus.DRAFT, edited.status());
        assertEquals(0, alertCount(scheduled.id(), EventAlertPurpose.GENERAL));
        assertTrue(alertStub.backToDraft().contains(generalId));
        assertEquals(EventStatus.DRAFT, checkInStub.lastState(scheduled.id()).orElseThrow().status());
        List<EventStateHistoryEntity> history = stateService.history(scheduled.id());
        assertEquals(1, history.stream()
                .filter(entry -> entry.getFromStatus() == EventStatus.SCHEDULED
                        && entry.getToStatus() == EventStatus.DRAFT)
                .count());
    }

    @Test
    void editingAStartedEventSendsTwoEditAlerts() {
        EventResponse scheduled = createScheduled("Started to edit");
        Instant start = scheduled.startDate();
        stateService.transition(scheduled.id(), EventStatus.IN_PROGRESS, "system", null);

        EventResponse edited = update.update(scheduled.id(),
                request("Started edited", start, zones("z1", "z2"), Set.of(Role.ESTUDIANTE, Role.PROFESOR)), coordinator);

        assertEquals(EventStatus.IN_PROGRESS, edited.status());
        assertEquals(2, alertCount(scheduled.id(), EventAlertPurpose.EDIT));
        assertEquals(EventStatus.IN_PROGRESS, checkInStub.lastState(scheduled.id()).orElseThrow().status());
    }

    @Test
    void scopeAndRolesAreReplaced() {
        EventResponse draft = creation.create(
                request("Replace data", null, zones("z1", "z2"), Set.of(Role.PROFESOR)), coordinator);

        update.update(draft.id(), request("Replace data", null, zones("z2", "z3"), Set.of(Role.ESTUDIANTE)), coordinator);

        Set<String> refs = scope.findByEventId(draft.id()).stream()
                .map(EventScopeItemEntity::getScopeRefId).collect(Collectors.toSet());
        Set<Role> roles = participants.findByEventId(draft.id()).stream()
                .map(EventParticipantEntity::getRole).collect(Collectors.toSet());
        assertEquals(Set.of("z2", "z3"), refs);
        assertEquals(Set.of(Role.ESTUDIANTE), roles);
    }

    @Test
    void aFinishedEventCannotBeEdited() {
        EventResponse scheduled = createScheduled("Finished event");
        stateService.transition(scheduled.id(), EventStatus.IN_PROGRESS, "system", null);
        stateService.transition(scheduled.id(), EventStatus.FINISHED, "system", null);

        BusinessException ex = assertThrows(BusinessException.class, () -> update.update(scheduled.id(),
                request("Finished edited", scheduled.startDate(), zones("z1"), Set.of(Role.ESTUDIANTE)), coordinator));

        assertEquals(ErrorCode.EVENT_NOT_EDITABLE, ex.code());
    }
}
