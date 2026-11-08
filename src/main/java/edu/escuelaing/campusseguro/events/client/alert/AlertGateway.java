package edu.escuelaing.campusseguro.events.client.alert;

import edu.escuelaing.campusseguro.events.exception.ExternalServiceUnavailableException;
import java.time.Instant;

/**
 * Calls to ALE (MS03 - Alerts and emergencies). This contract is provisional: it follows what
 * the event requirements need and the MS03 team will adapt to it.
 */
public interface AlertGateway {

    /**
     * Creates an alert.
     *
     * @return the id that ALE gives to the alert
     * @throws ExternalServiceUnavailableException if ALE does not answer
     */
    String createAlert(AlertCommand command);

    /**
     * Changes the date on which a scheduled alert is sent.
     *
     * @throws ExternalServiceUnavailableException if ALE does not answer
     */
    void reschedule(String alertId, Instant activationAt);

    /**
     * Takes a scheduled alert back to draft (the event lost its start date).
     *
     * @throws ExternalServiceUnavailableException if ALE does not answer
     */
    void backToDraft(String alertId);

    /**
     * Sends a scheduled alert now (the event just started). It is idempotent: activating an
     * alert that is already active is not an error.
     *
     * @throws ExternalServiceUnavailableException if ALE does not answer
     */
    void activate(String alertId);
}
