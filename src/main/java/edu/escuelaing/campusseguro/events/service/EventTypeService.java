package edu.escuelaing.campusseguro.events.service;

import edu.escuelaing.campusseguro.events.dto.response.EventTypeResponse;
import edu.escuelaing.campusseguro.events.exception.BusinessException;
import java.util.List;

/** Event type catalog: lists the types and adds new ones. */
public interface EventTypeService {

    /** Active types sorted by name. */
    List<EventTypeResponse> listActive();

    /**
     * Adds a type. The code is generated from the name.
     *
     * @param name  display name
     * @param actor user who adds it
     * @throws BusinessException (CONFLICT) if the name or the generated code already exists
     */
    EventTypeResponse create(String name, String actor);
}
