package edu.escuelaing.campusseguro.events.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.escuelaing.campusseguro.events.client.brigade.BrigadeGateway;
import edu.escuelaing.campusseguro.events.client.brigade.BrigadierPerformance;
import edu.escuelaing.campusseguro.events.client.brigade.IncidentIndicators;
import edu.escuelaing.campusseguro.events.client.checkin.CheckInGateway;
import edu.escuelaing.campusseguro.events.client.checkin.CheckInIndicators;
import edu.escuelaing.campusseguro.events.dto.response.EvaluationResponse;
import edu.escuelaing.campusseguro.events.entity.EventEvaluationEntity;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.repository.EventEvaluationRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

/** Tests the evaluation snapshot with mocked gateways and a real JSON mapper. */
class EventEvaluationServiceTest {

    private static final Instant NOW = Instant.parse("2026-12-01T16:00:00Z");
    private static final Instant CALCULATED = Instant.parse("2026-12-01T15:59:00Z");

    private final EventEvaluationRepository evaluations = mock(EventEvaluationRepository.class);
    private final CheckInGateway checkIn = mock(CheckInGateway.class);
    private final BrigadeGateway brigade = mock(BrigadeGateway.class);
    private final EventEvaluationServiceImpl service = new EventEvaluationServiceImpl(
            evaluations, checkIn, brigade, JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID eventId = UUID.randomUUID();

    private static ExternalServiceUnavailableException down(String service) {
        return new ExternalServiceUnavailableException(service, new RuntimeException("down"));
    }

    private CheckInIndicators chi(int total) {
        return new CheckInIndicators(eventId, CALCULATED, total, Map.of("CONFIRMED", total), new BigDecimal("80.456"));
    }

    private IncidentIndicators bei(int incidents) {
        return new IncidentIndicators(eventId, CALCULATED, incidents, incidents, incidents, 120L,
                incidents == 0 ? List.of() : List.of(new BrigadierPerformance("u-1", incidents, incidents)));
    }

    @BeforeEach
    void setUp() {
        when(evaluations.findByEventId(eventId)).thenReturn(Optional.empty());
        when(evaluations.save(any(EventEvaluationEntity.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void savesTheChiAndBeiNumbersTakenAtFinishTime() {
        when(checkIn.indicators(eventId)).thenReturn(chi(8));
        when(brigade.indicators(eventId)).thenReturn(bei(3));

        EvaluationResponse response = service.evaluate(eventId);

        assertTrue(response.complete());
        assertFalse(response.noRecords());
        assertEquals(NOW, response.evaluatedAt());
        assertEquals(8, response.totalCheckins());
        assertEquals(new BigDecimal("80.46"), response.checkinParticipationPct());
        assertEquals(Map.of("CONFIRMED", 8), response.checkInByStatus());
        assertEquals(3, response.totalIncidents());
        assertEquals(3, response.casesAttended());
        assertEquals(3, response.casesResolved());
        assertEquals(120L, response.avgAttentionSeconds());
        assertEquals(List.of(new EvaluationResponse.BrigadierResult("u-1", 3, 3)), response.brigadiers());
    }

    @Test
    void withoutAnyRecordsTheEvaluationSaysSo() {
        when(checkIn.indicators(eventId)).thenReturn(chi(0));
        when(brigade.indicators(eventId)).thenReturn(bei(0));

        EvaluationResponse response = service.evaluate(eventId);

        assertTrue(response.complete());
        assertTrue(response.noRecords());
    }

    @Test
    void ifChiIsDownTheCheckInNumbersStayEmpty() {
        when(checkIn.indicators(eventId)).thenThrow(down("MS04"));
        when(brigade.indicators(eventId)).thenReturn(bei(2));

        EvaluationResponse response = service.evaluate(eventId);

        assertFalse(response.complete());
        assertNull(response.totalCheckins());
        assertEquals(2, response.totalIncidents());
        assertFalse(response.noRecords());
        verify(evaluations).save(any(EventEvaluationEntity.class));
    }

    @Test
    void ifBeiIsDownTheIncidentNumbersStayEmpty() {
        when(checkIn.indicators(eventId)).thenReturn(chi(5));
        when(brigade.indicators(eventId)).thenThrow(down("MS05"));

        EvaluationResponse response = service.evaluate(eventId);

        assertFalse(response.complete());
        assertEquals(5, response.totalCheckins());
        assertNull(response.totalIncidents());
        assertTrue(response.brigadiers().isEmpty());
    }

    @Test
    void anExistingEvaluationIsReturnedWithoutAskingAgain() {
        EventEvaluationEntity saved = new EventEvaluationEntity();
        saved.setEventId(eventId);
        saved.setEvaluatedAt(CALCULATED);
        saved.setTotalCheckins(4);
        saved.setTotalIncidents(1);
        saved.setCasesAttended(1);
        saved.setCasesResolved(0);
        saved.setDetails("{}");
        when(evaluations.findByEventId(eventId)).thenReturn(Optional.of(saved));

        EvaluationResponse response = service.evaluate(eventId);

        assertEquals(CALCULATED, response.evaluatedAt());
        assertEquals(4, response.totalCheckins());
        verify(checkIn, never()).indicators(any());
        verify(brigade, never()).indicators(any());
        verify(evaluations, never()).save(any());
    }

    @Test
    void unreadableDetailsGiveAnEmptyListInsteadOfAnError() {
        EventEvaluationEntity saved = new EventEvaluationEntity();
        saved.setEventId(eventId);
        saved.setEvaluatedAt(CALCULATED);
        saved.setTotalCheckins(1);
        saved.setTotalIncidents(1);
        saved.setDetails("this is not json");
        when(evaluations.findByEventId(eventId)).thenReturn(Optional.of(saved));

        EvaluationResponse response = service.evaluate(eventId);

        assertTrue(response.brigadiers().isEmpty());
        assertNotNull(response.evaluatedAt());
    }

    @Test
    void theMissingNumbersAreCompletedLater() {
        when(checkIn.indicators(eventId)).thenThrow(down("MS04"));
        when(brigade.indicators(eventId)).thenReturn(bei(2));
        ArgumentCaptor<EventEvaluationEntity> captor = ArgumentCaptor.forClass(EventEvaluationEntity.class);
        service.evaluate(eventId);
        verify(evaluations).save(captor.capture());
        EventEvaluationEntity partial = captor.getValue();
        assertNull(partial.getTotalCheckins());

        when(evaluations.findByTotalCheckinsIsNullOrTotalIncidentsIsNull()).thenReturn(List.of(partial));
        // doReturn: calling checkIn.indicators(...) inside when(...) would trigger the exception set above.
        doReturn(chi(6)).when(checkIn).indicators(eventId);

        assertEquals(1, service.completePendingEvaluations());

        assertEquals(6, partial.getTotalCheckins());
        assertEquals(2, partial.getTotalIncidents());
        // BEI is not asked again: its numbers were already saved.
        verify(brigade).indicators(eventId);
    }

    @Test
    void anEvaluationThatStaysIncompleteIsNotCounted() {
        EventEvaluationEntity partial = new EventEvaluationEntity();
        partial.setEventId(eventId);
        partial.setEvaluatedAt(CALCULATED);
        when(evaluations.findByTotalCheckinsIsNullOrTotalIncidentsIsNull()).thenReturn(List.of(partial));
        when(checkIn.indicators(eventId)).thenThrow(down("MS04"));
        when(brigade.indicators(eventId)).thenThrow(down("MS05"));

        assertEquals(0, service.completePendingEvaluations());

        assertNull(partial.getTotalCheckins());
    }
}