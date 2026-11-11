package edu.escuelaing.campusseguro.events.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.escuelaing.campusseguro.events.client.audit.AuditRecord;
import edu.escuelaing.campusseguro.events.client.audit.StubAuditGateway;
import edu.escuelaing.campusseguro.events.dto.request.CreateEventRequest;
import edu.escuelaing.campusseguro.events.dto.request.ScopeItemRequest;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.PendingAuditEntity;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.repository.EventRepository;
import edu.escuelaing.campusseguro.events.repository.EventTypeRepository;
import edu.escuelaing.campusseguro.events.repository.PendingAuditRepository;
import edu.escuelaing.campusseguro.events.repository.PostgresIntegrationTest;
import edu.escuelaing.campusseguro.events.security.AccessActor;
import edu.escuelaing.campusseguro.events.security.Role;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Audit outbox against a real database: records are written with the action and sent to the MS08 stub. */
class AuditOutboxIT extends PostgresIntegrationTest {

    @Autowired
    private EventCreationService creation;
    @Autowired
    private EventLifecycleService lifecycle;
    @Autowired
    private AuditService delivery;
    @Autowired
    private PendingAuditRepository outbox;
    @Autowired
    private EventRepository events;
    @Autowired
    private EventTypeRepository types;
    @Autowired
    private StubAuditGateway auditStub;

    private final AccessActor coordinator = new AccessActor("coord-audit", Set.of(Role.COORDINADOR));

    /**
     * The database is shared by all integration tests, so old pending records of other tests may exist.
     * The delivery sends the oldest records first and stops at the first failure, so those old records
     * would hide the records of these tests. Starting from an empty outbox makes the tests independent.
     */
    @BeforeEach
    void emptyTheOutbox() {
        outbox.deleteAll();
        auditStub.clear();
    }

    @AfterEach
    void reset() {
        auditStub.clear();
    }

    private UUID createScheduledEvent(String name) {
        CreateEventRequest request = new CreateEventRequest(
                name, null, Set.of(types.findByCode("SISMO").orElseThrow().getId()),
                Instant.now().plus(2, ChronoUnit.DAYS),
                List.of(new ScopeItemRequest(ScopeType.ZONE, "z1")), Set.of(Role.ESTUDIANTE));
        return creation.create(request, coordinator).id();
    }

    private List<PendingAuditEntity> rowsOf(UUID eventId) {
        return outbox.findAll().stream().filter(row -> eventId.equals(row.getEventId()))
                .sorted((a, b) -> a.getOccurredAt().compareTo(b.getOccurredAt())).toList();
    }

    @Test
    void creatingAnEventLeavesTheCreationAndTheSchedulingInTheOutbox() {
        UUID eventId = createScheduledEvent("Audited creation");

        List<PendingAuditEntity> rows = rowsOf(eventId);

        assertTrue(rows.stream().anyMatch(row -> row.getAction().equals("EVENT_CREATED")));
        assertTrue(rows.stream().anyMatch(row -> row.getAction().equals("EVENT_STATUS_CHANGED")));
        assertTrue(rows.stream().allMatch(row -> row.getActor().equals("coord-audit") && row.getSentAt() == null));
    }

    @Test
    void theRecordsAreSentToMs08AndMarkedAsSent() {
        UUID eventId = createScheduledEvent("Audited delivery");

        delivery.deliverPending();

        List<AuditRecord> sent = auditStub.sent().stream()
                .filter(record -> record.evento() != null && eventId.equals(record.evento().id())).toList();
        assertTrue(sent.stream().anyMatch(record -> "CREATION".equals(record.tipoRegistro())
                && "EVENT_CREATED".equals(record.accion()) && "MS06".equals(record.origen())
                && "Audited delivery".equals(record.evento().nombre())));
        assertTrue(rowsOf(eventId).stream().allMatch(row -> row.getSentAt() != null));
    }

    @Test
    void whenMs08IsDownTheRecordsStayPendingAndAreSentWhenItComesBack() {
        UUID eventId = createScheduledEvent("Audited outage");
        auditStub.simulateOutage(true);

        delivery.deliverPending();

        List<PendingAuditEntity> waiting = rowsOf(eventId);
        assertTrue(waiting.stream().allMatch(row -> row.getSentAt() == null));
        assertTrue(waiting.stream().anyMatch(row -> row.getAttempts() >= 1 && row.getLastError() != null));

        auditStub.simulateOutage(false);
        delivery.deliverPending();

        assertTrue(rowsOf(eventId).stream().allMatch(row -> row.getSentAt() != null && row.getLastError() == null));
    }

    @Test
    void finishingAnEventIsAuditedAsFinalization() {
        UUID eventId = createScheduledEvent("Audited finish");
        EventEntity event = events.findById(eventId).orElseThrow();
        event.setScheduledStart(Instant.now().minusSeconds(30));
        events.saveAndFlush(event);
        lifecycle.startDueEvents();

        lifecycle.finish(eventId, coordinator);

        PendingAuditEntity last = rowsOf(eventId).stream()
                .filter(row -> row.getAction().equals("EVENT_FINISHED")).findFirst().orElseThrow();
        assertNotNull(last.getPayload());
        assertTrue(last.getPayload().contains("FINALIZATION"));
        assertNull(last.getSentAt());
    }
}
