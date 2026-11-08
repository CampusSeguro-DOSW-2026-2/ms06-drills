package edu.escuelaing.campusseguro.events.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import edu.escuelaing.campusseguro.events.exception.ErrorCode;
import edu.escuelaing.campusseguro.events.exception.InvalidStateTransitionException;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests every possible status change (4 x 4) against the allowed graph. */
class EventStateMachineTest {

    private static final EventStatus D = EventStatus.DRAFT;
    private static final EventStatus S = EventStatus.SCHEDULED;
    private static final EventStatus P = EventStatus.IN_PROGRESS;
    private static final EventStatus F = EventStatus.FINISHED;

    private final EventStateMachine machine = new EventStateMachine();

    @Test
    void onlyTheDocumentedChangesAreAllowed() {
        Map<EventStatus, Set<EventStatus>> allowed = Map.of(
                D, Set.of(S),
                S, Set.of(D, P),
                P, Set.of(F),
                F, Set.of());

        for (EventStatus from : EventStatus.values()) {
            for (EventStatus to : EventStatus.values()) {
                boolean expected = allowed.get(from).contains(to);
                assertEquals(expected, machine.canTransition(from, to), from + " -> " + to);
            }
        }
    }

    @Test
    void finishedHasNoWayOut() {
        assertTrue(machine.allowedTargets(F).isEmpty());
    }

    @Test
    void aScheduledEventCanGoBackToDraft() {
        assertTrue(machine.canTransition(S, D));
        assertEquals(Set.of(D, P), machine.allowedTargets(S));
    }

    @Test
    void anInvalidChangeThrowsAConflictWithBothStatuses() {
        InvalidStateTransitionException ex =
                assertThrows(InvalidStateTransitionException.class, () -> machine.assertTransition(D, F));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, ex.code());
        assertTrue(ex.getMessage().contains("DRAFT"));
        assertTrue(ex.getMessage().contains("FINISHED"));
    }

    @Test
    void aValidChangeDoesNotThrow() {
        machine.assertTransition(P, F);
        assertFalse(machine.canTransition(P, D));
    }

    @Test
    void sameStatusIsNotAChange() {
        assertThrows(InvalidStateTransitionException.class, () -> machine.assertTransition(S, S));
    }

    @Test
    void nullStatusesAreRejected() {
        assertThrows(NullPointerException.class, () -> machine.allowedTargets(null));
        assertThrows(NullPointerException.class, () -> machine.canTransition(D, null));
    }
}
