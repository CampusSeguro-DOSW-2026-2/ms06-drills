package edu.escuelaing.campusseguro.events.service;

import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.security.Role;
import java.util.List;
import java.util.Set;

/**
 * Moves a Draft event to Scheduled and tells the other services.
 *
 * <p>Order: CHI, then BEI, then the two ALE alerts, and only at the end the status change.
 * <ul>
 *   <li>CHI down: an exception is thrown. The caller's transaction is rolled back, so nothing is saved.</li>
 *   <li>BEI or ALE down: everything done so far is undone (best effort) and the event stays Draft.</li>
 * </ul>
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
}
