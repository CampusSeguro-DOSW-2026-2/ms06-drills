package edu.escuelaing.campusseguro.events.client.audit;

import edu.escuelaing.campusseguro.events.exception.AuditRejectedException;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;

/** Calls to MS08 (Analytics and audit): sends audit facts with the MS08 ingestion contract. */
public interface AuditGateway {

    /**
     * Sends one audit fact. Sending the same fact twice is safe: MS08 ignores it by its id.
     *
     * @throws ExternalServiceUnavailableException if MS08 or the Gateway does not answer (retry later)
     * @throws AuditRejectedException              if MS08 rejects the content with 400 (do not retry)
     */
    void send(AuditRecord record);
}
