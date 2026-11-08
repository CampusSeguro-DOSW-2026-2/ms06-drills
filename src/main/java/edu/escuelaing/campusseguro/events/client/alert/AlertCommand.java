package edu.escuelaing.campusseguro.events.client.alert;

import edu.escuelaing.campusseguro.events.client.ScopeRef;
import edu.escuelaing.campusseguro.events.entity.enums.EventAlertPurpose;
import edu.escuelaing.campusseguro.events.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Request to create an alert in ALE for an event.
 *
 * <p>When it is sent: {@code activateImmediately} sends it now; otherwise, with an
 * {@code activationAt} date it is scheduled for that moment, and with neither it stays as a draft.
 *
 * @param eventId             event that creates the alert
 * @param purpose             why the alert is created
 * @param simulacro           true if the event is a drill
 * @param title               alert title
 * @param instructions        text for the people who receive it
 * @param scope               buildings and zones the alert covers
 * @param recipientRoles      roles that receive the alert
 * @param activationAt        date to send it (scheduled alert)
 * @param activateImmediately send it now
 */
public record AlertCommand(
        UUID eventId,
        EventAlertPurpose purpose,
        boolean simulacro,
        String title,
        String instructions,
        List<ScopeRef> scope,
        Set<Role> recipientRoles,
        Instant activationAt,
        boolean activateImmediately) {
}
