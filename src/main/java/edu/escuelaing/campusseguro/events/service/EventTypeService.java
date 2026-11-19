package edu.escuelaing.campusseguro.events.service;

import edu.escuelaing.campusseguro.events.dto.response.AlertTypeResponse;
import edu.escuelaing.campusseguro.events.dto.response.EventTypeResponse;
import edu.escuelaing.campusseguro.events.exception.BusinessException;
import java.util.List;

/** Event type catalog: lists the types and adds new ones. */
public interface EventTypeService {

    /** Active types sorted by name. */
    List<EventTypeResponse> listActive();

    /**
     * Active and alert-eligible types sorted by name, to serve the MS03 alert catalog.
     * The {@code SIMULACRO} type is not eligible, so it is not included.
     */
    List<AlertTypeResponse> listAlertTypes();

    /**
     * Adds a type. The code is generated from the name.
     *
     * @param name  display name
     * @param actor user who adds it
     * @throws BusinessException (CONFLICT) if the name or the generated code already exists
     */
    EventTypeResponse create(String name, String actor);
}
