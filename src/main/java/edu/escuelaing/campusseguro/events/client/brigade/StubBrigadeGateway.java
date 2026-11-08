package edu.escuelaing.campusseguro.events.client.brigade;

import edu.escuelaing.campusseguro.events.client.EventStatePayload;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local and test version of {@link BrigadeGateway}. It remembers the last state it received
 * for each event and always answers "no brigadiers and no incidents".
 */
@Component
@Profile({"local", "test"})
public class StubBrigadeGateway implements BrigadeGateway {

    private final Map<UUID, EventStatePayload> lastState = new ConcurrentHashMap<>();
    private final Clock clock;

    public StubBrigadeGateway(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void pushState(EventStatePayload payload) {
        lastState.put(payload.eventId(), payload);
    }

    @Override
    public BrigadierSnapshot brigadiers(UUID eventId) {
        return new BrigadierSnapshot(eventId, clock.instant(), List.of());
    }

    @Override
    public IncidentIndicators indicators(UUID eventId) {
        return new IncidentIndicators(eventId, clock.instant(), 0, 0, 0, null, List.of());
    }

    /** Last state received for the event, if any. */
    public Optional<EventStatePayload> lastState(UUID eventId) {
        return Optional.ofNullable(lastState.get(eventId));
    }
}
