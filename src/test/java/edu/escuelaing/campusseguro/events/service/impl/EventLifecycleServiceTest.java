package edu.escuelaing.campusseguro.events.service.impl;

import edu.escuelaing.campusseguro.events.client.EventStatePayload;
import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.client.alert.AlertActor;
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
import edu.escuelaing.campusseguro.events.dto.response.EvaluationResponse;
import edu.escuelaing.campusseguro.events.dto.response.EventFinishResponse;
import edu.escuelaing.campusseguro.events.dto.response.EventWarning;
import edu.escuelaing.campusseguro.events.exception.InvalidStateTransitionException;
import edu.escuelaing.campusseguro.events.exception.ResourceNotFoundException;
import edu.escuelaing.campusseguro.events.security.AccessActor;
import edu.escuelaing.campusseguro.events.security.EventAccessPolicy;
import edu.escuelaing.campusseguro.events.security.Role;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import edu.escuelaing.campusseguro.events.service.BrigadierSyncService;
import edu.escuelaing.campusseguro.events.service.EventEvaluationService;
import edu.escuelaing.campusseguro.events.service.EventStateService;
import edu.escuelaing.campusseguro.events.service.SchedulingOutcome;
import edu.escuelaing.campusseguro.events.entity.EventParticipantEntity;
import edu.escuelaing.campusseguro.events.entity.EventScopeItemEntity;
import edu.escuelaing.campusseguro.events.entity.EventStartFailureEntity;
import edu.escuelaing.campusseguro.events.entity.enums.ExternalModule;
import edu.escuelaing.campusseguro.events.repository.EventParticipantRepository;
import edu.escuelaing.campusseguro.events.repository.EventRepository;
import edu.escuelaing.campusseguro.events.repository.EventScopeItemRepository;
import edu.escuelaing.campusseguro.events.repository.EventStartFailureRepository;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;



/** Tests the lifecycle order and the undo steps with mocked gateways. */
class EventLifecycleServiceTest {

    private static AccessActor actor(String userId) {
        return new AccessActor(userId, Set.of(Role.COORDINADOR));
    }

    private static final Instant START = Instant.parse("2026-12-01T15:00:00Z");
    private static final Instant NOW = Instant.parse("2026-12-01T15:00:05Z");

