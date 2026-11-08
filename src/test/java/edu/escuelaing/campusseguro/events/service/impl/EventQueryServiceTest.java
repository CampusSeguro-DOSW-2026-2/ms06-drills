package edu.escuelaing.campusseguro.events.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.escuelaing.campusseguro.events.client.brigade.BrigadeGateway;
import edu.escuelaing.campusseguro.events.client.brigade.IncidentIndicators;
import edu.escuelaing.campusseguro.events.client.checkin.CheckInGateway;
import edu.escuelaing.campusseguro.events.client.checkin.CheckInIndicators;
import edu.escuelaing.campusseguro.events.dto.response.EvaluationResponse;
import edu.escuelaing.campusseguro.events.dto.response.EventDetailResponse;
import edu.escuelaing.campusseguro.events.dto.response.EventSummaryResponse;
import edu.escuelaing.campusseguro.events.dto.response.EventWarning;
import edu.escuelaing.campusseguro.events.dto.response.PageResponse;
import edu.escuelaing.campusseguro.events.entity.EventBrigadierEntity;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.EventParticipantEntity;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Tests the event queries with mocked repositories, CHI and BEI. */
class EventQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-12-01T15:00:00Z");

    private final EventRepository events = mock(EventRepository.class);
    private final EventScopeItemRepository scopeItems = mock(EventScopeItemRepository.class);
    private final EventParticipantRepository participants = mock(EventParticipantRepository.class);
    private final EventAlertRepository alertLinks = mock(EventAlertRepository.class);
    private final EventBrigadierRepository brigadiers = mock(EventBrigadierRepository.class);
    private final EventEvaluationService evaluation = mock(EventEvaluationService.class);
    private final CheckInGateway checkIn = mock(CheckInGateway.class);
    private final BrigadeGateway brigade = mock(BrigadeGateway.class);
    private final TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
    private final EventQueryServiceImpl service = new EventQueryServiceImpl(
            events, scopeItems, participants, alertLinks, brigadiers, evaluation, checkIn, brigade,
            new EventMapper(), new EventAccessPolicy(), tx);

    private EventEntity running;

    @BeforeEach
    void setUp() {
        running = event("Running drill", EventStatus.IN_PROGRESS, true);
        when(alertLinks.findByEventId(any())).thenReturn(List.of());
        when(scopeItems.findByEventId(any())).thenReturn(List.of());
        when(participants.findByEventId(any())).thenReturn(List.of());
        when(brigadiers.findByEventId(any())).thenReturn(List.of());
    }

    private EventEntity event(String name, EventStatus status, boolean drill) {
        EventEntity event = new EventEntity();
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        event.setName(name);
        event.setStatus(status);
        event.setSimulacro(drill);
        event.setStartedAt(NOW);
        when(events.findById(event.getId())).thenReturn(Optional.of(event));
        return event;
    }

    private void invite(EventEntity event, Role... roles) {
        List<EventParticipantEntity> rows = java.util.Arrays.stream(roles).map(role -> {
            EventParticipantEntity row = new EventParticipantEntity();
            row.setEventId(event.getId());
            row.setRole(role);
            return row;
        }).toList();
        when(participants.findByEventId(event.getId())).thenReturn(rows);
    }

    private EventBrigadierEntity brigadier(String id, String name, String zone, BrigadierStatus status, Instant at) {
        EventBrigadierEntity row = new EventBrigadierEntity();
        row.setEventId(running.getId());
        row.setBrigadierId(id);
        row.setName(name);
        row.setZoneId(zone);
        row.setStatus(status);
        row.setSyncedAt(at);
        return row;
    }

    private void indicators(int checkins, int incidents) {
        when(checkIn.indicators(running.getId())).thenReturn(new CheckInIndicators(
                running.getId(), NOW, checkins, Map.of("SAFE", checkins), new BigDecimal("50.00")));
        when(brigade.indicators(running.getId())).thenReturn(new IncidentIndicators(
                running.getId(), NOW, incidents, 2, 1, 90L, List.of()));
    }

    private List<String> codes(EventDetailResponse detail) {
        return detail.monitoring().messages().stream().map(EventWarning::code).toList();
    }

    // ---- search ----

    @Test
    void searchEscapesTheTextAndAsksForAllStatusesByDefault() {
        when(events.search(any(), anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(running)));

        PageResponse<EventSummaryResponse> page = service.search("  100%_Drill! ", null, 0, 20);

        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(events).search(pattern.capture(), eq(EnumSet.allOf(EventStatus.class)), any(Pageable.class));
        assertEquals("%100!%!_drill!!%", pattern.getValue());
        assertEquals(1, page.content().size());
        assertEquals("Running drill", page.content().get(0).name());
    }

    @Test
    void searchWithAStatusAndNoTextKeepsOnlyThatStatus() {
        when(events.search(any(), anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<EventSummaryResponse> page = service.search(" ", EventStatus.FINISHED, 0, 10);

        verify(events).search(eq("%"), eq(Set.of(EventStatus.FINISHED)), any(Pageable.class));
        assertTrue(page.content().isEmpty());
        assertEquals(0, page.totalElements());
    }

    @Test
    void searchRejectsAWrongPageOrSize() {
        assertThrows(InvalidFieldException.class, () -> service.search(null, null, -1, 20));
        assertThrows(InvalidFieldException.class, () -> service.search(null, null, 0, 0));
        assertThrows(InvalidFieldException.class, () -> service.search(null, null, 0, 101));
    }

    // ---- detail ----

    @Test
    void aRunningEventShowsTheIndicatorsAndTheBrigadiersByZone() {
        indicators(4, 3);
        when(brigadiers.findByEventId(running.getId())).thenReturn(List.of(
                brigadier("b2", "Zoe", "z2", BrigadierStatus.ACTIVE, NOW),
                brigadier("b1", "Ana", "z1", BrigadierStatus.ACTIVE, NOW.minusSeconds(60)),
                brigadier("b3", "Bob", "z1", BrigadierStatus.INACTIVE, NOW.minusSeconds(30)),
                brigadier("b4", "Eva", null, BrigadierStatus.ACTIVE, NOW)));

        EventDetailResponse detail = service.detail(running.getId());

        assertEquals(4, detail.monitoring().checkIn().totalCheckins());
        assertEquals(3, detail.monitoring().incidents().totalIncidents());
        assertEquals(1, detail.monitoring().incidents().pending());
        assertEquals(NOW, detail.monitoring().brigadiersSyncedAt());
        assertEquals(java.util.Arrays.asList("z1", "z2", null),
                detail.monitoring().brigadiersByZone().stream().map(z -> z.zoneId()).toList());
        assertEquals(1, detail.monitoring().brigadiersByZone().get(0).activeCount());
        assertEquals(2, detail.monitoring().brigadiersByZone().get(0).brigadiers().size());
        assertTrue(detail.monitoring().messages().isEmpty());
        assertNull(detail.evaluation());
    }

    @Test
    void emptyIndicatorsExplainThatNothingIsRegisteredYet() {
        indicators(0, 0);

        EventDetailResponse detail = service.detail(running.getId());

        assertEquals(List.of("NO_CHECKINS", "NO_INCIDENTS", "NO_BRIGADIERS"), codes(detail));
        assertNull(detail.monitoring().brigadiersSyncedAt());
        assertTrue(detail.monitoring().brigadiersByZone().isEmpty());
    }

    @Test
    void whenChiIsDownTheRestOfTheMonitoringStillComes() {
        when(checkIn.indicators(running.getId())).thenThrow(new ExternalServiceUnavailableException("MS04", new RuntimeException()));
        when(brigade.indicators(running.getId())).thenReturn(new IncidentIndicators(
                running.getId(), NOW, 1, 0, 0, null, List.of()));
        when(brigadiers.findByEventId(running.getId()))
                .thenReturn(List.of(brigadier("b1", "Ana", "z1", BrigadierStatus.ACTIVE, NOW)));

        EventDetailResponse detail = service.detail(running.getId());

        assertNull(detail.monitoring().checkIn());
        assertNotNull(detail.monitoring().incidents());
        assertEquals(List.of("CHECKIN_UNAVAILABLE"), codes(detail));
        assertEquals(1, detail.monitoring().brigadiersByZone().size());
    }

    @Test
    void whenBeiIsDownTheIncidentsAreMarkedUnavailableAndTheLastBrigadierCopyIsShown() {
        when(checkIn.indicators(running.getId())).thenReturn(new CheckInIndicators(
                running.getId(), NOW, 2, null, null));
        when(brigade.indicators(running.getId())).thenThrow(new ExternalServiceUnavailableException("MS05", new RuntimeException()));
        when(brigadiers.findByEventId(running.getId()))
                .thenReturn(List.of(brigadier("b1", "Ana", "z1", BrigadierStatus.ACTIVE, NOW.minusSeconds(300))));

        EventDetailResponse detail = service.detail(running.getId());

        assertNull(detail.monitoring().incidents());
        assertTrue(detail.monitoring().checkIn().byStatus().isEmpty());
        assertEquals(List.of("INCIDENTS_UNAVAILABLE"), codes(detail));
        assertEquals(NOW.minusSeconds(300), detail.monitoring().brigadiersSyncedAt());
    }

    @Test
    void aFinishedEventShowsTheSavedEvaluationAndDoesNotCallChiOrBei() {
        EventEntity finished = event("Done", EventStatus.FINISHED, false);
        EvaluationResponse saved = new EvaluationResponse(
                finished.getId(), NOW, true, false, 5, new BigDecimal("80.00"), Map.of(), 1, 1, 1, 60L, List.of());
        when(evaluation.find(finished.getId())).thenReturn(Optional.of(saved));

        EventDetailResponse detail = service.detail(finished.getId());

        assertNull(detail.monitoring());
        assertEquals(saved, detail.evaluation());
        org.mockito.Mockito.verifyNoInteractions(checkIn, brigade);
    }

    @Test
    void aDraftHasNeitherMonitoringNorEvaluation() {
        EventEntity draft = event("Draft", EventStatus.DRAFT, false);

        EventDetailResponse detail = service.detail(draft.getId());

        assertNull(detail.monitoring());
        assertNull(detail.evaluation());
        assertEquals(EventStatus.DRAFT, detail.event().status());
    }

    @Test
    void anUnknownEventIsNotFound() {
        UUID id = UUID.randomUUID();
        when(events.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.detail(id));
    }

    // ---- active ----

    @Test
    void theActiveEventsAreTheOnesTheUserManagesOrIsInvitedTo() {
        EventEntity real = event("Real", EventStatus.IN_PROGRESS, false);
        EventEntity forStudents = event("For students", EventStatus.IN_PROGRESS, false);
        invite(forStudents, Role.ESTUDIANTE);
        when(events.findByStatusOrderByStartedAtDesc(EventStatus.IN_PROGRESS))
                .thenReturn(List.of(running, real, forStudents));

        List<EventSummaryResponse> student = service.active(new AccessActor("s", Set.of(Role.ESTUDIANTE)));
        List<EventSummaryResponse> organizer = service.active(new AccessActor("o", Set.of(Role.ORGANIZADOR)));
        List<EventSummaryResponse> coordinator = service.active(new AccessActor("c", Set.of(Role.COORDINADOR)));
        List<EventSummaryResponse> auditor = service.active(new AccessActor("a", Set.of(Role.AUDITOR)));

        assertEquals(List.of("For students"), student.stream().map(EventSummaryResponse::name).toList());
        assertEquals(List.of("Running drill"), organizer.stream().map(EventSummaryResponse::name).toList());
        assertEquals(List.of("Real", "For students"), coordinator.stream().map(EventSummaryResponse::name).toList());
        assertTrue(auditor.isEmpty());
    }
}