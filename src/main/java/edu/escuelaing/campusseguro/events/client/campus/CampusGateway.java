package edu.escuelaing.campusseguro.events.client.campus;

import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.entity.enums.ScopeType;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.exception.InvalidReferenceException;
import java.util.Collection;
import java.util.List;

/** Calls to CAM (MS02 - Campus and security). */
public interface CampusGateway {

    /**
     * Checks that the buildings or zones exist and are active.
     *
     * @throws InvalidReferenceException           if some id is unknown or inactive
     * @throws ExternalServiceUnavailableException if CAM does not answer
     */
    void validateScope(ScopeType type, Collection<String> ids);

    /**
     * Gets the route maps of the scope of an event (for the event panel).
     *
     * @throws ExternalServiceUnavailableException if CAM does not answer
     */
    List<ScopeRoute> routes(Collection<ScopeRef> scope);
}
