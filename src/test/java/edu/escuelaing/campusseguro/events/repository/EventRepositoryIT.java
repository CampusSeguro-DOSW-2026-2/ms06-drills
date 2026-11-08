package edu.escuelaing.campusseguro.events.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.EventTypeEntity;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** Class that tests the event repository and the type catalog against a real PostgreSQL. */
class EventRepositoryIT extends PostgresIntegrationTest {

    @Autowired
    private EventRepository events;

    @Autowired
    private EventTypeRepository types;

    @Autowired
    private TransactionTemplate tx;

    @BeforeEach
    void clean() {
        events.deleteAll();
    }

    private EventEntity event(String name, EventStatus status, Instant start) {
        EventEntity e = new EventEntity();
        e.setName(name);
        e.setStatus(status);
        e.setSimulacro(false);
        e.setScheduledStart(start);
        e.setCreatedBy("tester");
        return events.saveAndFlush(e);
    }

    @Test
    void theSevenPredefinedTypesAreSeeded() {
        List<EventTypeEntity> active = types.findByActiveTrueOrderByNameAsc();

        assertEquals(7, active.size());
        assertTrue(types.findByCode(EventTypeEntity.SIMULACRO_CODE).isPresent());
        assertTrue(types.existsByCode("SISMO"));
        assertTrue(types.existsByNameIgnoreCase("fuga de sustancias químicas"));
        assertFalse(types.existsByCode("NO_EXISTE"));
    }

    @Test
    void findsTypesByIdentifier() {
        EventTypeEntity sismo = types.findByCode("SISMO").orElseThrow();

        List<EventTypeEntity> found = types.findByIdIn(List.of(sismo.getId(), UUID.randomUUID()));

        assertEquals(1, found.size());
    }

    @Test
    void savesAnEventWithItsTypesAndFillsAuditFields() {
        EventTypeEntity simulacro = types.findByCode(EventTypeEntity.SIMULACRO_CODE).orElseThrow();
        EventEntity e = new EventEntity();
        e.setName("Simulacro bloque G");
        e.setStatus(EventStatus.DRAFT);
        e.setSimulacro(true);
        e.setCreatedBy("org-1");
        e.setTypes(new HashSet<>(Set.of(simulacro)));

        EventEntity saved = events.saveAndFlush(e);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertEquals(0L, saved.getVersion());
        int typeCount = tx.execute(status -> events.findById(saved.getId()).orElseThrow().getTypes().size());
        assertEquals(1, typeCount);
    }

    @Test
    void updateRefreshesTheVersionAndTheModificationDate() {
        EventEntity saved = event("Sismo", EventStatus.DRAFT, null);
        Instant before = saved.getUpdatedAt();

        saved.setName("Sismo editado");
        EventEntity updated = events.saveAndFlush(saved);

        assertEquals(1L, updated.getVersion());
        assertFalse(updated.getUpdatedAt().isBefore(before));
    }

    @Test
    void severalEventsCanBeInProgressAtTheSameTime() {
        event("Real", EventStatus.IN_PROGRESS, null);
        event("Drill", EventStatus.IN_PROGRESS, null);

        List<EventEntity> running = events.findByStatusIn(List.of(EventStatus.IN_PROGRESS));

        assertEquals(2, running.size());
    }

    @Test
    void findsScheduledEventsWhoseStartTimeArrived() {
        Instant now = Instant.now();
        EventEntity due = event("Vencido", EventStatus.SCHEDULED, now.minus(1, ChronoUnit.MINUTES));
        event("Futuro", EventStatus.SCHEDULED, now.plus(1, ChronoUnit.HOURS));
        event("Borrador", EventStatus.DRAFT, now.minus(1, ChronoUnit.MINUTES));

        List<EventEntity> found = events.findByStatusAndScheduledStartLessThanEqual(EventStatus.SCHEDULED, now);

        assertEquals(1, found.size());
        assertEquals(due.getId(), found.get(0).getId());
    }

    @Test
    void findsEventsInSeveralStatesAndLocksOneForUpdate() {
        EventEntity a = event("A", EventStatus.SCHEDULED, Instant.now().plus(1, ChronoUnit.HOURS));
        event("B", EventStatus.IN_PROGRESS, null);
        event("C", EventStatus.FINISHED, null);

        List<EventEntity> active = events.findByStatusIn(List.of(EventStatus.SCHEDULED, EventStatus.IN_PROGRESS));
        boolean locked = Boolean.TRUE.equals(tx.execute(status -> events.findForUpdate(a.getId()).isPresent()));

        assertEquals(2, active.size());
        assertTrue(locked);
    }
}
