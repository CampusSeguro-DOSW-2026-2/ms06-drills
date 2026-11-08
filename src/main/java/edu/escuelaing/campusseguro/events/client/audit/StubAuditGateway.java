package edu.escuelaing.campusseguro.events.client.audit;

import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local and test version of {@link AuditGateway}. It sends nothing: it remembers the records and
 * can pretend that MS08 is down.
 */
@Component
@Profile({"local", "test"})
public class StubAuditGateway implements AuditGateway {

    private final List<AuditRecord> sent = new CopyOnWriteArrayList<>();
    private volatile boolean down;

    @Override
    public void send(AuditRecord record) {
        if (down) {
            throw new ExternalServiceUnavailableException("MS08", new IllegalStateException("MS08 is down (stub)"));
        }
        sent.add(record);
    }

    /** Records received so far, oldest first. */
    public List<AuditRecord> sent() {
        return List.copyOf(sent);
    }

    /** Makes every later call fail ({@code true}) or work again ({@code false}). */
    public void simulateOutage(boolean value) {
        this.down = value;
    }

    /** Forgets the received records and ends any simulated outage. */
    public void clear() {
        sent.clear();
        down = false;
    }
}
