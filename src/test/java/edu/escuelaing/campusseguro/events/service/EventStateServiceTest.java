package edu.escuelaing.campusseguro.events.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.EventStateHistoryEntity;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.exception.InvalidStateTransitionException;
import edu.escuelaing.campusseguro.events.exception.ResourceNotFoundException;
import edu.escuelaing.campusseguro.events.repository.EventRepository;
import edu.escuelaing.campusseguro.events.repository.EventStateHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** Tests status changes, their dates and the history, with mocked repositories. */
class EventStateServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T14:00:00Z");

    private final EventRepository events = mock(EventRepository.class);
    private final EventStateHistoryRepository history = mock(EventStateHistoryRepository.class);
    private final EventStateService service = new EventStateService(
            events, history, new EventStateMachine(), Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID id = UUID.randomUUID();
    private EventEntity event;

    @BeforeEach
    void setUp() {
        event = new EventEntity();
        ReflectionTestUtils.setField(event, "id", id);
        event.setStatus(EventStatus.DRAFT);
        when(events.findForUpdate(id)).thenReturn(Optional.of(event));
        when(events.save(any(EventEntity.class))).thenAnswer(call -> call.getArgument(0));
    }

    private EventStateHistoryEntity savedEntry() {
        ArgumentCaptor<EventStateHistoryEntity> captor = ArgumentCaptor.forClass(EventStateHistoryEntity.class);
        verify(history).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void schedulingChangesTheStatusAndWritesTheHistory() {
        EventEntity result = service.transition(id, EventStatus.SCHEDULED, "coord-1", "all data complete");

        assertSame(event, result);
        assertEquals(EventStatus.SCHEDULED, result.getStatus());
        assertNull(result.getStartedAt());
        EventStateHistoryEntity entry = savedEntry();
        assertEquals(id, entry.getEventId());
        assertEquals(EventStatus.DRAFT, entry.getFromStatus());
        assertEquals(EventStatus.SCHEDULED, entry.getToStatus());
        assertEquals("coord-1", entry.getChangedBy());
        assertEquals("all data complete", entry.getReason());
        assertEquals(NOW, entry.getChangedAt());
    }

    @Test
    void startingSetsTheRealStartTime() {
        event.setStatus(EventStatus.SCHEDULED);

        EventEntity result = service.transition(id, EventStatus.IN_PROGRESS, "system", null);

        assertEquals(NOW, result.getStartedAt());
        assertNull(result.getEndedAt());
        assertNull(savedEntry().getReason());
    }

    @Test
    void finishingSetsTheRealEndTime() {
        event.setStatus(EventStatus.IN_PROGRESS);

        EventEntity result = service.transition(id, EventStatus.FINISHED, "coord-1", null);

        assertEquals(NOW, result.getEndedAt());
    }

    @Test
    void goingBackToDraftKeepsTheDatesEmpty() {
        event.setStatus(EventStatus.SCHEDULED);

        EventEntity result = service.transition(id, EventStatus.DRAFT, "coord-1", "start date removed");

        assertEquals(EventStatus.DRAFT, result.getStatus());
        assertNull(result.getStartedAt());
        assertNull(result.getEndedAt());
    }

    @Test
    void aNotAllowedChangeSavesNothing() {
        assertThrows(InvalidStateTransitionException.class,
                () -> service.transition(id, EventStatus.FINISHED, "coord-1", null));

        verify(events, never()).save(any());
        verify(history, never()).save(any());
        assertEquals(EventStatus.DRAFT, event.getStatus());
    }

    @Test
    void anUnknownEventIsNotFound() {
        UUID other = UUID.randomUUID();
        when(events.findForUpdate(other)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.transition(other, EventStatus.SCHEDULED, "coord-1", null));
        verify(history, never()).save(any());
    }

    @Test
    void aLongReasonIsCutToTheColumnSize() {
        String longReason = "x".repeat(500);

        service.transition(id, EventStatus.SCHEDULED, "coord-1", longReason);

        assertEquals(300, savedEntry().getReason().length());
    }

    @Test
    void creationIsRecordedWithoutPreviousStatus() {
        event.setStatus(EventStatus.SCHEDULED);

        service.recordCreation(event, "coord-1");

        EventStateHistoryEntity entry = savedEntry();
        assertNull(entry.getFromStatus());
        assertEquals(EventStatus.SCHEDULED, entry.getToStatus());
        assertEquals(id, entry.getEventId());
    }

    @Test
    void historyIsReadFromTheRepository() {
        EventStateHistoryEntity entry = new EventStateHistoryEntity();
        when(events.existsById(id)).thenReturn(true);
        when(history.findByEventIdOrderByChangedAtAsc(id)).thenReturn(List.of(entry));

        assertEquals(List.of(entry), service.history(id));
    }

    @Test
    void historyOfAnUnknownEventIsNotFound() {
        when(events.existsById(id)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.history(id));
    }
}
