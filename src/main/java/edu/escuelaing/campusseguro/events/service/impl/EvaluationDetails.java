package edu.escuelaing.campusseguro.events.service.impl;

import edu.escuelaing.campusseguro.events.client.brigade.BrigadierPerformance;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Detail of an evaluation that does not fit in a column. It is saved as JSON in
 * {@code event_evaluations.details}.
 *
 * @param checkInUpdatedAt   when CHI calculated its numbers; {@code null} if CHI has not answered
 * @param checkInByStatus    check-ins per status
 * @param incidentsUpdatedAt when BEI calculated its numbers; {@code null} if BEI has not answered
 * @param brigadiers         performance of each brigadier
 */
public record EvaluationDetails(
        Instant checkInUpdatedAt,
        Map<String, Integer> checkInByStatus,
        Instant incidentsUpdatedAt,
        List<BrigadierPerformance> brigadiers) {

    /** Details without any data yet. */
    public static EvaluationDetails empty() {
        return new EvaluationDetails(null, Map.of(), null, List.of());
    }

    /** Same details with the CHI part filled. */
    public EvaluationDetails withCheckIn(Instant updatedAt, Map<String, Integer> byStatus) {
        return new EvaluationDetails(updatedAt, byStatus == null ? Map.of() : byStatus, incidentsUpdatedAt, brigadiers);
    }

    /** Same details with the BEI part filled. */
    public EvaluationDetails withIncidents(Instant updatedAt, List<BrigadierPerformance> byBrigadier) {
        return new EvaluationDetails(
                checkInUpdatedAt, checkInByStatus, updatedAt, byBrigadier == null ? List.of() : byBrigadier);
    }
}
