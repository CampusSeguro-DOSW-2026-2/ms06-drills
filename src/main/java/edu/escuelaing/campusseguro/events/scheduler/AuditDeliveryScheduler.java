package edu.escuelaing.campusseguro.events.scheduler;

import edu.escuelaing.campusseguro.events.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends the pending audit records to MS08. It has no rules of its own: the work is done by
 * {@link AuditService}.
 *
 * <p>It can be turned off with {@code app.scheduling.enabled=false} (the integration tests do it).
 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class AuditDeliveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditDeliveryScheduler.class);

    private final AuditService audit;

    public AuditDeliveryScheduler(AuditService audit) {
        this.audit = audit;
    }

    /** Sends what is pending in the outbox. */
    @Scheduled(fixedDelayString = "${app.events.audit-audit-ms:15000}")
    public void deliver() {
        try {
            audit.deliverPending();
        } catch (RuntimeException ex) {
            log.error("Sending the audit records failed", ex);
        }
    }
}
