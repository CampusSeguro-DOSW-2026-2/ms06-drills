package edu.escuelaing.campusseguro.events.service;

import edu.escuelaing.campusseguro.events.dto.request.CreateEventRequest;
import edu.escuelaing.campusseguro.events.dto.response.EventResponse;
import edu.escuelaing.campusseguro.events.exception.BusinessException;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.exception.InvalidFieldException;
import edu.escuelaing.campusseguro.events.exception.ResourceNotFoundException;
import edu.escuelaing.campusseguro.events.security.AccessActor;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

/**
 * Edits an event (RF-EVE-02). The request replaces every editable field (like the edit form does).
 *
 * <ul>
 *   <li><b>Draft</b>: the data is saved; if it is now complete the event is scheduled (same rules as creation).</li>
 *   <li><b>Scheduled</b>: if it is still complete, CHI and BEI get the new data, the general alert is
 *       rescheduled when the date changed and brigadiers get a new alert to check their assignment.
 *       If the data is no longer complete the event goes back to Draft.</li>
 *   <li><b>In progress</b>: name, description, types, scope and participants can change (not the start date,
 *       the kind of event, or empty scope/participants). CHI and BEI get the new data and brigadiers and
 *       participants get an alert with the update.</li>
 *   <li><b>Finished</b>: it cannot be edited.</li>
 * </ul>
 *
 * <p>If CHI, BEI or ALE fail while editing a Scheduled or In progress event, nothing is saved: what was already
 * sent is undone (best effort) and the error is thrown so the transaction is rolled back.
 * A request that changes nothing in a Scheduled or In progress event does nothing (no alerts are sent).
 */
public interface EventUpdateService {

    /**
     * Edits the event.
     *
     * @param eventId event to edit
     * @param request new data (replaces the current one)
     * @param actor   user who edits
     * @return the event after the edit, with warnings if something stayed pending
     * @throws ResourceNotFoundException           if the event does not exist
     * @throws BusinessException                   (EVENT_NOT_EDITABLE) if the event is finished
     * @throws AccessDeniedException               if the role does not match the kind of event
     * @throws InvalidFieldException               if a field breaks a rule (past date, kind change, etc.)
     * @throws ExternalServiceUnavailableException if a required service is down
     */
    EventResponse update(UUID eventId, CreateEventRequest request, AccessActor actor);
}
