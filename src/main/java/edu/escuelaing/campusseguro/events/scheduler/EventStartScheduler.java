package edu.escuelaing.campusseguro.events.scheduler;

import edu.escuelaing.campusseguro.events.service.EventLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Checks every few seconds if an event has to start (RF-EVE-05). It has no rules of its own:
 * the work is done by {@link EventLifecycleService}.
 *
 * <p>It can be turned off with {@code app.scheduling.enabled=false} (the integration tests do it).
 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class EventStartScheduler {

    private static final Logger log = LoggerFactory.getLogger(EventStartScheduler.class);

    private final EventLifecycleService lifecycle;

    public EventStartScheduler(EventLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    /** Starts the events whose date has arrived and repeats the start calls that failed. */
    @Scheduled(fixedDelayString = "${app.events.start-check-ms:10000}")
    public void run() {
        try {
            int started = lifecycle.startDueEvents();
            if (started > 0) {
                log.info("Started {} event(s)", started);
            }
        } catch (RuntimeException ex) {
            log.error("The check for events to start failed", ex);
        }
        try {
            lifecycle.retryPendingStartCalls();
        } catch (RuntimeException ex) {
            log.error("The retry of pending start calls failed", ex);
        }
    }
}
