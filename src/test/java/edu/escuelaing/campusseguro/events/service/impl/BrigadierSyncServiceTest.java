package edu.escuelaing.campusseguro.events.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.escuelaing.campusseguro.events.client.brigade.BrigadeGateway;
import edu.escuelaing.campusseguro.events.client.brigade.BrigadierInfo;
import edu.escuelaing.campusseguro.events.client.brigade.BrigadierSnapshot;
import edu.escuelaing.campusseguro.events.entity.EventBrigadierEntity;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.enums.BrigadierStatus;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.repository.EventBrigadierRepository;
import edu.escuelaing.campusseguro.events.repository.EventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Tests the copy of the brigadiers from BEI with mocked repositories and gateway. */
class BrigadierSyncServiceTest {

    private static final Instant NOW = Instant.parse("2026-12-01T15:00:10Z");
    private static final Instant OLD = Instant.parse("2026-12-01T14:00:00Z");

    private final EventRepository events = mock(EventRepository.class);
    private final EventBrigadierRepository brigadiers = mock(EventBrigadierRepository.class);
    private final BrigadeGateway brigade = mock(BrigadeGateway.class);
    private final TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
    private final BrigadierSyncServiceImpl service = new BrigadierSyncServiceImpl(
            events, brigadiers, brigade, tx, Clock.fixed(NOW, ZoneOffset.UTC));

    private EventEntity event(EventStatus status) {
        EventEntity event = new EventEntity();
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        event.setStatus(status);
        when(events.findForUpdate(event.getId())).thenReturn(Optional.of(event));
        return event;
    }

    private void bei(EventEntity event, BrigadierInfo... infos) {
        when(brigade.brigadiers(event.getId()))
                .thenReturn(new BrigadierSnapshot(event.getId(), NOW, List.of(infos)));
    }

    private EventBrigadierEntity stored(EventEntity event, String brigadierId, String zone, BrigadierStatus status) {
        EventBrigadierEntity row = new EventBrigadierEntity();
        row.setEventId(event.getId());
        row.setBrigadierId(brigadierId);
        row.setName("Old name");
        row.setZoneId(zone);
        row.setStatus(status);
        row.setSyncedAt(OLD);
        return row;
    }

    @Test
    void copiesTheBrigadiersOfEveryRunningEvent() {
        EventEntity first = event(EventStatus.IN_PROGRESS);
        EventEntity second = event(EventStatus.IN_PROGRESS);
        when(events.findByStatusIn(List.of(EventStatus.IN_PROGRESS))).thenReturn(List.of(first, second));
        bei(first, new BrigadierInfo("u-1", "Ana", "z1", true));
        bei(second, new BrigadierInfo("u-2", "Luis", "z2", false));

        assertEquals(2, service.syncRunningEvents());

        ArgumentCaptor<EventBrigadierEntity> saved = ArgumentCaptor.forClass(EventBrigadierEntity.class);
        verify(brigadiers, times(2)).save(saved.capture());
        EventBrigadierEntity ana = saved.getAllValues().get(0);
        assertEquals(first.getId(), ana.getEventId());
        assertEquals("Ana", ana.getName());
        assertEquals("z1", ana.getZoneId());
        assertEquals(BrigadierStatus.ACTIVE, ana.getStatus());
        assertEquals(NOW, ana.getSyncedAt());
        assertEquals(BrigadierStatus.INACTIVE, saved.getAllValues().get(1).getStatus());
    }

    @Test
    void scheduledEventsAreSyncedByTheirOwnMethod() {
        EventEntity scheduled = event(EventStatus.SCHEDULED);
        when(events.findByStatusIn(List.of(EventStatus.SCHEDULED))).thenReturn(List.of(scheduled));
        bei(scheduled, new BrigadierInfo("u-1", "Ana", "z1", true));

        assertEquals(1, service.syncScheduledEvents());

        verify(events, never()).findByStatusIn(List.of(EventStatus.IN_PROGRESS));
        verify(brigadiers).save(any(EventBrigadierEntity.class));
    }

