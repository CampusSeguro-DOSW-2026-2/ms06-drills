package edu.escuelaing.campusseguro.events.client.alert;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local and test version of {@link AlertGateway}. It does not send anything: it gives fake ids
 * and remembers what it was asked, so tests and local runs can check the calls.
 */
@Component
@Profile({"local", "test"})
public class StubAlertGateway implements AlertGateway {

    private final List<AlertCommand> created = new CopyOnWriteArrayList<>();
    private final List<String> rescheduled = new CopyOnWriteArrayList<>();
    private final List<String> backToDraft = new CopyOnWriteArrayList<>();

    @Override
    public String createAlert(AlertCommand command) {
        created.add(command);
        return "stub-alert-" + UUID.randomUUID();
    }

    @Override
    public void reschedule(String alertId, Instant activationAt) {
        rescheduled.add(alertId + "@" + activationAt);
    }

    @Override
    public void backToDraft(String alertId) {
        backToDraft.add(alertId);
    }

    /** Alerts created so far, oldest first. */
    public List<AlertCommand> created() {
        return List.copyOf(created);
    }

    /** Reschedule calls so far, as {@code alertId@date}. */
    public List<String> rescheduled() {
        return List.copyOf(rescheduled);
    }

    /** Ids of the alerts taken back to draft so far. */
    public List<String> backToDraft() {
        return List.copyOf(backToDraft);
    }
}
