package edu.escuelaing.campusseguro.events.client.identity;

import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import edu.escuelaing.campusseguro.events.exception.InvalidReferenceException;
import edu.escuelaing.campusseguro.events.security.Role;
import java.util.Set;

/** Checks of the roles that an event invites (the catalog is the local {@code Role} enum). */
public interface IdentityGateway {

    /**
     * Checks that the roles invited to an event belong to the official catalog.
     *
     * @throws InvalidReferenceException           if some role is unknown
     * @throws ExternalServiceUnavailableException if the check needs a service that does not answer
     */
    void validateRoles(Set<Role> roles);
}
