package edu.escuelaing.campusseguro.events.service;

import edu.escuelaing.campusseguro.events.dto.request.CreateEventRequest;
import edu.escuelaing.campusseguro.events.dto.response.EventResponse;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.exception.InvalidFieldException;
import edu.escuelaing.campusseguro.events.exception.InvalidReferenceException;
import edu.escuelaing.campusseguro.events.security.AccessActor;
import org.springframework.security.access.AccessDeniedException;

/**
 * Creates an event (RF-EVE-01).
 *
 * <p>Who can create: the coordinator creates real events and the organizer creates drills
 * (an event with the SIMULACRO type). The event is Scheduled only if it has a future start date,
 * a scope and participants; otherwise it is Draft. If CAM or AUT cannot validate the scope or the
 * roles, those parts are left pending and the event stays Draft.
 */
public interface EventCreationService {

    /**
     * Creates the event.
     *
     * @param request data of the event
     * @param actor   user who creates it
     * @return the saved event, with warnings if something stayed pending
     * @throws AccessDeniedException               if the role does not match the kind of event
     * @throws InvalidReferenceException           if a type, building, zone or role is unknown
     * @throws InvalidFieldException               if the start date is not in the future
     * @throws ExternalServiceUnavailableException if CHI is down and the event had to be scheduled
     */
    EventResponse create(CreateEventRequest request, AccessActor actor);
}
