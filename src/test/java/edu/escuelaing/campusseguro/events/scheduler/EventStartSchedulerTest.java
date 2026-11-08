package edu.escuelaing.campusseguro.events.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.escuelaing.campusseguro.events.service.EventLifecycleService;
import org.junit.jupiter.api.Test;

/** Tests that the scheduler only delegates and never lets one failure stop the other job. */
class EventStartSchedulerTest {

    private final EventLifecycleService lifecycle = mock(EventLifecycleService.class);
    private final EventStartScheduler scheduler = new EventStartScheduler(lifecycle);

    @Test
    void startsTheDueEventsAndRetriesThePendingCalls() {
        when(lifecycle.startDueEvents()).thenReturn(2);

        scheduler.run();

        verify(lifecycle).startDueEvents();
        verify(lifecycle).retryPendingStartCalls();
    }

    @Test
    void theRetryStillRunsWhenStartingFails() {
        when(lifecycle.startDueEvents()).thenThrow(new IllegalStateException("database down"));

        scheduler.run();

        verify(lifecycle).retryPendingStartCalls();
    }

    @Test
    void anErrorInTheRetryDoesNotEscapeTheJob() {
        when(lifecycle.retryPendingStartCalls()).thenThrow(new IllegalStateException("database down"));

        scheduler.run();

        verify(lifecycle).startDueEvents();
    }
}
