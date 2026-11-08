package edu.escuelaing.campusseguro.events.service;

import edu.escuelaing.campusseguro.events.dto.response.EventPanelResponse;
import edu.escuelaing.campusseguro.events.exception.BusinessException;
import edu.escuelaing.campusseguro.events.exception.ResourceNotFoundException;
import edu.escuelaing.campusseguro.events.security.AccessActor;
import java.util.UUID;

/**
 * Builds the panel of an event while it is In progress (RF-EVE-05).
 *
 * <p>The panel has the event name, the route maps of its scope (from CAM), and the state of the
 * check-in and incident buttons. If CAM does not answer the panel is still returned, without routes.
 */
public interface EventPanelService {

    /**
     * Panel of an event.
     *
     * @param eventId event to show
     * @param actor   user who asks; the start problems are only listed for who manages the event
     * @throws ResourceNotFoundException if the event does not exist
     * @throws BusinessException         (EVENT_NOT_ACTIVE) if the event is not In progress
     */
    EventPanelResponse panel(UUID eventId, AccessActor actor);
}
