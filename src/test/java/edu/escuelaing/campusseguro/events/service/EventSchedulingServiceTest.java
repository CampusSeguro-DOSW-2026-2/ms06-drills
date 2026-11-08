package edu.escuelaing.campusseguro.events.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.escuelaing.campusseguro.events.client.EventStatePayload;
import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.client.alert.AlertCommand;
import edu.escuelaing.campusseguro.events.client.alert.AlertGateway;
import edu.escuelaing.campusseguro.events.client.brigade.BrigadeGateway;
import edu.escuelaing.campusseguro.events.client.checkin.CheckInGateway;
import edu.escuelaing.campusseguro.events.entity.EventAlertEntity;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.enums.EventAlertPurpose;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.repository.EventAlertRepository;
import edu.escuelaing.campusseguro.events.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

/** Tests the scheduling order and the undo steps with mocked gateways. */
class EventSchedulingServiceTest {

    private static final Instant START = Instant.parse("2026-12-01T15:00:00Z");

    private final CheckInGateway checkIn = mock(CheckInGateway.class);
    private final BrigadeGateway brigade = mock(BrigadeGateway.class);
    private final AlertGateway alerts = mock(AlertGateway.class);
    private final EventAlertRepository alertLinks = mock(EventAlertRepository.class);
    private final EventStateService stateService = mock(EventStateService.class);
    private final EventSchedulingService service =
            new EventSchedulingService(checkIn, brigade, alerts, alertLinks, stateService);

    private final List<ScopeRef> scope = List.of(new ScopeRef(ScopeType.ZONE, "z1"));
    private final Set<Role> roles = Set.of(Role.ESTUDIANTE);
    private EventEntity event;

    private static ExternalServiceUnavailableException down(String service) {
        return new ExternalServiceUnavailableException(service, new RuntimeException("down"));
    }

    @BeforeEach
    void setUp() {
        event = new EventEntity();
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        event.setName("Drill");
        event.setSimulacro(true);
        event.setStatus(EventStatus.DRAFT);
        event.setScheduledStart(START);
        when(alerts.createAlert(any(AlertCommand.class))).thenReturn("alert-1", "alert-2");
        when(alertLinks.saveAll(any())).thenAnswer(call -> List.copyOf(call.<List<EventAlertEntity>>getArgument(0)));
    }

    @Test
    void schedulesInOrderAndCreatesTheTwoAlerts() {
        SchedulingOutcome outcome = service.schedule(event, scope, roles, "coord-1");

        assertTrue(outcome.scheduled());
        assertTrue(outcome.warnings().isEmpty());
        assertEquals(2, outcome.alerts().size());
        assertEquals(EventAlertPurpose.BRIGADISTS, outcome.alerts().get(0).getPurpose());
        assertEquals("alert-1", outcome.alerts().get(0).getAlertId());
        assertEquals(EventAlertPurpose.GENERAL, outcome.alerts().get(1).getPurpose());

        InOrder order = inOrder(checkIn, brigade, alerts, stateService);
        order.verify(checkIn).pushState(any(EventStatePayload.class));
        order.verify(brigade).pushState(any(EventStatePayload.class));
        order.verify(alerts, times(2)).createAlert(any(AlertCommand.class));
        order.verify(stateService).transition(event.getId(), EventStatus.SCHEDULED, "coord-1", "Scheduled with all its data");
    }

    @Test
    void brigadiersAlertIsImmediateAndGeneralAlertWaitsForTheStartDate() {
        service.schedule(event, scope, roles, "coord-1");

        ArgumentCaptor<AlertCommand> captor = ArgumentCaptor.forClass(AlertCommand.class);
        verify(alerts, times(2)).createAlert(captor.capture());
        AlertCommand brigadiers = captor.getAllValues().get(0);
        AlertCommand general = captor.getAllValues().get(1);

        assertEquals(EventAlertPurpose.BRIGADISTS, brigadiers.purpose());
        assertTrue(brigadiers.activateImmediately());
        assertNull(brigadiers.activationAt());
        assertEquals(Set.of(Role.BRIGADISTA), brigadiers.recipientRoles());
        assertTrue(brigadiers.simulacro());

        assertEquals(EventAlertPurpose.GENERAL, general.purpose());
        assertFalse(general.activateImmediately());
        assertEquals(START, general.activationAt());
        assertEquals(roles, general.recipientRoles());
        assertEquals(scope, general.scope());
    }

