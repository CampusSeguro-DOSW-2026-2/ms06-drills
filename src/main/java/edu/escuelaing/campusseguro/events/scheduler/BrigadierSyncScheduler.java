package edu.escuelaing.campusseguro.events.scheduler;

import edu.escuelaing.campusseguro.events.service.BrigadierSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the brigadier sync: events In progress every 10 s and Scheduled events every 30 s.
 * It has no rules of its own: the work is done by {@link BrigadierSyncService}.
 *
 * <p>It can be turned off with {@code app.scheduling.enabled=false} (the integration tests do it).
 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class BrigadierSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(BrigadierSyncScheduler.class);

    private final BrigadierSyncService sync;

    public BrigadierSyncScheduler(BrigadierSyncService sync) {
        this.sync = sync;
    }

    /** Events that are In progress. */
    @Scheduled(fixedDelayString = "${app.events.bei-sync-running-ms:10000}")
    public void syncRunning() {
        try {
            sync.syncRunningEvents();
        } catch (RuntimeException ex) {
            log.error("The brigadier sync of the running events failed", ex);
        }
    }

    /** Events that are Scheduled. */
    @Scheduled(fixedDelayString = "${app.events.bei-sync-scheduled-ms:30000}")
    public void syncScheduled() {
        try {
            sync.syncScheduledEvents();
        } catch (RuntimeException ex) {
            log.error("The brigadier sync of the scheduled events failed", ex);
        }
    }
}
