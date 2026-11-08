package edu.escuelaing.campusseguro.events.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Automatic evaluation of a finished event: a snapshot of the CHI and BEI numbers taken when
 * the event finished.
 *
 * @param eventId                 evaluated event
 * @param evaluatedAt             when the evaluation was created
 * @param complete                false while the CHI or BEI numbers are still missing (they are completed later)
 * @param noRecords               true if there are no check-ins and no incidents at all
 * @param totalCheckins           check-ins registered (CHI); {@code null} if CHI did not answer yet
 * @param checkinParticipationPct percentage of invited people who did the check-in
 * @param checkInByStatus         check-ins per status
 * @param totalIncidents          incidents reported (BEI); {@code null} if BEI did not answer yet
 * @param casesAttended           cases attended by the brigadiers
 * @param casesResolved           cases resolved by the brigadiers
 * @param avgAttentionSeconds     average attention time of an incident, in seconds
 * @param brigadiers              performance of each brigadier
 */
public record EvaluationResponse(
        UUID eventId,
        Instant evaluatedAt,
        boolean complete,
        boolean noRecords,
        Integer totalCheckins,
        BigDecimal checkinParticipationPct,
        Map<String, Integer> checkInByStatus,
        Integer totalIncidents,
        Integer casesAttended,
        Integer casesResolved,
        Long avgAttentionSeconds,
        List<BrigadierResult> brigadiers) {

    /** Cases of one brigadier. */
    public record BrigadierResult(String brigadierId, int casesAttended, int casesResolved) {
    }
}
