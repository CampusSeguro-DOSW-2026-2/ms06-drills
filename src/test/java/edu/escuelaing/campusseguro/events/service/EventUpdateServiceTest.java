package edu.escuelaing.campusseguro.events.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.escuelaing.campusseguro.events.client.EventStatePayload;
import edu.escuelaing.campusseguro.events.client.alert.AlertCommand;
import edu.escuelaing.campusseguro.events.client.alert.AlertGateway;
import edu.escuelaing.campusseguro.events.client.brigade.BrigadeGateway;
import edu.escuelaing.campusseguro.events.client.campus.CampusGateway;
import edu.escuelaing.campusseguro.events.client.checkin.CheckInGateway;
import edu.escuelaing.campusseguro.events.client.identity.IdentityGateway;
import edu.escuelaing.campusseguro.events.dto.CreateEventRequest;
import edu.escuelaing.campusseguro.events.dto.EventResponse;
import edu.escuelaing.campusseguro.events.dto.ScopeItemRequest;
import edu.escuelaing.campusseguro.events.entity.EventAlertEntity;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.EventParticipantEntity;
import edu.escuelaing.campusseguro.events.entity.EventScopeItemEntity;
import edu.escuelaing.campusseguro.events.entity.EventTypeEntity;
import edu.escuelaing.campusseguro.events.entity.enums.EventAlertPurpose;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.exception.BusinessException;
import edu.escuelaing.campusseguro.events.exception.ErrorCode;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.exception.InvalidFieldException;
import edu.escuelaing.campusseguro.events.exception.ResourceNotFoundException;
import edu.escuelaing.campusseguro.events.repository.EventAlertRepository;
import edu.escuelaing.campusseguro.events.repository.EventParticipantRepository;
import edu.escuelaing.campusseguro.events.repository.EventRepository;
import edu.escuelaing.campusseguro.events.repository.EventScopeItemRepository;
import edu.escuelaing.campusseguro.events.repository.EventTypeRepository;
import edu.escuelaing.campusseguro.events.security.AccessActor;
import edu.escuelaing.campusseguro.events.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

