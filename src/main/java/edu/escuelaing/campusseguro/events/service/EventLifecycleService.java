package edu.escuelaing.campusseguro.events.service;

import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.security.Role;
import java.util.List;
import java.util.Set;

/**
 * Moves an event through its life: Draft to Scheduled, and Scheduled to In progress.
 *
 * <h3>Scheduling</h3>
 *
 * <p>Order: CHI, then BEI, then the two ALE alerts, and only at the end the status change.
 * <ul>
 *   <li>CHI down: an exception is thrown. The caller's transaction is rolled back, so nothing is saved.</li>
 *   <li>BEI or ALE down: everything done so far is undone (best effort) and the event stays Draft.</li>
 * </ul>
 *
 * <h3>Starting (RF-EVE-05)</h3>
 * <p>Every Scheduled event whose start date has arrived is started on its own: several events
 * can start at the same time and one failure never stops the others. The status changes first;
 * then CHI (check-in), BEI (brigadiers and incidents) and ALE (start alert) are told. If one of
 * them does not answer, the event starts anyway: the failure is saved and the call is repeated
 * until it works.
 */
public interface EventLifecycleService {

    /**
     * Tries to schedule a Draft event that already has date, scope and participants.
     *
     * @param event the saved Draft event
     * @param scope its scope
     * @param roles its invited roles
     * @param actor user who creates or edits the event
     * @return what happened; see {@link SchedulingOutcome}
     * @throws ExternalServiceUnavailableException if CHI does not answer
     */
    SchedulingOutcome schedule(EventEntity event, List<ScopeRef> scope, Set<Role> roles, String actor);

    /**
     * Starts every Scheduled event whose start date has arrived. Each event is started in its own
     * transaction. An event edited or finished by someone else in the meantime is skipped.
     *
     * @return how many events were started
     */
    int startDueEvents();

    /**
     * Repeats the calls to CHI, BEI or ALE that failed when an event started. A call is closed
     * when it works, or when the event is no longer In progress.
     *
     * @return how many pending calls were closed
     */
    int retryPendingStartCalls();
}
