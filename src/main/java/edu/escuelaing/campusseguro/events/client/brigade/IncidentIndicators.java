package edu.escuelaing.campusseguro.events.client.brigade;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Incident numbers of one event, as BEI reports them.
 *
 * @param eventId       event
 * @param updatedAt     when BEI calculated them
 * @param totalIncidents incidents reported
 * @param casesAttended cases attended
 * @param casesResolved cases resolved
 * @param avgAttentionSeconds average attention time in seconds; {@code null} if BEI does not send it
 * @param byBrigadier   numbers per brigadier
 */
public record IncidentIndicators(
        UUID eventId,
        Instant updatedAt,
        int totalIncidents,
        int casesAttended,
        int casesResolved,
        Long avgAttentionSeconds,
        List<BrigadierPerformance> byBrigadier) {
}
