package edu.escuelaing.campusseguro.events.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.escuelaing.campusseguro.events.service.BrigadierSyncService;
import org.junit.jupiter.api.Test;

/** Tests that each job only calls its own method and that an error never escapes the job. */
class BrigadierSyncSchedulerTest {

    private final BrigadierSyncService sync = mock(BrigadierSyncService.class);
    private final BrigadierSyncScheduler scheduler = new BrigadierSyncScheduler(sync);

    @Test
    void theRunningJobSyncsTheRunningEventsOnly() {
        scheduler.syncRunning();

        verify(sync).syncRunningEvents();
        verify(sync, never()).syncScheduledEvents();
    }

    @Test
    void theScheduledJobSyncsTheScheduledEventsOnly() {
        scheduler.syncScheduled();

        verify(sync).syncScheduledEvents();
        verify(sync, never()).syncRunningEvents();
    }

    @Test
    void anErrorDoesNotEscapeTheJobs() {
        when(sync.syncRunningEvents()).thenThrow(new IllegalStateException("database down"));
        when(sync.syncScheduledEvents()).thenThrow(new IllegalStateException("database down"));

        scheduler.syncRunning();
        scheduler.syncScheduled();

        verify(sync).syncRunningEvents();
        verify(sync).syncScheduledEvents();
    }
}
