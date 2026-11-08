package edu.escuelaing.campusseguro.events.service.impl;

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
import edu.escuelaing.campusseguro.events.service.BrigadierSyncService;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Default implementation of {@link BrigadierSyncService}. */
@Service
public class BrigadierSyncServiceImpl implements BrigadierSyncService {

    private static final Logger log = LoggerFactory.getLogger(BrigadierSyncServiceImpl.class);

    private final EventRepository events;
    private final EventBrigadierRepository brigadiers;
    private final BrigadeGateway brigade;
    private final TransactionTemplate tx;
    private final Clock clock;

    public BrigadierSyncServiceImpl(
            EventRepository events,
            EventBrigadierRepository brigadiers,
            BrigadeGateway brigade,
            TransactionTemplate tx,
            Clock clock) {
        this.events = events;
        this.brigadiers = brigadiers;
        this.brigade = brigade;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public int syncRunningEvents() {
        return syncAll(EventStatus.IN_PROGRESS);
    }

    @Override
    public int syncScheduledEvents() {
        return syncAll(EventStatus.SCHEDULED);
    }

    @Override
    public boolean syncEvent(UUID eventId) {
        return copy(eventId, EventStatus.IN_PROGRESS);
    }

    /**
     * Syncs every event in the given status, one at a time. If BEI does not answer the cycle
     * stops, because BEI is down for all the events and each call would wait for the timeout.
     * An error with one event does not stop the others.
     */
    private int syncAll(EventStatus status) {
        List<UUID> ids = events.findByStatusIn(List.of(status)).stream().map(EventEntity::getId).toList();
        int synced = 0;
        for (UUID eventId : ids) {
            try {
                if (copy(eventId, status)) {
                    synced++;
                }
            } catch (ExternalServiceUnavailableException ex) {
                log.warn("BEI did not answer: the brigadiers of {} event(s) keep their last data", ids.size() - synced);
                break;
            } catch (RuntimeException ex) {
                log.error("Could not sync the brigadiers of event {}", eventId, ex);
            }
        }
        return synced;
    }

    /**
     * Asks BEI first (no database transaction is open during the call) and then writes the answer.
     *
     * @throws ExternalServiceUnavailableException if BEI does not answer
     */
    private boolean copy(UUID eventId, EventStatus expected) {
        BrigadierSnapshot snapshot = brigade.brigadiers(eventId);
        return Boolean.TRUE.equals(tx.execute(status -> apply(eventId, expected, snapshot)));
    }

    /**
     * Copies the answer of BEI. The event is locked and its status checked again, so nothing is
     * written after the event was finished (finishing freezes the brigadiers).
     */
    private boolean apply(UUID eventId, EventStatus expected, BrigadierSnapshot snapshot) {
        EventEntity event = events.findForUpdate(eventId).orElse(null);
        if (event == null || event.getStatus() != expected) {
            return false;
        }
        Instant now = clock.instant();
        Map<String, EventBrigadierEntity> current = new HashMap<>();
        brigadiers.findByEventId(eventId).forEach(row -> current.put(row.getBrigadierId(), row));

        Set<String> inSnapshot = new HashSet<>();
        for (BrigadierInfo info : snapshot.brigadiers()) {
            inSnapshot.add(info.brigadierId());
            EventBrigadierEntity row = current.getOrDefault(info.brigadierId(), new EventBrigadierEntity());
            row.setEventId(eventId);
            row.setBrigadierId(info.brigadierId());
            row.setName(info.name());
            row.setZoneId(info.zoneId());
            row.setStatus(info.active() ? BrigadierStatus.ACTIVE : BrigadierStatus.INACTIVE);
            row.setSyncedAt(now);
            brigadiers.save(row);
        }
        // A brigadier that BEI no longer lists removed the assignment: it stays as inactive.
        current.values().stream()
                .filter(row -> !inSnapshot.contains(row.getBrigadierId()))
                .forEach(row -> {
                    row.setStatus(BrigadierStatus.INACTIVE);
                    row.setSyncedAt(now);
                    brigadiers.save(row);
                });
        return true;
    }
}