    private final CheckInGateway checkIn = mock(CheckInGateway.class);
    private final BrigadeGateway brigade = mock(BrigadeGateway.class);
    private final AlertGateway alerts = mock(AlertGateway.class);
    private final EventAlertRepository alertLinks = mock(EventAlertRepository.class);
    private final EventStateService stateService = mock(EventStateService.class);
    private final EventRepository events = mock(EventRepository.class);
    private final EventScopeItemRepository scopeItems = mock(EventScopeItemRepository.class);
    private final EventParticipantRepository participants = mock(EventParticipantRepository.class);
    private final EventStartFailureRepository failures = mock(EventStartFailureRepository.class);
    private final TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
    private final EventEvaluationService evaluation = mock(EventEvaluationService.class);
    private final BrigadierSyncService brigadierSync = mock(BrigadierSyncService.class);
    private final EventLifecycleServiceImpl service = new EventLifecycleServiceImpl(
            checkIn, brigade, alerts, alertLinks, stateService, events, scopeItems, participants, failures, tx,
            Clock.fixed(NOW, ZoneOffset.UTC), new EventAccessPolicy(), evaluation, brigadierSync);

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
        when(alerts.createAlert(any(AlertCommand.class), any(AlertActor.class))).thenReturn("alert-1", "alert-2");
        when(alertLinks.saveAll(any())).thenAnswer(call -> List.copyOf(call.<List<EventAlertEntity>>getArgument(0)));
    }

    @Test
    void schedulesInOrderAndCreatesTheTwoAlerts() {
        SchedulingOutcome outcome = service.schedule(event, scope, roles, actor("coord-1"));

        assertTrue(outcome.scheduled());
        assertTrue(outcome.warnings().isEmpty());
        assertEquals(2, outcome.alerts().size());
        assertEquals(EventAlertPurpose.BRIGADISTS, outcome.alerts().get(0).getPurpose());
        assertEquals("alert-1", outcome.alerts().get(0).getAlertId());
        assertEquals(EventAlertPurpose.GENERAL, outcome.alerts().get(1).getPurpose());

        InOrder order = inOrder(checkIn, brigade, alerts, stateService);
        order.verify(checkIn).pushState(any(EventStatePayload.class));
        order.verify(brigade).pushState(any(EventStatePayload.class));
        order.verify(alerts, times(2)).createAlert(any(AlertCommand.class), any(AlertActor.class));
        order.verify(stateService).transition(event.getId(), EventStatus.SCHEDULED, "coord-1", "Scheduled with all its data");
    }

    @Test
    void brigadiersAlertIsImmediateAndGeneralAlertWaitsForTheStartDate() {
        service.schedule(event, scope, roles, actor("coord-1"));

        ArgumentCaptor<AlertCommand> captor = ArgumentCaptor.forClass(AlertCommand.class);
        verify(alerts, times(2)).createAlert(captor.capture(), any(AlertActor.class));
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
        service.schedule(event, scope, roles, actor("coord-1"));

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

        assertThrows(ExternalServiceUnavailableException.class, () -> service.schedule(event, scope, roles, actor("coord-1")));

        verify(brigade, never()).pushState(any(EventStatePayload.class));
        verify(alerts, never()).createAlert(any(AlertCommand.class), any(AlertActor.class));
        verify(stateService, never()).transition(any(), any(), anyString(), any());
    }

    @Test
    void beiDownKeepsTheEventAsDraftAndUndoesChi() {
        doThrow(down("MS05")).when(brigade).pushState(any(EventStatePayload.class));

        SchedulingOutcome outcome = service.schedule(event, scope, roles, actor("coord-1"));

        assertFalse(outcome.scheduled());
        assertEquals("SAVED_AS_DRAFT", outcome.warnings().get(0).code());
        assertTrue(outcome.warnings().get(0).message().contains("MS05") || outcome.warnings().get(0).message().contains("BEI"));
        assertTrue(outcome.alerts().isEmpty());
        ArgumentCaptor<EventStatePayload> chi = ArgumentCaptor.forClass(EventStatePayload.class);
        verify(checkIn, times(2)).pushState(chi.capture());
        assertEquals(EventStatus.DRAFT, chi.getAllValues().get(1).status());
        verify(alerts, never()).createAlert(any(AlertCommand.class), any(AlertActor.class));
        verify(stateService, never()).transition(any(), any(), anyString(), any());
    }

    @Test
    void aleDownOnTheSecondAlertUndoesEverything() {
        when(alerts.createAlert(any(AlertCommand.class), any(AlertActor.class))).thenReturn("alert-1").thenThrow(down("MS03"));

        SchedulingOutcome outcome = service.schedule(event, scope, roles, actor("coord-1"));

        assertFalse(outcome.scheduled());
        assertEquals("SAVED_AS_DRAFT", outcome.warnings().get(0).code());
        verify(alerts).archive(eq("alert-1"), any(AlertActor.class));
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
        when(alerts.createAlert(any(AlertCommand.class), any(AlertActor.class))).thenThrow(down("MS03"));

        SchedulingOutcome outcome = service.schedule(event, scope, roles, actor("coord-1"));

        assertFalse(outcome.scheduled());
        verify(alerts, never()).archive(anyString(), any(AlertActor.class));
    }

    @Test
    void aFailingUndoDoesNotBreakTheOutcome() {
        doThrow(down("MS05")).when(brigade).pushState(any(EventStatePayload.class));
        doThrow(down("MS04")).when(checkIn).pushState(
                org.mockito.ArgumentMatchers.argThat(payload -> payload != null && payload.status() == EventStatus.DRAFT));

        SchedulingOutcome outcome = service.schedule(event, scope, roles, actor("coord-1"));

        assertFalse(outcome.scheduled());
    }

    // ----- starting (RF-EVE-05) -----

    private EventEntity scheduledEvent(Instant start) {
        EventEntity scheduled = new EventEntity();
        ReflectionTestUtils.setField(scheduled, "id", UUID.randomUUID());
        scheduled.setName("Scheduled drill");
        scheduled.setSimulacro(true);
        scheduled.setStatus(EventStatus.SCHEDULED);
        scheduled.setScheduledStart(start);
        when(events.findForUpdate(scheduled.getId())).thenReturn(Optional.of(scheduled));
        return scheduled;
    }

    private void due(EventEntity... due) {
        when(events.findByStatusAndScheduledStartLessThanEqual(EventStatus.SCHEDULED, NOW)).thenReturn(List.of(due));
    }

    private void withScopeRolesAndGeneralAlert(EventEntity target, String alertId) {
        EventScopeItemEntity item = new EventScopeItemEntity();
        item.setScopeType(ScopeType.ZONE);
        item.setScopeRefId("z1");
        when(scopeItems.findByEventId(target.getId())).thenReturn(List.of(item));
        EventParticipantEntity participant = new EventParticipantEntity();
        participant.setRole(Role.ESTUDIANTE);
        when(participants.findByEventId(target.getId())).thenReturn(List.of(participant));
        EventAlertEntity link = new EventAlertEntity();
        link.setAlertId(alertId);
        link.setPurpose(EventAlertPurpose.GENERAL);
        when(alertLinks.findByEventIdAndPurpose(target.getId(), EventAlertPurpose.GENERAL)).thenReturn(List.of(link));
    }

    @Test
    void startsADueEventAndTellsChiBeiAndAle() {
        EventEntity due = scheduledEvent(START);
        due(due);
        withScopeRolesAndGeneralAlert(due, "alert-9");

        assertEquals(1, service.startDueEvents());

        verify(stateService).transition(due.getId(), EventStatus.IN_PROGRESS, "system", "Started automatically");
        ArgumentCaptor<EventStatePayload> chi = ArgumentCaptor.forClass(EventStatePayload.class);
        verify(checkIn).pushState(chi.capture());
        assertEquals(EventStatus.IN_PROGRESS, chi.getValue().status());
        assertEquals(List.of(new ScopeRef(ScopeType.ZONE, "z1")), chi.getValue().scope());
        assertEquals(Set.of(Role.ESTUDIANTE), chi.getValue().invitedRoles());
        verify(brigade).pushState(any(EventStatePayload.class));
        verify(alerts).activate(eq("alert-9"), any(AlertActor.class));
        verify(failures, never()).save(any());
    }

    @Test
    void startsEveryDueEventOnItsOwn() {
        EventEntity first = scheduledEvent(START);
        EventEntity second = scheduledEvent(START.minusSeconds(60));
        due(first, second);

        assertEquals(2, service.startDueEvents());

        verify(stateService).transition(first.getId(), EventStatus.IN_PROGRESS, "system", "Started automatically");
        verify(stateService).transition(second.getId(), EventStatus.IN_PROGRESS, "system", "Started automatically");
    }

    @Test
    void skipsAnEventThatWasEditedOrStartedMeanwhile() {
        EventEntity backToDraft = scheduledEvent(START);
        backToDraft.setStatus(EventStatus.DRAFT);
        EventEntity movedToTheFuture = scheduledEvent(NOW.plusSeconds(3600));
        EventEntity deleted = scheduledEvent(START);
        when(events.findForUpdate(deleted.getId())).thenReturn(Optional.empty());
        due(backToDraft, movedToTheFuture, deleted);

        assertEquals(0, service.startDueEvents());

        verify(stateService, never()).transition(any(), any(), anyString(), any());
        verify(checkIn, never()).pushState(any());
    }

    @Test
    void theEventStartsEvenIfChiBeiAndAleDoNotAnswer() {
        EventEntity due = scheduledEvent(START);
        due(due);
        withScopeRolesAndGeneralAlert(due, "alert-9");
        doThrow(down("MS04")).when(checkIn).pushState(any());
        doThrow(down("MS05")).when(brigade).pushState(any());
        doThrow(down("MS03")).when(alerts).activate(anyString(), any(AlertActor.class));

        assertEquals(1, service.startDueEvents());

        verify(stateService).transition(due.getId(), EventStatus.IN_PROGRESS, "system", "Started automatically");
        ArgumentCaptor<EventStartFailureEntity> saved = ArgumentCaptor.forClass(EventStartFailureEntity.class);
        verify(failures, times(3)).save(saved.capture());
        assertEquals(
                Set.of(ExternalModule.CHI, ExternalModule.BEI, ExternalModule.ALE),
                saved.getAllValues().stream().map(EventStartFailureEntity::getModule).collect(Collectors.toSet()));
        saved.getAllValues().forEach(row -> {
            assertEquals(1, row.getAttempts());
            assertTrue(row.isOpen());
            assertTrue(row.getLastError().contains("unavailable"));
        });
    }

    @Test
    void anErrorStartingOneEventDoesNotStopTheOthers() {
        EventEntity broken = scheduledEvent(START);
        when(events.findForUpdate(broken.getId())).thenThrow(new IllegalStateException("database error"));
        EventEntity healthy = scheduledEvent(START);
        due(broken, healthy);

        assertEquals(1, service.startDueEvents());

        verify(stateService).transition(healthy.getId(), EventStatus.IN_PROGRESS, "system", "Started automatically");
    }

    private EventStartFailureEntity openFailure(EventEntity target, ExternalModule module, int attempts) {
        EventStartFailureEntity row = new EventStartFailureEntity();
        ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
        row.setEventId(target.getId());
        row.setModule(module);
        row.setAttempts(attempts);
        row.setLastError("down");
        when(failures.findByResolvedAtIsNull()).thenReturn(List.of(row));
        when(failures.findById(row.getId())).thenReturn(Optional.of(row));
        when(failures.findByEventIdAndModule(target.getId(), module)).thenReturn(Optional.of(row));
        return row;
    }

    @Test
    void retryClosesTheCallWhenTheServiceAnswers() {
        EventEntity running = scheduledEvent(START);
        running.setStatus(EventStatus.IN_PROGRESS);
        when(events.findById(running.getId())).thenReturn(Optional.of(running));
        EventStartFailureEntity row = openFailure(running, ExternalModule.CHI, 1);

        assertEquals(1, service.retryPendingStartCalls());

        verify(checkIn).pushState(any(EventStatePayload.class));
        verify(brigade, never()).pushState(any());
        assertEquals(NOW, row.getResolvedAt());
    }

    @Test
    void retryKeepsTheCallOpenAndCountsTheNewAttempt() {
        EventEntity running = scheduledEvent(START);
        running.setStatus(EventStatus.IN_PROGRESS);
        when(events.findById(running.getId())).thenReturn(Optional.of(running));
        EventStartFailureEntity row = openFailure(running, ExternalModule.BEI, 2);
        doThrow(down("MS05")).when(brigade).pushState(any());

        assertEquals(0, service.retryPendingStartCalls());

        assertEquals(3, row.getAttempts());
        assertTrue(row.isOpen());
    }

    @Test
    void retryRetriesTheStartAlertForAle() {
        EventEntity running = scheduledEvent(START);
        running.setStatus(EventStatus.IN_PROGRESS);
        when(events.findById(running.getId())).thenReturn(Optional.of(running));
        openFailure(running, ExternalModule.ALE, 1);
        withScopeRolesAndGeneralAlert(running, "alert-7");

        assertEquals(1, service.retryPendingStartCalls());

        verify(alerts).activate(eq("alert-7"), any(AlertActor.class));
        verify(checkIn, never()).pushState(any());
    }

    @Test
    void retryClosesTheCallWithoutCallingWhenTheEventIsNotInProgressAnymore() {
        EventEntity finished = scheduledEvent(START);
        finished.setStatus(EventStatus.FINISHED);
        when(events.findById(finished.getId())).thenReturn(Optional.of(finished));
        EventStartFailureEntity row = openFailure(finished, ExternalModule.CHI, 4);

        assertEquals(1, service.retryPendingStartCalls());

        assertEquals(NOW, row.getResolvedAt());
        verify(checkIn, never()).pushState(any());
    }

    // ----- finishing (RF-EVE-04) -----

    private final AccessActor organizer = new AccessActor("org-1", Set.of(Role.ORGANIZADOR));

    private EventEntity runningEvent() {
        EventEntity running = scheduledEvent(START);
        running.setStatus(EventStatus.IN_PROGRESS);
        running.setStartedAt(START);
        when(events.findById(running.getId())).thenReturn(Optional.of(running));
        withScopeRolesAndGeneralAlert(running, "alert-9");
        return running;
    }

    private EvaluationResponse metrics(boolean complete, boolean noRecords) {
        return new EvaluationResponse(UUID.randomUUID(), NOW, complete, noRecords, 3, BigDecimal.TEN, Map.of(),
                1, 1, 1, 60L, List.of());
    }

    private List<String> codes(EventFinishResponse response) {
        return response.warnings().stream().map(EventWarning::code).toList();
    }

    @Test
    void finishesInOrderAndReturnsTheMetrics() {
        EventEntity running = runningEvent();
        EvaluationResponse metrics = metrics(true, false);
        when(evaluation.evaluate(running.getId())).thenReturn(metrics);

        EventFinishResponse response = service.finish(running.getId(), organizer);

        InOrder order = inOrder(checkIn, brigade, brigadierSync, alerts, stateService, evaluation);
        ArgumentCaptor<EventStatePayload> chi = ArgumentCaptor.forClass(EventStatePayload.class);
        order.verify(checkIn).pushState(chi.capture());
        order.verify(brigade).pushState(any(EventStatePayload.class));
        order.verify(brigadierSync).syncEvent(running.getId());
        order.verify(alerts).createAlert(any(AlertCommand.class), any(AlertActor.class));
        order.verify(stateService).transition(running.getId(), EventStatus.FINISHED, "org-1", "Finished by the user");
        order.verify(evaluation).evaluate(running.getId());
        assertEquals(EventStatus.FINISHED, chi.getValue().status());
        assertEquals(NOW, chi.getValue().endDate());
        assertEquals(metrics, response.evaluation());
        assertTrue(response.warnings().isEmpty());
        assertEquals("Scheduled drill", response.name());
    }

    @Test
    void theFinishAlertIsImmediateAndGoesToTheInvitedRoles() {
        EventEntity running = runningEvent();
        when(evaluation.evaluate(running.getId())).thenReturn(metrics(true, false));

        service.finish(running.getId(), organizer);

        ArgumentCaptor<AlertCommand> captor = ArgumentCaptor.forClass(AlertCommand.class);
        verify(alerts).createAlert(captor.capture(), any(AlertActor.class));
        AlertCommand command = captor.getValue();
        assertEquals(EventAlertPurpose.FINISH, command.purpose());
        assertTrue(command.activateImmediately());
        assertNull(command.activationAt());
        assertEquals(Set.of(Role.ESTUDIANTE), command.recipientRoles());
        ArgumentCaptor<EventAlertEntity> link = ArgumentCaptor.forClass(EventAlertEntity.class);
        verify(alertLinks).save(link.capture());
        assertEquals(EventAlertPurpose.FINISH, link.getValue().getPurpose());
    }

    @Test
    void onlyAnEventInProgressCanBeFinished() {
        EventEntity scheduled = scheduledEvent(START);

        assertThrows(InvalidStateTransitionException.class, () -> service.finish(scheduled.getId(), organizer));

        verify(checkIn, never()).pushState(any());
        verify(stateService, never()).transition(any(), any(), anyString(), any());
    }

    @Test
    void theRoleMustMatchTheKindOfEvent() {
        EventEntity drill = runningEvent();
        AccessActor coordinator = new AccessActor("coord-1", Set.of(Role.COORDINADOR));

        assertThrows(AccessDeniedException.class, () -> service.finish(drill.getId(), coordinator));

        verify(checkIn, never()).pushState(any());
    }

    @Test
    void anUnknownEventIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.finish(UUID.randomUUID(), organizer));
    }

    @Test
    void ifChiIsDownTheEventIsNotFinished() {
        EventEntity running = runningEvent();
        doThrow(down("MS04")).when(checkIn).pushState(any());

        assertThrows(ExternalServiceUnavailableException.class, () -> service.finish(running.getId(), organizer));

        verify(brigade, never()).pushState(any());
        verify(brigadierSync, never()).syncEvent(any());
        verify(stateService, never()).transition(any(), any(), anyString(), any());
        verify(evaluation, never()).evaluate(any());
    }

    @Test
    void ifBeiIsDownTheCheckInIsEnabledAgainAndTheEventIsNotFinished() {
        EventEntity running = runningEvent();
        doThrow(down("MS05")).when(brigade).pushState(any());

        assertThrows(ExternalServiceUnavailableException.class, () -> service.finish(running.getId(), organizer));

        ArgumentCaptor<EventStatePayload> chi = ArgumentCaptor.forClass(EventStatePayload.class);
        verify(checkIn, times(2)).pushState(chi.capture());
        assertEquals(EventStatus.FINISHED, chi.getAllValues().get(0).status());
        assertEquals(EventStatus.IN_PROGRESS, chi.getAllValues().get(1).status());
        verify(brigadierSync, never()).syncEvent(any());
        verify(alerts, never()).createAlert(any(), any(AlertActor.class));
        verify(stateService, never()).transition(any(), any(), anyString(), any());
    }

    @Test
    void ifTheFinalBrigadierCopyFailsTheEventIsFinishedWithAWarning() {
        EventEntity running = runningEvent();
        when(brigadierSync.syncEvent(running.getId())).thenThrow(down("MS05"));
        when(evaluation.evaluate(running.getId())).thenReturn(metrics(true, false));

        EventFinishResponse response = service.finish(running.getId(), organizer);

        verify(stateService).transition(running.getId(), EventStatus.FINISHED, "org-1", "Finished by the user");
        verify(alerts).createAlert(any(AlertCommand.class), any(AlertActor.class));
        assertEquals(List.of("BRIGADIERS_NOT_SYNCED"), codes(response));
    }

    @Test
    void ifAleIsDownTheEventIsFinishedAnyway() {
        EventEntity running = runningEvent();
        doThrow(down("MS03")).when(alerts).createAlert(any(AlertCommand.class), any(AlertActor.class));
        when(evaluation.evaluate(running.getId())).thenReturn(metrics(true, false));

        EventFinishResponse response = service.finish(running.getId(), organizer);

        verify(stateService).transition(running.getId(), EventStatus.FINISHED, "org-1", "Finished by the user");
        assertEquals(List.of("FINISH_ALERT_NOT_SENT"), codes(response));
        verify(alertLinks, never()).save(any());
    }

    @Test
    void incompleteMetricsAndMissingRecordsAreReported() {
        EventEntity running = runningEvent();
        when(evaluation.evaluate(running.getId())).thenReturn(metrics(false, false));
        assertEquals(List.of("EVALUATION_INCOMPLETE"), codes(service.finish(running.getId(), organizer)));

        EventEntity other = runningEvent();
        when(evaluation.evaluate(other.getId())).thenReturn(metrics(true, true));
        assertEquals(List.of("NO_RECORDS"), codes(service.finish(other.getId(), organizer)));
    }

    @Test
    void theEventStaysFinishedEvenIfTheEvaluationCannotBeSaved() {
        EventEntity running = runningEvent();
        when(evaluation.evaluate(running.getId())).thenThrow(new IllegalStateException("database error"));

        EventFinishResponse response = service.finish(running.getId(), organizer);

        assertNull(response.evaluation());
        assertEquals(List.of("EVALUATION_FAILED"), codes(response));
        verify(stateService).transition(running.getId(), EventStatus.FINISHED, "org-1", "Finished by the user");
    }
}