    @Test
    void updatesAnExistingBrigadierInsteadOfDuplicatingIt() {
        EventEntity running = event(EventStatus.IN_PROGRESS);
        when(events.findByStatusIn(List.of(EventStatus.IN_PROGRESS))).thenReturn(List.of(running));
        EventBrigadierEntity row = stored(running, "u-1", "z1", BrigadierStatus.ACTIVE);
        when(brigadiers.findByEventId(running.getId())).thenReturn(List.of(row));
        bei(running, new BrigadierInfo("u-1", "Ana", "z2", true));

        service.syncRunningEvents();

        verify(brigadiers).save(row);
        assertEquals("Ana", row.getName());
        assertEquals("z2", row.getZoneId());
        assertEquals(NOW, row.getSyncedAt());
    }

    @Test
    void aBrigadierThatBeiNoLongerListsBecomesInactive() {
        EventEntity running = event(EventStatus.IN_PROGRESS);
        when(events.findByStatusIn(List.of(EventStatus.IN_PROGRESS))).thenReturn(List.of(running));
        EventBrigadierEntity gone = stored(running, "u-9", "z1", BrigadierStatus.ACTIVE);
        when(brigadiers.findByEventId(running.getId())).thenReturn(List.of(gone));
        bei(running);

        service.syncRunningEvents();

        assertEquals(BrigadierStatus.INACTIVE, gone.getStatus());
        assertEquals(NOW, gone.getSyncedAt());
        verify(brigadiers).save(gone);
    }

    @Test
    void whenBeiIsDownTheLastDataIsKeptAndTheCycleStops() {
        EventEntity first = event(EventStatus.IN_PROGRESS);
        EventEntity second = event(EventStatus.IN_PROGRESS);
        when(events.findByStatusIn(List.of(EventStatus.IN_PROGRESS))).thenReturn(List.of(first, second));
        EventBrigadierEntity row = stored(first, "u-1", "z1", BrigadierStatus.ACTIVE);
        when(brigadiers.findByEventId(first.getId())).thenReturn(List.of(row));
        when(brigade.brigadiers(any(UUID.class)))
                .thenThrow(new ExternalServiceUnavailableException("MS05", new RuntimeException("down")));

        assertEquals(0, service.syncRunningEvents());

        verify(brigade, times(1)).brigadiers(any(UUID.class));
        verify(brigadiers, never()).save(any());
        assertEquals(OLD, row.getSyncedAt());
        assertEquals(BrigadierStatus.ACTIVE, row.getStatus());
    }

    @Test
    void nothingIsWrittenForAnEventFinishedWhileBeiWasAnswering() {
        EventEntity running = event(EventStatus.IN_PROGRESS);
        when(events.findByStatusIn(List.of(EventStatus.IN_PROGRESS))).thenReturn(List.of(running));
        bei(running, new BrigadierInfo("u-1", "Ana", "z1", true));
        running.setStatus(EventStatus.FINISHED);

        assertEquals(0, service.syncRunningEvents());

        verify(brigadiers, never()).save(any());
    }

    @Test
    void anErrorWithOneEventDoesNotStopTheOthers() {
        EventEntity broken = event(EventStatus.IN_PROGRESS);
        EventEntity healthy = event(EventStatus.IN_PROGRESS);
        when(events.findByStatusIn(List.of(EventStatus.IN_PROGRESS))).thenReturn(List.of(broken, healthy));
        when(brigade.brigadiers(broken.getId())).thenThrow(new IllegalStateException("bad answer"));
        bei(healthy, new BrigadierInfo("u-1", "Ana", "z1", true));

        assertEquals(1, service.syncRunningEvents());

        verify(brigadiers).save(any(EventBrigadierEntity.class));
    }

    @Test
    void anEventWithoutBrigadiersIsStillSynced() {
        EventEntity running = event(EventStatus.IN_PROGRESS);
        when(events.findByStatusIn(List.of(EventStatus.IN_PROGRESS))).thenReturn(List.of(running));
        bei(running);

        assertEquals(1, service.syncRunningEvents());

        verify(brigadiers, never()).save(any());
    }
}
