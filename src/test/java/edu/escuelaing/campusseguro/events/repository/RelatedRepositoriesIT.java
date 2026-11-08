package edu.escuelaing.campusseguro.events.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.escuelaing.campusseguro.events.entity.EventAlertEntity;
import edu.escuelaing.campusseguro.events.entity.EventBrigadierEntity;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.entity.EventEvaluationEntity;
import edu.escuelaing.campusseguro.events.entity.EventParticipantEntity;
import edu.escuelaing.campusseguro.events.entity.EventScopeItemEntity;
import edu.escuelaing.campusseguro.events.entity.EventStartFailureEntity;
import edu.escuelaing.campusseguro.events.entity.EventStateHistoryEntity;
import edu.escuelaing.campusseguro.events.entity.PendingAuditEntity;
import edu.escuelaing.campusseguro.events.entity.enums.BrigadierStatus;
import edu.escuelaing.campusseguro.events.entity.enums.EventAlertPurpose;
import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.entity.enums.ExternalModule;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.security.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/** Tests the child tables of the event: mapping, unique constraints and cascade delete. */
class RelatedRepositoriesIT extends PostgresIntegrationTest {

    @Autowired
    private EventRepository events;
    @Autowired
    private EventScopeItemRepository scope;
    @Autowired
    private EventParticipantRepository participants;
    @Autowired
    private EventBrigadierRepository brigadiers;
    @Autowired
    private EventAlertRepository alerts;
    @Autowired
    private EventStateHistoryRepository history;
    @Autowired
    private EventEvaluationRepository evaluations;
    @Autowired
    private PendingAuditRepository audits;
    @Autowired
    private EventStartFailureRepository startFailures;
    @Autowired
    private TransactionTemplate tx;

    private UUID eventId;

    @BeforeEach
    void createEvent() {
        events.deleteAll();
        audits.deleteAll();
        EventEntity e = new EventEntity();
        e.setName("Evento");
        e.setStatus(EventStatus.SCHEDULED);
        e.setCreatedBy("tester");
        eventId = events.saveAndFlush(e).getId();
    }

    private EventScopeItemEntity scopeItem(ScopeType type, String ref) {
        EventScopeItemEntity s = new EventScopeItemEntity();
        s.setEventId(eventId);
        s.setScopeType(type);
        s.setScopeRefId(ref);
        return s;
    }

    @Test
    void scopeIsStoredAndReplacedByDeletingIt() {
        scope.saveAll(List.of(scopeItem(ScopeType.BUILDING, "b-1"), scopeItem(ScopeType.ZONE, "z-1")));
        assertEquals(2, scope.findByEventId(eventId).size());

        tx.executeWithoutResult(status -> scope.deleteByEventId(eventId));

        assertTrue(scope.findByEventId(eventId).isEmpty());
    }

    @Test
    void duplicatedScopeItemIsRejected() {
        scope.saveAndFlush(scopeItem(ScopeType.ZONE, "z-1"));

        assertThrows(DataIntegrityViolationException.class,
                () -> scope.saveAndFlush(scopeItem(ScopeType.ZONE, "z-1")));
    }

    @Test
    void participantsAreStoredAndReplacedByDeletingThem() {
        EventParticipantEntity p = new EventParticipantEntity();
        p.setEventId(eventId);
        p.setRole(Role.ESTUDIANTE);
        participants.saveAndFlush(p);
        assertEquals(1, participants.findByEventId(eventId).size());

        tx.executeWithoutResult(status -> participants.deleteByEventId(eventId));

        assertTrue(participants.findByEventId(eventId).isEmpty());
    }

    @Test
    void brigadiersAreSyncedOncePerEvent() {
        EventBrigadierEntity b = new EventBrigadierEntity();
        b.setEventId(eventId);
        b.setBrigadierId("u-1");
        b.setName("Ana");
        b.setZoneId("z-1");
        b.setStatus(BrigadierStatus.ACTIVE);
        b.setSyncedAt(Instant.now());
        brigadiers.saveAndFlush(b);

        assertTrue(brigadiers.findByEventIdAndBrigadierId(eventId, "u-1").isPresent());
        assertEquals(1, brigadiers.findByEventId(eventId).size());
        EventBrigadierEntity duplicate = new EventBrigadierEntity();
        duplicate.setEventId(eventId);
        duplicate.setBrigadierId("u-1");
        duplicate.setStatus(BrigadierStatus.INACTIVE);
        duplicate.setSyncedAt(Instant.now());
        assertThrows(DataIntegrityViolationException.class, () -> brigadiers.saveAndFlush(duplicate));
    }

    @Test
    void alertsAreFoundByPurpose() {
        for (EventAlertPurpose purpose : List.of(EventAlertPurpose.BRIGADISTS, EventAlertPurpose.GENERAL)) {
            EventAlertEntity a = new EventAlertEntity();
            a.setEventId(eventId);
            a.setAlertId("alert-" + purpose);
            a.setPurpose(purpose);
            alerts.saveAndFlush(a);
        }

        assertEquals(2, alerts.findByEventId(eventId).size());
        assertEquals(1, alerts.findByEventIdAndPurpose(eventId, EventAlertPurpose.GENERAL).size());
    }

