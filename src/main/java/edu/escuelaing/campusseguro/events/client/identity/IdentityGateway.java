package edu.escuelaing.campusseguro.events.client.identity;

import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.exception.InvalidReferenceException;
import edu.escuelaing.campusseguro.events.security.Role;
import java.util.Set;

/** Calls to AUT (MS01 - Identity and access). */
public interface IdentityGateway {

    /**
     * Checks that the roles invited to an event exist in AUT.
     *
     * @throws InvalidReferenceException           if some role is unknown
     * @throws ExternalServiceUnavailableException if AUT does not answer
     */
    void validateRoles(Set<Role> roles);
}
