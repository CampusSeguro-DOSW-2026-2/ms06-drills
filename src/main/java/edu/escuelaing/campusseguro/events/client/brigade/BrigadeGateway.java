package edu.escuelaing.campusseguro.events.client.brigade;

import edu.escuelaing.campusseguro.events.client.EventStatePayload;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import java.util.UUID;

/** Calls to BEI (MS05 - Brigadiers and incidents). The contract is a proposal until MS05 confirms it. */
public interface BrigadeGateway {

    /**
     * Tells BEI the state of an event so it prepares or releases its brigadiers.
     * It is idempotent: repeating the call is safe.
     *
     * @throws ExternalServiceUnavailableException if BEI does not answer
     */
    void pushState(EventStatePayload payload);

    /**
     * Brigadiers of an event.
     *
     * @throws ExternalServiceUnavailableException if BEI does not answer
     */
    BrigadierSnapshot brigadiers(UUID eventId);

    /**
     * Incident numbers of an event.
     *
     * @throws ExternalServiceUnavailableException if BEI does not answer
     */
    IncidentIndicators indicators(UUID eventId);
}
