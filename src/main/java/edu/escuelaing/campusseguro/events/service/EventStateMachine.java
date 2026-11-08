package edu.escuelaing.campusseguro.events.service;

import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.exception.InvalidStateTransitionException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Allowed status changes of an event.
 *
 * <pre>
 * DRAFT &lt;-&gt; SCHEDULED -&gt; IN_PROGRESS -&gt; FINISHED
 * </pre>
 *
 * A scheduled event can go back to draft (for example when its start date is removed).
 * FINISHED is final. This class only knows the graph; the conditions to enter a status
 * (complete data, services available, ...) are checked by the services that use it.
 */
@Component
public class EventStateMachine {

    private static final Map<EventStatus, Set<EventStatus>> TRANSITIONS = new EnumMap<>(EventStatus.class);

    static {
        TRANSITIONS.put(EventStatus.DRAFT, Set.of(EventStatus.SCHEDULED));
        TRANSITIONS.put(EventStatus.SCHEDULED, Set.of(EventStatus.DRAFT, EventStatus.IN_PROGRESS));
        TRANSITIONS.put(EventStatus.IN_PROGRESS, Set.of(EventStatus.FINISHED));
        TRANSITIONS.put(EventStatus.FINISHED, Set.of());
    }

    /** Statuses that can be reached from the given one (empty for FINISHED). */
    public Set<EventStatus> allowedTargets(EventStatus from) {
        return TRANSITIONS.get(Objects.requireNonNull(from, "from"));
    }

    /** Tells if the change {@code from -> to} is allowed. */
    public boolean canTransition(EventStatus from, EventStatus to) {
        return allowedTargets(from).contains(Objects.requireNonNull(to, "to"));
    }

    /**
     * Checks the change {@code from -> to}.
     *
     * @throws InvalidStateTransitionException if it is not allowed
     */
    public void assertTransition(EventStatus from, EventStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidStateTransitionException(from, to);
        }
    }
}
