package edu.escuelaing.campusseguro.events.service;

import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.dto.response.EventFinishResponse;
import edu.escuelaing.campusseguro.events.entity.EventEntity;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.security.AccessActor;
import edu.escuelaing.campusseguro.events.security.Role;
import edu.escuelaing.campusseguro.events.exception.InvalidStateTransitionException;
import edu.escuelaing.campusseguro.events.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

/**
 * Moves an event through its life: Draft to Scheduled, Scheduled to In progress, and In progress to Finished.
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
 *
 * <h3>Finishing (RF-EVE-04)</h3>
 * <p>The coordinator or organizer finishes an event that is In progress. CHI and BEI must answer (check-in
 * is disabled and the brigadiers are frozen); if one of them is down nothing changes and the error is
 * thrown. Right after BEI freezes the brigadiers, MS06 copies their final list. If ALE is down the event finishes anyway and the answer says the alert was not sent. After
 * the status change the automatic evaluation (CHI and BEI numbers) is saved.
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

    /**
     * Finishes an event that is In progress.
     *
     * <p>Order: CHI (disable check-in), BEI (freeze brigadiers), final copy of the brigadiers, ALE (alert to
     * the participants), status change, and then the evaluation. If the final copy fails the event is finished
     * anyway, with a warning, and the last copy is kept. If BEI fails after CHI answered, the check-in
     * is enabled again (best effort).
     *
     * @param eventId event to finish
     * @param actor   user who finishes it
     * @return the finished event with its general metrics and any warning
     * @throws ResourceNotFoundException           if the event does not exist
     * @throws AccessDeniedException               if the role does not match the kind of event
     * @throws InvalidStateTransitionException     if the event is not In progress
     * @throws ExternalServiceUnavailableException if CHI or BEI do not answer (the event is not finished)
     */
    EventFinishResponse finish(UUID eventId, AccessActor actor);
}
