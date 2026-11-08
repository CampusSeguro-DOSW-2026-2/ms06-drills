package edu.escuelaing.campusseguro.events.service;

/**
 * Keeps a local copy of the brigadiers of each event (table {@code event_brigadiers}).
 *
 * <p>BEI (MS05) is the source of truth: the brigadier activates and chooses a zone there. MS06
 * asks BEI for the list of every event that is In progress (every 10 s) or Scheduled (every 30 s)
 * and copies it. If BEI does not answer, the last copy is kept untouched, so its
 * {@code syncedAt} date shows how old the data is, and the rest of that cycle is skipped.
 * A Finished event is never synced: its brigadier data stays frozen as it was at the end.
 */
public interface BrigadierSyncService {

    /**
     * Syncs the brigadiers of every event that is In progress.
     *
     * @return how many events were synced
     */
    int syncRunningEvents();

    /**
     * Syncs the brigadiers of every event that is Scheduled.
     *
     * @return how many events were synced
     */
    int syncScheduledEvents();
}
