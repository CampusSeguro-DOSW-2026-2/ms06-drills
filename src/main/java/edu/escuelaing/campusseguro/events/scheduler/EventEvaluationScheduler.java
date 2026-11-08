package edu.escuelaing.campusseguro.events.scheduler;

import edu.escuelaing.campusseguro.events.service.EventEvaluationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Completes the evaluations that were saved while CHI or BEI were down. It has no rules of its
 * own: the work is done by {@link EventEvaluationService}.
 *
 * <p>It can be turned off with {@code app.scheduling.enabled=false} (the integration tests do it).
 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class EventEvaluationScheduler {

    private static final Logger log = LoggerFactory.getLogger(EventEvaluationScheduler.class);

    private final EventEvaluationService evaluation;

    public EventEvaluationScheduler(EventEvaluationService evaluation) {
        this.evaluation = evaluation;
    }

    /** Asks CHI and BEI again for the missing numbers. */
    @Scheduled(fixedDelayString = "${app.events.evaluation-retry-ms:30000}")
    public void completePending() {
        try {
            evaluation.completePendingEvaluations();
        } catch (RuntimeException ex) {
            log.error("Completing the pending evaluations failed", ex);
        }
    }
}
