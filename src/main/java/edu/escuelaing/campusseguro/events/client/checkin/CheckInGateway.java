package edu.escuelaing.campusseguro.events.client.checkin;

import edu.escuelaing.campusseguro.events.client.EventStatePayload;
import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import java.util.UUID;

/** Calls to CHI (MS04 - Evacuation and check-in). The contract is a proposal until MS04 confirms it. */
public interface CheckInGateway {

    /**
     * Tells CHI the state of an event so it schedules, enables or disables the check-in.
     * It is idempotent: repeating the call is safe.
     *
     * @throws ExternalServiceUnavailableException if CHI does not answer
     */
    void pushState(EventStatePayload payload);

    /**
     * Check-in numbers of an event.
     *
     * @throws ExternalServiceUnavailableException if CHI does not answer
     */
    CheckInIndicators indicators(UUID eventId);

    /**
     * Check-ins of an event, one page at a time.
     *
     * @param status filter by CHI status; {@code null} for all
     * @throws ExternalServiceUnavailableException if CHI does not answer
     */
    CheckInPage checkins(UUID eventId, String status, int page, int size);
}
