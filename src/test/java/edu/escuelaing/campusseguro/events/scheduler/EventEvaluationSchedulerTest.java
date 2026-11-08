package edu.escuelaing.campusseguro.events.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.escuelaing.campusseguro.events.service.EventEvaluationService;
import org.junit.jupiter.api.Test;

/** Tests that the scheduler only delegates and an error never escapes the job. */
class EventEvaluationSchedulerTest {

    private final EventEvaluationService evaluation = mock(EventEvaluationService.class);
    private final EventEvaluationScheduler scheduler = new EventEvaluationScheduler(evaluation);

    @Test
    void completesThePendingEvaluations() {
        scheduler.completePending();

        verify(evaluation).completePendingEvaluations();
    }

    @Test
    void anErrorDoesNotEscapeTheJob() {
        when(evaluation.completePendingEvaluations()).thenThrow(new IllegalStateException("database down"));

        scheduler.completePending();

        verify(evaluation).completePendingEvaluations();
    }
}
