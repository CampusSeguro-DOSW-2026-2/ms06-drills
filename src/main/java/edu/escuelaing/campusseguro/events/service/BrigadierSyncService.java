package edu.escuelaing.campusseguro.events.service;

import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import java.util.UUID;

/**
 * Keeps a local copy of the brigadiers of each event (table {@code event_brigadiers}).
 *
 * <p>BEI (MS05) is the source of truth: the brigadier activates and chooses a zone there. MS06
 * asks BEI for the list of every event that is In progress (every 10 s) or Scheduled (every 30 s)
 * and copies it. If BEI does not answer, the last copy is kept untouched, so its
 * {@code syncedAt} date shows how old the data is, and the rest of that cycle is skipped.
 * A Finished event is never synced: its brigadier data stays frozen as it was at the end. For that,
 * the finish flow syncs the event one last time, right after BEI freezes it.
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

    /**
     * Syncs one event that is In progress, right now. It is used by the finish flow to take the
     * final list of brigadiers. An event in another status is skipped.
     *
     * @return {@code true} if the list was copied; {@code false} if the event was skipped
     * @throws ExternalServiceUnavailableException if BEI does not answer (the last copy is kept)
     */
    boolean syncEvent(UUID eventId);
}
