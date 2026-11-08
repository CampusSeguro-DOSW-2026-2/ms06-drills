package edu.escuelaing.campusseguro.events.client.checkin;

import edu.escuelaing.campusseguro.events.client.EventStatePayload;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local and test version of {@link CheckInGateway}. It remembers the last state it received
 * for each event and always answers "no check-ins yet".
 */
@Component
@Profile({"local", "test"})
public class StubCheckInGateway implements CheckInGateway {

    private final Map<UUID, EventStatePayload> lastState = new ConcurrentHashMap<>();
    private final Clock clock;

    public StubCheckInGateway(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void pushState(EventStatePayload payload) {
        lastState.put(payload.eventId(), payload);
    }

    @Override
    public CheckInIndicators indicators(UUID eventId) {
        return new CheckInIndicators(eventId, clock.instant(), 0, Map.of(), BigDecimal.ZERO);
    }

    @Override
    public CheckInPage checkins(UUID eventId, String status, int page, int size) {
        return new CheckInPage(page, size, 0, List.of());
    }

    /** Last state received for the event, if any. */
    public Optional<EventStatePayload> lastState(UUID eventId) {
        return Optional.ofNullable(lastState.get(eventId));
    }
}
