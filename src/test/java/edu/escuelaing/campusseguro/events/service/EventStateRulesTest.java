package edu.escuelaing.campusseguro.events.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import org.junit.jupiter.api.Test;

/** Tests what can be done with an event in each status. */
class EventStateRulesTest {

    private final EventStateRules rules = new EventStateRules();

    @Test
    void everyStatusExceptFinishedCanBeEdited() {
        assertTrue(rules.isEditable(EventStatus.DRAFT));
        assertTrue(rules.isEditable(EventStatus.SCHEDULED));
        assertTrue(rules.isEditable(EventStatus.IN_PROGRESS));
        assertFalse(rules.isEditable(EventStatus.FINISHED));
    }

    @Test
    void theStartDateCannotChangeOnceTheEventStarted() {
        assertTrue(rules.isStartDateEditable(EventStatus.DRAFT));
        assertTrue(rules.isStartDateEditable(EventStatus.SCHEDULED));
        assertFalse(rules.isStartDateEditable(EventStatus.IN_PROGRESS));
        assertFalse(rules.isStartDateEditable(EventStatus.FINISHED));
    }

    @Test
    void onlyFinishedIsFinal() {
        assertTrue(rules.isFinal(EventStatus.FINISHED));
        assertFalse(rules.isFinal(EventStatus.IN_PROGRESS));
    }

    @Test
    void nullStatusIsRejected() {
        assertThrows(NullPointerException.class, () -> rules.isStartDateEditable(null));
        assertThrows(NullPointerException.class, () -> rules.isFinal(null));
    }
}
