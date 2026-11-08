package edu.escuelaing.campusseguro.events.service;

   import edu.escuelaing.campusseguro.events.dto.response.EventWarning;
import edu.escuelaing.campusseguro.events.entity.EventAlertEntity;
import java.util.List;

/**
 * Result of trying to schedule an event.
 *
 * @param scheduled {@code true} if the event is now Scheduled; {@code false} if it stays Draft
 * @param warnings  why it stayed Draft (empty when scheduled)
 * @param alerts    alerts created in ALE (empty when it stayed Draft)
 */
public record SchedulingOutcome(boolean scheduled, List<EventWarning> warnings, List<EventAlertEntity> alerts) {
}
