package edu.escuelaing.campusseguro.events.service;

import edu.escuelaing.campusseguro.events.dto.response.EvaluationResponse;
import java.util.UUID;

/**
 * Automatic evaluation of an event (RF-EVE-04): when an event finishes, MS06 saves a snapshot of
 * the check-in numbers (CHI) and the incident numbers (BEI), so they do not change later.
 *
 * <p>If CHI or BEI do not answer, the evaluation is saved with what is available and the missing
 * numbers stay empty; they are completed later by {@link #completePendingEvaluations()}.
 */
public interface EventEvaluationService {

    /**
     * Creates the evaluation of a finished event. If it already exists it is returned as it is.
     * It never throws because CHI or BEI are down.
     */
    EvaluationResponse evaluate(UUID eventId);

    /**
     * Asks CHI or BEI again for the numbers that are still missing in the saved evaluations.
     *
     * @return how many evaluations are complete after this run
     */
    int completePendingEvaluations();
}