/** Tests the edit rules for each status with mocked repositories and gateways. */
class EventUpdateServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T14:00:00Z");
    private static final Instant START = Instant.parse("2026-12-01T15:00:00Z");
    private static final Instant NEW_START = Instant.parse("2026-12-05T09:00:00Z");

    private final EventRepository events = mock(EventRepository.class);
    private final EventTypeRepository eventTypes = mock(EventTypeRepository.class);
    private final EventScopeItemRepository scopeItems = mock(EventScopeItemRepository.class);
    private final EventParticipantRepository participants = mock(EventParticipantRepository.class);
    private final EventAlertRepository alertLinks = mock(EventAlertRepository.class);
    private final EventSchedulingService scheduling = mock(EventSchedulingService.class);
    private final EventStateService stateService = mock(EventStateService.class);
    private final CheckInGateway checkIn = mock(CheckInGateway.class);
    private final BrigadeGateway brigade = mock(BrigadeGateway.class);
    private final AlertGateway alerts = mock(AlertGateway.class);
    private final CampusGateway campus = mock(CampusGateway.class);
    private final IdentityGateway identity = mock(IdentityGateway.class);
    private final EventUpdateService service = new EventUpdateService(
            events, eventTypes, scopeItems, participants, alertLinks, campus, identity,
            scheduling, stateService, checkIn, brigade, alerts, Clock.fixed(NOW, ZoneOffset.UTC));

    private final AccessActor coordinator = new AccessActor("coord-1", Set.of(Role.COORDINADOR));
    private final AccessActor organizer = new AccessActor("org-1", Set.of(Role.ORGANIZADOR));
    private final UUID id = UUID.randomUUID();

    private EventTypeEntity sismo;
    private EventTypeEntity simulacro;
    private EventEntity event;
    private EventAlertEntity general;

    private static ExternalServiceUnavailableException down(String service) {
        return new ExternalServiceUnavailableException(service, new RuntimeException("down"));
    }

    private EventTypeEntity type(String code) {
        EventTypeEntity entity = new EventTypeEntity();
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        entity.setCode(code);
        entity.setName(code);
        entity.setActive(true);
        return entity;
    }

    @BeforeEach
    void setUp() {
        sismo = type("SISMO");
        simulacro = type(EventTypeEntity.SIMULACRO_CODE);
        when(eventTypes.findByIdIn(any())).thenAnswer(call -> {
            Set<UUID> ids = Set.copyOf(call.<java.util.Collection<UUID>>getArgument(0));
            return List.of(sismo, simulacro).stream().filter(t -> ids.contains(t.getId())).toList();
        });
        givenEvent(EventStatus.SCHEDULED, START, false);
        when(alerts.createAlert(any(AlertCommand.class))).thenReturn("edit-1", "edit-2");
    }

    /** Stores an event with one zone (z1), one role (ESTUDIANTE) and, if it has a date, a general alert. */
    private void givenEvent(EventStatus status, Instant start, boolean drill) {
        event = new EventEntity();
        ReflectionTestUtils.setField(event, "id", id);
        event.setName("Old");
        event.setDescription("old");
        event.setStatus(status);
        event.setSimulacro(drill);
        event.setScheduledStart(start);
        event.setCreatedBy("coord-1");
        event.setTypes(new HashSet<>(Set.of(drill ? simulacro : sismo)));
        when(events.findForUpdate(id)).thenReturn(Optional.of(event));

        EventScopeItemEntity zone = new EventScopeItemEntity();
        zone.setEventId(id);
        zone.setScopeType(ScopeType.ZONE);
        zone.setScopeRefId("z1");
        when(scopeItems.findByEventId(id)).thenReturn(List.of(zone));

        EventParticipantEntity role = new EventParticipantEntity();
        role.setEventId(id);
        role.setRole(Role.ESTUDIANTE);
        when(participants.findByEventId(id)).thenReturn(List.of(role));

        general = new EventAlertEntity();
        general.setEventId(id);
        general.setAlertId("general-1");
        general.setPurpose(EventAlertPurpose.GENERAL);
        when(alertLinks.findByEventIdAndPurpose(id, EventAlertPurpose.GENERAL)).thenReturn(List.of(general));
        when(alertLinks.findByEventId(id)).thenReturn(List.of(general));
    }

    private CreateEventRequest request(String name, Instant start, UUID typeId, List<String> zones, Set<Role> roles) {
        List<ScopeItemRequest> scope = zones.stream().map(zone -> new ScopeItemRequest(ScopeType.ZONE, zone)).toList();
        return new CreateEventRequest(name, "old", Set.of(typeId), start, scope, roles);
    }

    /** The same data the event already has: editing with it changes nothing. */
    private CreateEventRequest same() {
        return request("Old", event.getScheduledStart(), sismo.getId(), List.of("z1"), Set.of(Role.ESTUDIANTE));
    }

    private CreateEventRequest renamed(String name) {
        return request(name, event.getScheduledStart(), sismo.getId(), List.of("z1"), Set.of(Role.ESTUDIANTE));
    }

    // ------------------------------------------------------------ general rules

    @Test
    void anUnknownEventIsNotFound() {
        when(events.findForUpdate(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.update(id, same(), coordinator));
    }

    @Test
    void aFinishedEventCannotBeEdited() {
        givenEvent(EventStatus.FINISHED, START, false);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(id, same(), coordinator));

        assertEquals(ErrorCode.EVENT_NOT_EDITABLE, ex.code());
    }

    @Test
    void theCoordinatorCannotEditADrill() {
        givenEvent(EventStatus.DRAFT, null, true);

        assertThrows(AccessDeniedException.class, () -> service.update(id,
                request("New", null, simulacro.getId(), List.of(), Set.of()), coordinator));
        verify(events, never()).save(any(EventEntity.class));
    }

    @Test
    void theOrganizerCannotEditARealEvent() {
        assertThrows(AccessDeniedException.class, () -> service.update(id, renamed("New"), organizer));
    }

    @Test
    void theKindOfEventCannotChange() {
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, () -> service.update(id,
                request("Old", START, simulacro.getId(), List.of("z1"), Set.of(Role.ESTUDIANTE)), coordinator));

        assertEquals("typeIds", ex.field());
    }

    // -------------------------------------------------------------------- Draft

    @Test
    void aDraftIsSavedAndStaysDraftWhenIncomplete() {
        givenEvent(EventStatus.DRAFT, null, false);

        EventResponse response = service.update(id,
                request("New", null, sismo.getId(), List.of(), Set.of()), coordinator);

        assertEquals("New", response.name());
        assertEquals(EventStatus.DRAFT, response.status());
        verify(events).save(event);
        verify(scheduling, never()).schedule(any(), any(), any(), anyString());
    }

    @Test
    void aDraftIsScheduledWhenItBecomesComplete() {
        givenEvent(EventStatus.DRAFT, null, false);
        when(scheduling.schedule(any(), any(), any(), anyString()))
                .thenReturn(new SchedulingOutcome(true, List.of(), List.of()));

        service.update(id, request("Old", START, sismo.getId(), List.of("z1"), Set.of(Role.ESTUDIANTE)), coordinator);

        verify(scheduling).schedule(event, List.of(new edu.escuelaing.campusseguro.events.client.ScopeRef(
                ScopeType.ZONE, "z1")), Set.of(Role.ESTUDIANTE), "coord-1");
        assertEquals(START, event.getScheduledStart());
    }

    @Test
    void aDraftWithAPastDateIsRejected() {
        givenEvent(EventStatus.DRAFT, null, false);

        InvalidFieldException ex = assertThrows(InvalidFieldException.class, () -> service.update(id,
                request("Old", NOW.minusSeconds(1), sismo.getId(), List.of(), Set.of()), coordinator));

        assertEquals("startDate", ex.field());
        verify(events, never()).save(any(EventEntity.class));
    }

    @Test
    void campusDownKeepsTheCurrentScopeAndWarns() {
        givenEvent(EventStatus.DRAFT, null, false);
        doThrow(down("MS02")).when(campus).validateScope(any(), any());

        EventResponse response = service.update(id,
                request("Old", null, sismo.getId(), List.of("z9"), Set.of(Role.ESTUDIANTE)), coordinator);

        assertEquals(1, response.scope().size());
        assertEquals("z1", response.scope().get(0).id());
        assertEquals("SCOPE_PENDING", response.warnings().get(0).code());
    }

    // ---------------------------------------------------------------- Scheduled

    @Test
    void aScheduledEventRejectsAPastDate() {
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, () -> service.update(id,
                request("Old", NOW.minusSeconds(1), sismo.getId(), List.of("z1"), Set.of(Role.ESTUDIANTE)), coordinator));

        assertEquals("startDate", ex.field());
    }

    @Test
    void aScheduledEventMayKeepItsCurrentDateEvenIfItIsAboutToStart() {
        givenEvent(EventStatus.SCHEDULED, NOW.minusSeconds(5), false);

        service.update(id, renamed("New"), coordinator);

        verify(checkIn).pushState(any(EventStatePayload.class));
    }

    @Test
    void editingWithTheSameDataDoesNothing() {
        service.update(id, same(), coordinator);

        verify(events, never()).save(any(EventEntity.class));
        verify(checkIn, never()).pushState(any(EventStatePayload.class));
        verify(brigade, never()).pushState(any(EventStatePayload.class));
        verify(alerts, never()).createAlert(any(AlertCommand.class));
    }

    @Test
    void aChangeTellsChiAndBeiAndAlertsTheBrigadiers() {
        EventResponse response = service.update(id, renamed("New"), coordinator);

        ArgumentCaptor<EventStatePayload> chi = ArgumentCaptor.forClass(EventStatePayload.class);
        verify(checkIn).pushState(chi.capture());
        assertEquals("New", chi.getValue().name());
        assertEquals(EventStatus.SCHEDULED, chi.getValue().status());
        verify(brigade).pushState(chi.getValue());

        ArgumentCaptor<AlertCommand> alert = ArgumentCaptor.forClass(AlertCommand.class);
        verify(alerts).createAlert(alert.capture());
        assertEquals(EventAlertPurpose.EDIT, alert.getValue().purpose());
        assertTrue(alert.getValue().activateImmediately());
        assertEquals(Set.of(Role.BRIGADISTA), alert.getValue().recipientRoles());

        verify(alerts, never()).reschedule(anyString(), any(Instant.class));
        verify(stateService, never()).transition(any(), any(), anyString(), any());
        assertEquals("New", response.name());
        assertEquals(EventStatus.SCHEDULED, response.status());
    }

    @Test
    void aNewDateReschedulesTheGeneralAlert() {
        service.update(id,
                request("Old", NEW_START, sismo.getId(), List.of("z1"), Set.of(Role.ESTUDIANTE)), coordinator);

        verify(alerts).reschedule("general-1", NEW_START);
        assertEquals(NEW_START, event.getScheduledStart());
    }

    @Test
    void chiDownStopsTheEditBeforeAnythingElse() {
        doThrow(down("MS04")).when(checkIn).pushState(any(EventStatePayload.class));

        assertThrows(ExternalServiceUnavailableException.class, () -> service.update(id, renamed("New"), coordinator));

        verify(brigade, never()).pushState(any(EventStatePayload.class));
        verify(alerts, never()).createAlert(any(AlertCommand.class));
        verify(alertLinks, never()).saveAll(any());
    }

    @Test
    void aleDownWhileReschedulingRestoresChiAndBei() {
        doThrow(down("MS03")).when(alerts).reschedule(anyString(), any(Instant.class));

        assertThrows(ExternalServiceUnavailableException.class, () -> service.update(id,
                request("New", NEW_START, sismo.getId(), List.of("z1"), Set.of(Role.ESTUDIANTE)), coordinator));

        ArgumentCaptor<EventStatePayload> chi = ArgumentCaptor.forClass(EventStatePayload.class);
        verify(checkIn, times(2)).pushState(chi.capture());
        assertEquals("New", chi.getAllValues().get(0).name());
        assertEquals("Old", chi.getAllValues().get(1).name());
        verify(brigade, times(2)).pushState(any(EventStatePayload.class));
        verify(alertLinks, never()).saveAll(any());
    }

    @Test
    void aleDownWhileCreatingTheAlertRestoresChiAndBei() {
        when(alerts.createAlert(any(AlertCommand.class))).thenThrow(down("MS03"));

        assertThrows(ExternalServiceUnavailableException.class, () -> service.update(id, renamed("New"), coordinator));

        verify(checkIn, times(2)).pushState(any(EventStatePayload.class));
        verify(brigade, times(2)).pushState(any(EventStatePayload.class));
    }

    @Test
    void removingTheDateSendsTheScheduledEventBackToDraft() {
        EventResponse response = service.update(id,
                request("Old", null, sismo.getId(), List.of("z1"), Set.of(Role.ESTUDIANTE)), coordinator);

        verify(alerts).backToDraft("general-1");
        ArgumentCaptor<EventStatePayload> chi = ArgumentCaptor.forClass(EventStatePayload.class);
        verify(checkIn).pushState(chi.capture());
        assertEquals(EventStatus.DRAFT, chi.getValue().status());
        verify(brigade).pushState(chi.getValue());
        verify(alertLinks).deleteAll(List.of(general));
        verify(stateService).transition(eq(id), eq(EventStatus.DRAFT), eq("coord-1"), anyString());
        assertFalse(response.warnings().stream().anyMatch(w -> "SCOPE_PENDING".equals(w.code())));
    }

    @Test
    void beiDownWhileGoingBackToDraftRestoresTheRest() {
        doThrow(down("MS05")).when(brigade).pushState(any(EventStatePayload.class));

        assertThrows(ExternalServiceUnavailableException.class, () -> service.update(id,
                request("Old", null, sismo.getId(), List.of("z1"), Set.of(Role.ESTUDIANTE)), coordinator));

        verify(alerts).reschedule("general-1", START);
        verify(checkIn, times(2)).pushState(any(EventStatePayload.class));
        verify(stateService, never()).transition(any(), any(), anyString(), any());
    }

    // -------------------------------------------------------------- In progress

    @Test
    void aStartedEventCannotChangeItsStartDate() {
        givenEvent(EventStatus.IN_PROGRESS, START, false);

        InvalidFieldException ex = assertThrows(InvalidFieldException.class, () -> service.update(id,
                request("Old", NEW_START, sismo.getId(), List.of("z1"), Set.of(Role.ESTUDIANTE)), coordinator));

        assertEquals("startDate", ex.field());
    }

    @Test
    void aStartedEventMustKeepItsScope() {
        givenEvent(EventStatus.IN_PROGRESS, START, false);

        InvalidFieldException ex = assertThrows(InvalidFieldException.class, () -> service.update(id,
                request("Old", START, sismo.getId(), List.of(), Set.of(Role.ESTUDIANTE)), coordinator));

        assertEquals("scope", ex.field());
    }

    @Test
    void aStartedEventMustKeepItsParticipants() {
        givenEvent(EventStatus.IN_PROGRESS, START, false);

        InvalidFieldException ex = assertThrows(InvalidFieldException.class, () -> service.update(id,
                request("Old", START, sismo.getId(), List.of("z1"), Set.of()), coordinator));

        assertEquals("participantRoles", ex.field());
    }

    @Test
    void editingAStartedEventAlertsBrigadiersAndParticipants() {
        givenEvent(EventStatus.IN_PROGRESS, START, false);

        service.update(id, request("New", START, sismo.getId(), List.of("z1"), Set.of(Role.ESTUDIANTE, Role.PROFESOR)),
                coordinator);

        ArgumentCaptor<EventStatePayload> chi = ArgumentCaptor.forClass(EventStatePayload.class);
        verify(checkIn).pushState(chi.capture());
        assertEquals(EventStatus.IN_PROGRESS, chi.getValue().status());
        verify(brigade).pushState(chi.getValue());

        ArgumentCaptor<AlertCommand> alert = ArgumentCaptor.forClass(AlertCommand.class);
        verify(alerts, times(2)).createAlert(alert.capture());
        assertEquals(Set.of(Role.BRIGADISTA), alert.getAllValues().get(0).recipientRoles());
        assertEquals(Set.of(Role.ESTUDIANTE, Role.PROFESOR), alert.getAllValues().get(1).recipientRoles());
        assertEquals(EventAlertPurpose.EDIT, alert.getAllValues().get(1).purpose());
        verify(stateService, never()).transition(any(), any(), anyString(), any());
    }

    @Test
    void editingAStartedEventWithoutChangesDoesNothing() {
        givenEvent(EventStatus.IN_PROGRESS, START, false);

        service.update(id, same(), coordinator);

        verify(checkIn, never()).pushState(any(EventStatePayload.class));
        verify(alerts, never()).createAlert(any(AlertCommand.class));
    }

    @Test
    void beiDownWhileEditingAStartedEventRestoresChi() {
        givenEvent(EventStatus.IN_PROGRESS, START, false);
        doThrow(down("MS05")).when(brigade).pushState(any(EventStatePayload.class));

        assertThrows(ExternalServiceUnavailableException.class, () -> service.update(id, renamed("New"), coordinator));

        verify(checkIn, times(2)).pushState(any(EventStatePayload.class));
        verify(alerts, never()).createAlert(any(AlertCommand.class));
    }
}