    @Test
    void sendsTheScheduledStateToChiAndBei() {
        service.schedule(event, scope, roles, "coord-1");

        ArgumentCaptor<EventStatePayload> chi = ArgumentCaptor.forClass(EventStatePayload.class);
        verify(checkIn).pushState(chi.capture());
        assertEquals(EventStatus.SCHEDULED, chi.getValue().status());
        assertEquals(START, chi.getValue().startDate());
        assertEquals(scope, chi.getValue().scope());
        assertEquals(roles, chi.getValue().invitedRoles());
        verify(brigade).pushState(chi.getValue());
    }

    @Test
    void chiDownStopsEverythingAndThrows() {
        doThrow(down("MS04")).when(checkIn).pushState(any(EventStatePayload.class));

        assertThrows(ExternalServiceUnavailableException.class, () -> service.schedule(event, scope, roles, "coord-1"));

        verify(brigade, never()).pushState(any(EventStatePayload.class));
        verify(alerts, never()).createAlert(any(AlertCommand.class));
        verify(stateService, never()).transition(any(), any(), anyString(), any());
    }

    @Test
    void beiDownKeepsTheEventAsDraftAndUndoesChi() {
        doThrow(down("MS05")).when(brigade).pushState(any(EventStatePayload.class));

        SchedulingOutcome outcome = service.schedule(event, scope, roles, "coord-1");

        assertFalse(outcome.scheduled());
        assertEquals("SAVED_AS_DRAFT", outcome.warnings().get(0).code());
        assertTrue(outcome.warnings().get(0).message().contains("MS05") || outcome.warnings().get(0).message().contains("BEI"));
        assertTrue(outcome.alerts().isEmpty());
        ArgumentCaptor<EventStatePayload> chi = ArgumentCaptor.forClass(EventStatePayload.class);
        verify(checkIn, times(2)).pushState(chi.capture());
        assertEquals(EventStatus.DRAFT, chi.getAllValues().get(1).status());
        verify(alerts, never()).createAlert(any(AlertCommand.class));
        verify(stateService, never()).transition(any(), any(), anyString(), any());
    }

    @Test
    void aleDownOnTheSecondAlertUndoesEverything() {
        when(alerts.createAlert(any(AlertCommand.class))).thenReturn("alert-1").thenThrow(down("MS03"));

        SchedulingOutcome outcome = service.schedule(event, scope, roles, "coord-1");

        assertFalse(outcome.scheduled());
        assertEquals("SAVED_AS_DRAFT", outcome.warnings().get(0).code());
        verify(alerts).backToDraft("alert-1");
        ArgumentCaptor<EventStatePayload> chi = ArgumentCaptor.forClass(EventStatePayload.class);
        verify(checkIn, times(2)).pushState(chi.capture());
        assertEquals(EventStatus.DRAFT, chi.getAllValues().get(1).status());
        ArgumentCaptor<EventStatePayload> bei = ArgumentCaptor.forClass(EventStatePayload.class);
        verify(brigade, times(2)).pushState(bei.capture());
        assertEquals(EventStatus.DRAFT, bei.getAllValues().get(1).status());
        verify(alertLinks, never()).saveAll(any());
        verify(stateService, never()).transition(any(), any(), anyString(), any());
    }

    @Test
    void aleDownOnTheFirstAlertHasNothingToUndoInAle() {
        when(alerts.createAlert(any(AlertCommand.class))).thenThrow(down("MS03"));

        SchedulingOutcome outcome = service.schedule(event, scope, roles, "coord-1");

        assertFalse(outcome.scheduled());
        verify(alerts, never()).backToDraft(anyString());
    }

    @Test
    void aFailingUndoDoesNotBreakTheOutcome() {
        doThrow(down("MS05")).when(brigade).pushState(any(EventStatePayload.class));
        doThrow(down("MS04")).when(checkIn).pushState(
                org.mockito.ArgumentMatchers.argThat(payload -> payload != null && payload.status() == EventStatus.DRAFT));

        SchedulingOutcome outcome = service.schedule(event, scope, roles, "coord-1");

        assertFalse(outcome.scheduled());
    }
}
