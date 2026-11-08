package edu.escuelaing.campusseguro.events.dto.response;

import edu.escuelaing.campusseguro.events.entity.enums.EventStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Answer of finishing an event (RF-EVE-04).
 *
 * @param id         event identifier
 * @param name       event name
 * @param status     always FINISHED
 * @param startedAt  real start time
 * @param endedAt    real end time
 * @param evaluation general metrics of the event; {@code null} only if they could not be saved
 * @param warnings   what could not be done (alert not sent, metrics incomplete, no records)
 */
public record EventFinishResponse(
        UUID id,
        String name,
        EventStatus status,
        Instant startedAt,
        Instant endedAt,
        EvaluationResponse evaluation,
        List<EventWarning> warnings) {
}
