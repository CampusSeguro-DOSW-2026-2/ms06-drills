package edu.escuelaing.campusseguro.events.client.audit;

import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;

/** Calls to MS08 (Analytics and audit). The contract is a proposal until MS08 confirms it. */
public interface AuditGateway {

    /**
     * Sends one audit record. Sending the same record twice is safe: MS08 can ignore it by its id.
     *
     * @throws ExternalServiceUnavailableException if MS08 does not answer
     */
    void send(AuditRecord record);
}