    @Test
    void historyIsReturnedOldestFirst() {
        Instant now = Instant.now();
        history.saveAndFlush(change(null, EventStatus.DRAFT, now.minus(2, ChronoUnit.MINUTES)));
        history.saveAndFlush(change(EventStatus.DRAFT, EventStatus.SCHEDULED, now));

        List<EventStateHistoryEntity> found = history.findByEventIdOrderByChangedAtAsc(eventId);

        assertEquals(2, found.size());
        assertEquals(EventStatus.DRAFT, found.get(0).getToStatus());
        assertEquals(EventStatus.SCHEDULED, found.get(1).getToStatus());
    }

    private EventStateHistoryEntity change(EventStatus from, EventStatus to, Instant at) {
        EventStateHistoryEntity h = new EventStateHistoryEntity();
        h.setEventId(eventId);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setChangedBy("tester");
        h.setReason("prueba");
        h.setChangedAt(at);
        return h;
    }

    @Test
    void anEventHasAtMostOneEvaluation() {
        evaluations.saveAndFlush(evaluation());

        assertTrue(evaluations.findByEventId(eventId).isPresent());
        assertThrows(DataIntegrityViolationException.class, () -> evaluations.saveAndFlush(evaluation()));
    }

    @Test
    void evaluationsThatMissTheChiOrBeiNumbersAreFound() {
        assertTrue(evaluations.findByTotalCheckinsIsNullOrTotalIncidentsIsNull().isEmpty());

        EventEvaluationEntity partial = evaluation();
        partial.setTotalIncidents(null);
        evaluations.saveAndFlush(partial);

        List<EventEvaluationEntity> pending = evaluations.findByTotalCheckinsIsNullOrTotalIncidentsIsNull();
        assertEquals(1, pending.size());
        assertEquals(eventId, pending.get(0).getEventId());
    }

    private EventEvaluationEntity evaluation() {
        EventEvaluationEntity ev = new EventEvaluationEntity();
        ev.setEventId(eventId);
        ev.setEvaluatedAt(Instant.now());
        ev.setTotalCheckins(10);
        ev.setCheckinParticipationPct(new BigDecimal("83.33"));
        ev.setTotalIncidents(2);
        ev.setCasesAttended(2);
        ev.setCasesResolved(1);
        ev.setAvgAttentionSeconds(95L);
        ev.setDetails("{}");
        return ev;
    }

    @Test
    void pendingAuditReturnsOnlyUnsentRecordsOldestFirst() {
        Instant now = Instant.now();
        audits.saveAndFlush(audit("NEW", now));
        audits.saveAndFlush(audit("OLD", now.minus(1, ChronoUnit.HOURS)));
        PendingAuditEntity sent = audit("SENT", now.minus(2, ChronoUnit.HOURS));
        sent.setSentAt(now);
        audits.saveAndFlush(sent);

        List<PendingAuditEntity> pending = audits.findTop100BySentAtIsNullOrderByOccurredAtAsc();

        assertEquals(2, pending.size());
        assertEquals("OLD", pending.get(0).getAction());
    }

    private PendingAuditEntity audit(String action, Instant at) {
        PendingAuditEntity p = new PendingAuditEntity();
        p.setAction(action);
        p.setActor("tester");
        p.setEventId(eventId);
        p.setPayload("{}");
        p.setOccurredAt(at);
        p.setAttempts(0);
        return p;
    }

    private EventStartFailureEntity startFailure(ExternalModule module) {
        EventStartFailureEntity failure = new EventStartFailureEntity();
        failure.setEventId(eventId);
        failure.setModule(module);
        failure.setLastError("down");
        failure.setAttempts(1);
        return failure;
    }

    @Test
    void startFailuresAreStoredAndOnlyOpenOnesAreListed() {
        EventStartFailureEntity chi = startFailures.saveAndFlush(startFailure(ExternalModule.CHI));
        startFailures.saveAndFlush(startFailure(ExternalModule.BEI));

        chi.setResolvedAt(Instant.now());
        startFailures.saveAndFlush(chi);

        List<EventStartFailureEntity> open = startFailures.findByEventIdAndResolvedAtIsNull(eventId);
        assertEquals(1, open.size());
        assertEquals(ExternalModule.BEI, open.get(0).getModule());
        assertTrue(open.get(0).isOpen());
        assertEquals(1, startFailures.findByResolvedAtIsNull().size());
        assertTrue(startFailures.findByEventIdAndModule(eventId, ExternalModule.CHI).isPresent());
    }

    @Test
    void thereIsOnlyOneStartFailurePerEventAndModule() {
        startFailures.saveAndFlush(startFailure(ExternalModule.ALE));

        assertThrows(DataIntegrityViolationException.class,
                () -> startFailures.saveAndFlush(startFailure(ExternalModule.ALE)));
    }

    @Test
    void deletingTheEventRemovesItsChildren() {
        scope.saveAndFlush(scopeItem(ScopeType.ZONE, "z-9"));
        startFailures.saveAndFlush(startFailure(ExternalModule.CHI));
        history.saveAndFlush(change(null, EventStatus.DRAFT, Instant.now()));

        events.deleteById(eventId);
        events.flush();

        assertTrue(scope.findByEventId(eventId).isEmpty());
        assertTrue(history.findByEventIdOrderByChangedAtAsc(eventId).isEmpty());
        assertTrue(startFailures.findByEventIdAndResolvedAtIsNull(eventId).isEmpty());
    }
}
